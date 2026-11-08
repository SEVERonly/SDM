package com.example.model

enum class DownloadStatus {
    QUEUED,
    CONNECTING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class ChunkStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

enum class DownloadCategory(val label: String) {
    ALL("All"),
    APKS("APKs"),
    VIDEOS("Videos"),
    ARCHIVES("Archives"),
    AUDIO("Audio")
}

data class ChunkInfo(
    val chunkIndex: Int,
    val startByte: Long,
    val endByte: Long,
    val downloadedBytes: Long = 0L,
    val status: ChunkStatus = ChunkStatus.PENDING,
    val speedBytesPerSec: Long = 0L
) {
    val totalBytes: Long get() = (endByte - startByte + 1).coerceAtLeast(0L)
    val progress: Float get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

data class DownloadItem(
    val id: String,
    val url: String,
    val fileName: String,
    val filePath: String,
    val fileSize: Long = -1L,
    val downloadedBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val speedBytesPerSec: Long = 0L,
    val threadCount: Int = 8,
    val supportsRanges: Boolean = true,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val mimeType: String? = null,
    val chunks: List<ChunkInfo> = emptyList()
) {
    val progress: Float
        get() = if (fileSize > 0) (downloadedBytes.toFloat() / fileSize).coerceIn(0f, 1f) else 0f

    val isApk: Boolean
        get() = fileName.endsWith(".apk", ignoreCase = true) ||
                fileName.endsWith(".xapk", ignoreCase = true) ||
                fileName.endsWith(".apks", ignoreCase = true)

    val isArchive: Boolean
        get() = fileName.endsWith(".zip", ignoreCase = true) ||
                fileName.endsWith(".rar", ignoreCase = true) ||
                fileName.endsWith(".7z", ignoreCase = true) ||
                fileName.endsWith(".tar", ignoreCase = true) ||
                fileName.endsWith(".gz", ignoreCase = true) ||
                fileName.endsWith(".xapk", ignoreCase = true)

    val isVideo: Boolean
        get() = fileName.endsWith(".mp4", ignoreCase = true) ||
                fileName.endsWith(".mkv", ignoreCase = true) ||
                fileName.endsWith(".webm", ignoreCase = true) ||
                fileName.endsWith(".avi", ignoreCase = true) ||
                fileName.endsWith(".mov", ignoreCase = true)

    val isAudio: Boolean
        get() = fileName.endsWith(".mp3", ignoreCase = true) ||
                fileName.endsWith(".m4a", ignoreCase = true) ||
                fileName.endsWith(".flac", ignoreCase = true) ||
                fileName.endsWith(".wav", ignoreCase = true) ||
                fileName.endsWith(".aac", ignoreCase = true)

    fun matchesCategory(category: DownloadCategory): Boolean {
        return when (category) {
            DownloadCategory.ALL -> true
            DownloadCategory.APKS -> isApk
            DownloadCategory.VIDEOS -> isVideo
            DownloadCategory.ARCHIVES -> isArchive
            DownloadCategory.AUDIO -> isAudio
        }
    }

    val extension: String
        get() = fileName.substringAfterLast('.', "").uppercase()
}

data class AppSettings(
    val speedLimitKbps: Int = 0, // 0 = Unlimited, otherwise speed in KB/s
    val maxConcurrentDownloads: Int = 3, // 1 to 10
    val defaultThreadCount: Int = 8, // 4, 8, 16, 32
    val customStoragePath: String = "", // Empty means default to /SDM_Downloads
    val wifiOnly: Boolean = false,
    val autoInstallApk: Boolean = true,
    val notifyOnComplete: Boolean = true
)
