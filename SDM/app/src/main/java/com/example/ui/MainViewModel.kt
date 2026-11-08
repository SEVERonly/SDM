package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SdmApplication
import com.example.engine.ResolvedFileInfo
import com.example.installer.ArchiveEntryItem
import com.example.model.AppSettings
import com.example.model.DownloadItem
import com.example.model.DownloadStatus
import com.example.service.DownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class LinkCaptureData(
    val url: String,
    val suggestedFileName: String = "",
    val fileSize: Long = -1L,
    val supportsRanges: Boolean = true,
    val mimeType: String? = null,
    val threadCount: Int = 8,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class ArchiveViewerData(
    val archiveFile: File,
    val entries: List<ArchiveEntryItem> = emptyList(),
    val isExtracting: Boolean = false,
    val extractionProgress: String = "",
    val extractedSuccess: Boolean = false,
    val errorMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SdmApplication
    private val database = app.database
    private val engine = app.downloadEngine
    private val settingsManager = app.settingsManager
    private val packageHelper = app.packageManagerHelper

    val settings: StateFlow<AppSettings> = settingsManager.settings

    val totalSpeedBytes: StateFlow<Long> = engine.totalSpeedBytes
    val speedHistory: StateFlow<List<Long>> = engine.speedHistory

    // Merge database list with live in-memory chunk progress and instantaneous speeds
    val allDownloads: StateFlow<List<DownloadItem>> = combine(
        database.downloadDao().getAllDownloads(),
        engine.liveDownloadsFlow
    ) { dbEntities, liveMap ->
        dbEntities.map { entity ->
            val liveItem = liveMap[entity.id]
            if (liveItem != null) {
                liveItem
            } else {
                entity.toDomainModel()
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeDownloads: StateFlow<List<DownloadItem>> = allDownloads
        .map { list ->
            list.filter {
                it.status == DownloadStatus.DOWNLOADING ||
                        it.status == DownloadStatus.CONNECTING ||
                        it.status == DownloadStatus.PAUSED ||
                        it.status == DownloadStatus.QUEUED
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedDownloads: StateFlow<List<DownloadItem>> = allDownloads
        .map { list ->
            list.filter { it.status == DownloadStatus.COMPLETED }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected task for live thread visualizer
    private val _selectedTaskForThreads = MutableStateFlow<String?>(null)
    val selectedTaskForThreads: StateFlow<String?> = _selectedTaskForThreads.asStateFlow()

    // Overlay dialog when a link is captured from browser / outside
    private val _linkCaptureDialog = MutableStateFlow<LinkCaptureData?>(null)
    val linkCaptureDialog: StateFlow<LinkCaptureData?> = _linkCaptureDialog.asStateFlow()

    // Archive inspector / extractor dialog state
    private val _archiveViewer = MutableStateFlow<ArchiveViewerData?>(null)
    val archiveViewer: StateFlow<ArchiveViewerData?> = _archiveViewer.asStateFlow()

    fun selectTaskForThreadVisualizer(taskId: String?) {
        _selectedTaskForThreads.value = taskId
    }

    /**
     * Called when a URL is captured from browser via intent or manual input
     */
    fun openLinkCaptureDialog(url: String) {
        val currentSettings = settings.value
        _linkCaptureDialog.value = LinkCaptureData(
            url = url,
            threadCount = currentSettings.defaultThreadCount,
            isLoading = true
        )

        viewModelScope.launch(Dispatchers.IO) {
            val result = engine.inspectUrl(url)
            if (result.isSuccess) {
                val info = result.getOrThrow()
                _linkCaptureDialog.value = _linkCaptureDialog.value?.copy(
                    suggestedFileName = info.fileName,
                    fileSize = info.fileSize,
                    supportsRanges = info.supportsRanges,
                    mimeType = info.mimeType,
                    isLoading = false
                )
            } else {
                val fallbackName = url.substringBefore('?').substringAfterLast('/').ifBlank { "download" }
                _linkCaptureDialog.value = _linkCaptureDialog.value?.copy(
                    suggestedFileName = fallbackName,
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun dismissLinkCaptureDialog() {
        _linkCaptureDialog.value = null
    }

    fun updateLinkCaptureThreads(threads: Int) {
        _linkCaptureDialog.value = _linkCaptureDialog.value?.copy(threadCount = threads)
    }

    fun updateLinkCaptureFileName(newName: String) {
        _linkCaptureDialog.value = _linkCaptureDialog.value?.copy(suggestedFileName = newName)
    }

    /**
     * Start downloading from the capture dialog or directly
     */
    fun confirmDownload(
        url: String,
        fileName: String,
        threadCount: Int = settings.value.defaultThreadCount,
        fileSize: Long = -1L,
        supportsRanges: Boolean = true,
        mimeType: String? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val targetDir = settingsManager.resolveDownloadDirectory()
            val safeName = if (fileName.isNotBlank()) fileName else "file_${System.currentTimeMillis()}"
            val targetFile = File(targetDir, safeName)

            val downloadItem = DownloadItem(
                id = UUID.randomUUID().toString(),
                url = url.trim(),
                fileName = safeName,
                filePath = targetFile.absolutePath,
                fileSize = fileSize,
                downloadedBytes = 0L,
                status = DownloadStatus.QUEUED,
                threadCount = threadCount,
                supportsRanges = supportsRanges,
                mimeType = mimeType,
                createdAt = System.currentTimeMillis()
            )

            // Insert to DB
            database.downloadDao().insert(com.example.data.DownloadEntity.fromDomainModel(downloadItem))

            // Start foreground service
            DownloadService.start(app)

            // Hand off to engine
            engine.startDownload(downloadItem)

            _selectedTaskForThreads.value = downloadItem.id
            _linkCaptureDialog.value = null
        }
    }

    /**
     * Batch Downloader: queue multiple URLs simultaneously
     */
    fun startBatchDownloads(
        urls: List<String>,
        threadCount: Int = settings.value.defaultThreadCount,
        autoInstall: Boolean = settings.value.autoInstallApk
    ) {
        if (urls.isEmpty()) return
        if (autoInstall != settings.value.autoInstallApk) {
            settingsManager.updateAutoInstallApk(autoInstall)
        }

        viewModelScope.launch(Dispatchers.IO) {
            val targetDir = settingsManager.resolveDownloadDirectory()
            DownloadService.start(app)

            urls.forEachIndexed { index, rawUrl ->
                val url = rawUrl.trim()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    val cleanUrl = url.substringBefore('?').substringBefore('#')
                    val nameCandidate = cleanUrl.substringAfterLast('/').ifBlank { "batch_${System.currentTimeMillis()}_$index" }
                    val targetFile = File(targetDir, nameCandidate)

                    val downloadItem = DownloadItem(
                        id = UUID.randomUUID().toString(),
                        url = url,
                        fileName = nameCandidate,
                        filePath = targetFile.absolutePath,
                        fileSize = -1L,
                        downloadedBytes = 0L,
                        status = DownloadStatus.QUEUED,
                        threadCount = threadCount,
                        supportsRanges = true,
                        createdAt = System.currentTimeMillis() + index
                    )

                    database.downloadDao().insert(com.example.data.DownloadEntity.fromDomainModel(downloadItem))
                    engine.startDownload(downloadItem)

                    if (index == 0) {
                        _selectedTaskForThreads.value = downloadItem.id
                    }
                }
            }
        }
    }

    fun pauseDownload(taskId: String) {
        engine.pauseDownload(taskId)
    }

    fun resumeDownload(item: DownloadItem) {
        DownloadService.start(app)
        engine.startDownload(item)
    }

    fun cancelDownload(taskId: String, deleteFile: Boolean = true) {
        engine.cancelDownload(taskId, deleteFile)
    }

    fun openDownloadedFile(item: DownloadItem) {
        packageHelper.openFile(File(item.filePath), item.mimeType)
    }

    fun installDownloadedPackage(item: DownloadItem) {
        val file = File(item.filePath)
        if (item.fileName.endsWith(".xapk", ignoreCase = true)) {
            viewModelScope.launch {
                packageHelper.installXapk(file)
            }
        } else {
            packageHelper.installApk(file)
        }
    }

    fun inspectArchiveFile(file: File) {
        viewModelScope.launch {
            _archiveViewer.value = ArchiveViewerData(archiveFile = file, isExtracting = false)
            val entries = packageHelper.listArchiveEntries(file)
            _archiveViewer.value = _archiveViewer.value?.copy(entries = entries)
        }
    }

    fun extractCurrentArchive() {
        val current = _archiveViewer.value ?: return
        viewModelScope.launch {
            val outputFolder = File(current.archiveFile.parentFile, "${current.archiveFile.nameWithoutExtension}_extracted")
            _archiveViewer.value = current.copy(isExtracting = true, extractionProgress = "Extracting...")

            val res = packageHelper.extractArchive(current.archiveFile, outputFolder) { count, name ->
                _archiveViewer.value = _archiveViewer.value?.copy(
                    extractionProgress = "Extracted $count files ($name)"
                )
            }

            if (res.isSuccess) {
                _archiveViewer.value = _archiveViewer.value?.copy(
                    isExtracting = false,
                    extractedSuccess = true,
                    extractionProgress = "Extraction completed to ${outputFolder.name}!"
                )
            } else {
                _archiveViewer.value = _archiveViewer.value?.copy(
                    isExtracting = false,
                    errorMessage = res.exceptionOrNull()?.message ?: "Extraction failed"
                )
            }
        }
    }

    fun dismissArchiveViewer() {
        _archiveViewer.value = null
    }

    fun clearCompletedDownloads() {
        viewModelScope.launch {
            database.downloadDao().clearCompleted()
        }
    }

    // Settings actions
    fun setSpeedLimit(kbps: Int) = settingsManager.updateSpeedLimit(kbps)
    fun setMaxConcurrent(count: Int) = settingsManager.updateMaxConcurrent(count)
    fun setDefaultThreads(threads: Int) = settingsManager.updateDefaultThreadCount(threads)
    fun setCustomStoragePath(path: String) = settingsManager.updateCustomStoragePath(path)
    fun setWifiOnly(enabled: Boolean) = settingsManager.updateWifiOnly(enabled)
    fun setAutoInstallApk(enabled: Boolean) = settingsManager.updateAutoInstallApk(enabled)
    fun setNotifyOnComplete(enabled: Boolean) = settingsManager.updateNotifyOnComplete(enabled)
    fun getDefaultStoragePath(): String = settingsManager.getDefaultStoragePathDisplay()
}
