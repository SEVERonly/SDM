package com.example.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.DownloadEntity
import com.example.data.SettingsManager
import com.example.model.ChunkInfo
import com.example.model.ChunkStatus
import com.example.model.DownloadItem
import com.example.model.DownloadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.RandomAccessFile
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

data class ResolvedFileInfo(
    val url: String,
    val fileName: String,
    val fileSize: Long,
    val supportsRanges: Boolean,
    val mimeType: String?
)

class DownloadEngine(
    private val context: Context,
    private val database: AppDatabase,
    private val settingsManager: SettingsManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val speedLimiter = SpeedLimiter(0L)

    // Standard Universal User-Agent to avoid HTTP 403 Forbidden on CDNs & AWS S3
    private val standardUserAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    // Active Jobs and State
    private val downloadJobs = ConcurrentHashMap<String, Job>()
    private val liveDownloads = ConcurrentHashMap<String, DownloadItem>()

    private val _liveDownloadsFlow = MutableStateFlow<Map<String, DownloadItem>>(emptyMap())
    val liveDownloadsFlow: StateFlow<Map<String, DownloadItem>> = _liveDownloadsFlow.asStateFlow()

    private val _totalSpeedBytes = MutableStateFlow(0L)
    val totalSpeedBytes: StateFlow<Long> = _totalSpeedBytes.asStateFlow()

    private val _speedHistory = MutableStateFlow<List<Long>>(List(30) { 0L })
    val speedHistory: StateFlow<List<Long>> = _speedHistory.asStateFlow()

    private val onDownloadCompletedListeners = mutableListOf<(DownloadItem) -> Unit>()

    init {
        // Monitor settings changes to update speed limiter
        scope.launch {
            settingsManager.settings.collect { settings ->
                val bytesLimit = if (settings.speedLimitKbps > 0) {
                    settings.speedLimitKbps * 1024L
                } else {
                    0L
                }
                speedLimiter.maxBytesPerSecond = bytesLimit
            }
        }

        // Ticker loop for speed metrics & charts
        scope.launch {
            var lastGlobalBytes = 0L
            while (isActive) {
                delay(1000)
                var currentGlobalBytes = 0L
                for (item in liveDownloads.values) {
                    if (item.status == DownloadStatus.DOWNLOADING) {
                        currentGlobalBytes += item.downloadedBytes
                    }
                }
                val speed = max(0L, currentGlobalBytes - lastGlobalBytes)
                lastGlobalBytes = currentGlobalBytes
                _totalSpeedBytes.value = speed

                _speedHistory.value = (_speedHistory.value.drop(1) + speed).takeLast(30)
            }
        }
    }

    fun addOnDownloadCompletedListener(listener: (DownloadItem) -> Unit) {
        onDownloadCompletedListeners.add(listener)
    }

    /**
     * Inspects a URL using HEAD or byte-range request to get metadata before downloading
     */
    suspend fun inspectUrl(rawUrl: String): Result<ResolvedFileInfo> = withContext(Dispatchers.IO) {
        try {
            val url = rawUrl.trim()
            val headRequest = Request.Builder()
                .url(url)
                .header("User-Agent", standardUserAgent)
                .header("Accept", "*/*")
                .head()
                .build()

            var response: Response? = null
            try {
                response = okHttpClient.newCall(headRequest).execute()
            } catch (e: Exception) {
                // Some servers reject HEAD requests; fallback to a 0-0 range GET
                val getRangeRequest = Request.Builder()
                    .url(url)
                    .header("User-Agent", standardUserAgent)
                    .header("Accept", "*/*")
                    .header("Range", "bytes=0-0")
                    .build()
                response = okHttpClient.newCall(getRangeRequest).execute()
            }

            if (response == null || (!response.isSuccessful && response.code != 206)) {
                return@withContext Result.failure(Exception("HTTP ${response?.code ?: "Unknown"}: Server denied inspection"))
            }

            var contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
            val contentRange = response.header("Content-Range")
            if (contentRange != null && contentRange.contains("/")) {
                val totalStr = contentRange.substringAfterLast("/")
                totalStr.toLongOrNull()?.let { contentLength = it }
            }

            val acceptRanges = response.header("Accept-Ranges")
            val supportsRanges = (acceptRanges != null && acceptRanges.contains("bytes", ignoreCase = true)) ||
                    (response.code == 206)

            val disposition = response.header("Content-Disposition")
            val mimeType = response.header("Content-Type")

            var fileName = extractFileNameFromDisposition(disposition)
            if (fileName.isNullOrBlank()) {
                fileName = extractFileNameFromUrl(url)
            }
            if (fileName.isBlank()) {
                fileName = "download_${System.currentTimeMillis()}"
            }

            response.close()

            Result.success(
                ResolvedFileInfo(
                    url = url,
                    fileName = fileName,
                    fileSize = contentLength,
                    supportsRanges = supportsRanges,
                    mimeType = mimeType
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Start or resume a download safely with coroutine isolation
     */
    fun startDownload(downloadItem: DownloadItem) {
        val taskId = downloadItem.id
        if (downloadJobs[taskId]?.isActive == true) return

        val job = scope.launch {
            try {
                executeDownload(downloadItem)
            } catch (t: Throwable) {
                Log.e("SDM", "Fatal error in download job $taskId: ${t.message}", t)
                val current = liveDownloads[taskId] ?: downloadItem
                val failed = current.copy(
                    status = DownloadStatus.FAILED,
                    speedBytesPerSec = 0L,
                    errorMessage = t.message ?: "Download connection failed"
                )
                updateLiveItem(failed)
                database.downloadDao().update(DownloadEntity.fromDomainModel(failed))
            }
        }
        downloadJobs[taskId] = job
    }

    fun pauseDownload(taskId: String) {
        downloadJobs[taskId]?.cancel()
        downloadJobs.remove(taskId)

        val current = liveDownloads[taskId]
        if (current != null) {
            val paused = current.copy(status = DownloadStatus.PAUSED, speedBytesPerSec = 0L)
            updateLiveItem(paused)
            scope.launch {
                database.downloadDao().update(DownloadEntity.fromDomainModel(paused))
            }
        }
    }

    fun cancelDownload(taskId: String, deleteFile: Boolean = true) {
        downloadJobs[taskId]?.cancel()
        downloadJobs.remove(taskId)

        val current = liveDownloads.remove(taskId)
        _liveDownloadsFlow.value = liveDownloads.toMap()

        scope.launch {
            database.downloadDao().deleteById(taskId)
            if (deleteFile && current != null) {
                val f = File(current.filePath)
                if (f.exists()) f.delete()
            }
        }
    }

    private suspend fun executeDownload(initialItem: DownloadItem) = withContext(Dispatchers.IO) {
        val taskId = initialItem.id

        // Check Network Rules (Wi-Fi Only)
        val settings = settingsManager.settings.value
        if (settings.wifiOnly && !isWifiConnected()) {
            val paused = initialItem.copy(
                status = DownloadStatus.PAUSED,
                errorMessage = "Waiting for Wi-Fi connection..."
            )
            updateLiveItem(paused)
            database.downloadDao().update(DownloadEntity.fromDomainModel(paused))
            return@withContext
        }

        var item = initialItem.copy(status = DownloadStatus.CONNECTING, errorMessage = null)
        updateLiveItem(item)
        database.downloadDao().update(DownloadEntity.fromDomainModel(item))

        try {
            // Resolve info if not already known
            var fileSize = item.fileSize
            var supportsRanges = item.supportsRanges

            if (fileSize <= 0 || item.mimeType == null) {
                val inspected = inspectUrl(item.url).getOrNull()
                if (inspected != null) {
                    fileSize = inspected.fileSize
                    supportsRanges = inspected.supportsRanges
                    item = item.copy(
                        fileSize = fileSize,
                        supportsRanges = supportsRanges,
                        mimeType = inspected.mimeType ?: item.mimeType
                    )
                }
            }

            // Ensure destination directory exists
            val targetFile = File(item.filePath)
            targetFile.parentFile?.mkdirs()

            // Prepare Chunks: if server does not support ranges or fileSize unknown, use 1 stream
            val threadCount = if (supportsRanges && fileSize > 1024 * 1024) {
                item.threadCount.coerceIn(1, 32)
            } else {
                1
            }

            val chunks = if (item.chunks.isNotEmpty() && item.chunks.size == threadCount) {
                item.chunks.toMutableList()
            } else {
                createChunks(fileSize, threadCount)
            }

            // Pre-allocate file size if known for instant merging
            if (fileSize > 0 && !targetFile.exists()) {
                try {
                    RandomAccessFile(targetFile, "rw").use { raf ->
                        raf.setLength(fileSize)
                    }
                } catch (e: Exception) {
                    Log.w("SDM", "Could not pre-allocate file length: ${e.message}")
                }
            }

            item = item.copy(
                status = DownloadStatus.DOWNLOADING,
                fileSize = fileSize,
                supportsRanges = supportsRanges,
                threadCount = threadCount,
                chunks = chunks
            )
            updateLiveItem(item)

            // Multi-stream parallel download execution
            val isCancelled = AtomicBoolean(false)
            val chunkDownloadedCounters = chunks.map { AtomicLong(it.downloadedBytes) }
            val chunkSpeedCounters = chunks.map { AtomicLong(0L) }

            // Progress syncing ticker
            val progressJob = launch {
                var lastSecBytes = item.downloadedBytes
                while (isActive && !isCancelled.get()) {
                    delay(500)
                    val totalDownloaded = chunkDownloadedCounters.sumOf { it.get() }
                    val currentSpeed = max(0L, (totalDownloaded - lastSecBytes) * 2)
                    lastSecBytes = totalDownloaded

                    val updatedChunks = chunks.mapIndexed { idx, c ->
                        val down = chunkDownloadedCounters[idx].get()
                        val spd = chunkSpeedCounters[idx].getAndSet(0L) * 2
                        val status = if (down >= c.totalBytes && c.totalBytes > 0) {
                            ChunkStatus.COMPLETED
                        } else {
                            ChunkStatus.DOWNLOADING
                        }
                        c.copy(downloadedBytes = down, status = status, speedBytesPerSec = spd)
                    }

                    item = item.copy(
                        downloadedBytes = totalDownloaded,
                        speedBytesPerSec = currentSpeed,
                        chunks = updatedChunks
                    )
                    updateLiveItem(item)

                    database.downloadDao().updateProgress(
                        taskId,
                        totalDownloaded,
                        DownloadStatus.DOWNLOADING.name,
                        DownloadEntity.serializeChunks(updatedChunks)
                    )
                }
            }

            // Execute parallel chunks inside supervisorScope to isolate child failures cleanly
            try {
                supervisorScope {
                    val chunkJobs = chunks.mapIndexed { index, chunk ->
                        async {
                            try {
                                downloadChunkStream(
                                    url = item.url,
                                    chunk = chunk,
                                    targetFile = targetFile,
                                    supportsRanges = supportsRanges,
                                    counter = chunkDownloadedCounters[index],
                                    speedCounter = chunkSpeedCounters[index],
                                    isCancelled = isCancelled
                                )
                            } catch (e: Exception) {
                                isCancelled.set(true)
                                throw e
                            }
                        }
                    }

                    // Await all chunks
                    chunkJobs.awaitAll()
                }
            } finally {
                progressJob.cancel()
            }

            val finalDownloaded = chunkDownloadedCounters.sumOf { it.get() }
            val completedChunks = chunks.mapIndexed { idx, c ->
                c.copy(
                    downloadedBytes = chunkDownloadedCounters[idx].get(),
                    status = ChunkStatus.COMPLETED,
                    speedBytesPerSec = 0L
                )
            }

            val completedItem = item.copy(
                status = DownloadStatus.COMPLETED,
                downloadedBytes = if (fileSize > 0) fileSize else finalDownloaded,
                speedBytesPerSec = 0L,
                completedAt = System.currentTimeMillis(),
                chunks = completedChunks
            )
            updateLiveItem(completedItem)
            database.downloadDao().update(DownloadEntity.fromDomainModel(completedItem))

            // Trigger completion listeners (Auto-install APK, notification, etc.)
            onDownloadCompletedListeners.forEach { listener ->
                try { listener(completedItem) } catch (e: Exception) { e.printStackTrace() }
            }

        } catch (e: CancellationException) {
            val current = liveDownloads[taskId] ?: item
            val paused = current.copy(status = DownloadStatus.PAUSED, speedBytesPerSec = 0L)
            updateLiveItem(paused)
            database.downloadDao().update(DownloadEntity.fromDomainModel(paused))
        } catch (e: Exception) {
            Log.e("SDM", "Download error caught safely for ${item.fileName}: ${e.message}")
            val current = liveDownloads[taskId] ?: item
            val failed = current.copy(
                status = DownloadStatus.FAILED,
                speedBytesPerSec = 0L,
                errorMessage = e.message ?: "Download connection failed"
            )
            updateLiveItem(failed)
            database.downloadDao().update(DownloadEntity.fromDomainModel(failed))
        } finally {
            downloadJobs.remove(taskId)
        }
    }

    /**
     * Downloads an individual chunk directly to its byte offset using RandomAccessFile.
     * Includes automatic fallback for servers that reject Range requests.
     */
    private suspend fun downloadChunkStream(
        url: String,
        chunk: ChunkInfo,
        targetFile: File,
        supportsRanges: Boolean,
        counter: AtomicLong,
        speedCounter: AtomicLong,
        isCancelled: AtomicBoolean
    ) = withContext(Dispatchers.IO) {
        val alreadyDownloaded = counter.get()
        if (chunk.totalBytes > 0 && alreadyDownloaded >= chunk.totalBytes) {
            return@withContext
        }

        val requestStart = chunk.startByte + alreadyDownloaded
        val requestEnd = chunk.endByte

        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", standardUserAgent)
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")
            .header("Connection", "keep-alive")

        val useRange = supportsRanges && chunk.endByte > 0
        if (useRange) {
            requestBuilder.header("Range", "bytes=$requestStart-$requestEnd")
        }

        var response: Response
        try {
            response = okHttpClient.newCall(requestBuilder.build()).execute()
        } catch (e: Exception) {
            throw Exception("Network error on chunk ${chunk.chunkIndex + 1}: ${e.message ?: "Connection timed out"}")
        }

        // If the server rejected range headers (e.g. HTTP 403 or 416), try single stream full GET fallback on chunk 0
        if (!response.isSuccessful && response.code != 206) {
            if (useRange && chunk.chunkIndex == 0) {
                response.close()
                val fallbackRequest = Request.Builder()
                    .url(url)
                    .header("User-Agent", standardUserAgent)
                    .header("Accept", "*/*")
                    .header("Connection", "keep-alive")
                    .build()
                try {
                    response = okHttpClient.newCall(fallbackRequest).execute()
                } catch (e: Exception) {
                    throw Exception("Fallback request failed: ${e.message}")
                }
            }

            if (!response.isSuccessful && response.code != 206) {
                val code = response.code
                response.close()
                throw Exception("HTTP $code: Server rejected download stream")
            }
        }

        val body = response.body ?: run {
            response.close()
            throw Exception("Response body empty for chunk ${chunk.chunkIndex + 1}")
        }

        try {
            body.byteStream().use { inputStream ->
                RandomAccessFile(targetFile, "rw").use { raf ->
                    raf.seek(requestStart)
                    val buffer = ByteArray(32 * 1024)
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        if (isCancelled.get()) break

                        speedLimiter.acquire(bytesRead)

                        raf.write(buffer, 0, bytesRead)
                        counter.addAndGet(bytesRead.toLong())
                        speedCounter.addAndGet(bytesRead.toLong())
                    }
                }
            }
        } finally {
            response.close()
        }
    }

    private fun createChunks(fileSize: Long, count: Int): List<ChunkInfo> {
        if (fileSize <= 0 || count <= 1) {
            return listOf(
                ChunkInfo(
                    chunkIndex = 0,
                    startByte = 0L,
                    endByte = if (fileSize > 0) fileSize - 1 else -1L,
                    status = ChunkStatus.PENDING
                )
            )
        }

        val chunkSize = fileSize / count
        val list = mutableListOf<ChunkInfo>()
        var start = 0L

        for (i in 0 until count) {
            val end = if (i == count - 1) fileSize - 1 else (start + chunkSize - 1)
            list.add(
                ChunkInfo(
                    chunkIndex = i,
                    startByte = start,
                    endByte = end,
                    status = ChunkStatus.PENDING
                )
            )
            start = end + 1
        }
        return list
    }

    private fun updateLiveItem(item: DownloadItem) {
        liveDownloads[item.id] = item
        _liveDownloadsFlow.value = liveDownloads.toMap()
    }

    fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun extractFileNameFromDisposition(disposition: String?): String? {
        if (disposition == null) return null
        val filenamePattern = "(?i)filename\\*?=['\"]?(?:UTF-8'')?([^;'\"]+)".toRegex()
        val match = filenamePattern.find(disposition) ?: return null
        return try {
            URLDecoder.decode(match.groupValues[1].trim(), "UTF-8")
        } catch (e: Exception) {
            match.groupValues[1].trim()
        }
    }

    private fun extractFileNameFromUrl(url: String): String {
        return try {
            val cleanUrl = url.substringBefore('?').substringBefore('#')
            val name = cleanUrl.substringAfterLast('/')
            if (name.isNotBlank()) URLDecoder.decode(name, "UTF-8") else "file"
        } catch (e: Exception) {
            "download_file"
        }
    }
}
