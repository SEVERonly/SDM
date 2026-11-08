package com.example.installer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class ArchiveEntryItem(
    val name: String,
    val size: Long,
    val compressedSize: Long,
    val isDirectory: Boolean
) {
    val simpleName: String get() = name.trimEnd('/').substringAfterLast('/')
}

class PackageAndArchiveManager(private val context: Context) {

    /**
     * Checks whether the app has permission to install unknown apps (Android 8.0+)
     */
    fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Creates an intent to request permission to install unknown apps
     */
    fun createInstallPermissionIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            null
        }
    }

    /**
     * Installs an APK file using FileProvider and standard Package Installer Intent
     */
    fun installApk(file: File): Boolean {
        if (!file.exists()) return false

        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(installIntent)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Inspects zip / xapk / apks archive contents without extracting
     */
    suspend fun listArchiveEntries(file: File): List<ArchiveEntryItem> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<ArchiveEntryItem>()
        if (!file.exists()) return@withContext entries

        try {
            ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    entries.add(
                        ArchiveEntryItem(
                            name = entry.name,
                            size = entry.size,
                            compressedSize = entry.compressedSize,
                            isDirectory = entry.isDirectory
                        )
                    )
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        entries
    }

    /**
     * Extracts an archive to the specified output folder with progress callback
     */
    suspend fun extractArchive(
        archiveFile: File,
        destinationDir: File,
        onProgress: (extractedFiles: Int, currentFileName: String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        if (!archiveFile.exists()) {
            return@withContext Result.failure(Exception("Archive file does not exist"))
        }

        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }

        var count = 0
        try {
            ZipInputStream(BufferedInputStream(FileInputStream(archiveFile))).use { zis ->
                val buffer = ByteArray(8192)
                var entry: ZipEntry? = zis.nextEntry

                while (entry != null) {
                    // Prevent zip slip vulnerability
                    val targetFile = File(destinationDir, entry.name)
                    val canonicalDest = destinationDir.canonicalPath
                    val canonicalTarget = targetFile.canonicalPath

                    if (!canonicalTarget.startsWith(canonicalDest + File.separator) &&
                        canonicalTarget != canonicalDest
                    ) {
                        throw SecurityException("Zip Slip detected: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        BufferedOutputStream(FileOutputStream(targetFile)).use { bos ->
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                bos.write(buffer, 0, len)
                            }
                        }
                        count++
                        onProgress(count, entry.name)
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            Result.success(destinationDir)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Handles .xapk / .apks installation by locating the base APK within the archive
     */
    suspend fun installXapk(xapkFile: File): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val extractDir = File(context.cacheDir, "xapk_extracted_${System.currentTimeMillis()}")
            extractDir.mkdirs()

            val extractResult = extractArchive(xapkFile, extractDir) { _, _ -> }
            if (extractResult.isFailure) {
                return@withContext Result.failure(extractResult.exceptionOrNull() ?: Exception("Failed to extract XAPK"))
            }

            // Find base APK or any APK file inside
            val apkFiles = extractDir.walkTopDown().filter { it.extension.equals("apk", ignoreCase = true) }.toList()
            val baseApk = apkFiles.firstOrNull { it.name.equals("base.apk", ignoreCase = true) }
                ?: apkFiles.firstOrNull()

            if (baseApk != null) {
                val launched = installApk(baseApk)
                Result.success(launched)
            } else {
                Result.failure(Exception("No installable APK found inside archive"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generic file opener using Intent.ACTION_VIEW and system apps
     */
    fun openFile(file: File, mimeType: String? = null): Boolean {
        if (!file.exists()) return false

        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val resolvedMime = mimeType ?: getMimeTypeFromFile(file)

            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, resolvedMime)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(Intent.createChooser(openIntent, "Open with").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun getMimeTypeFromFile(file: File): String {
        return when (file.extension.lowercase()) {
            "apk" -> "application/vnd.android.package-archive"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            "pdf" -> "application/pdf"
            "mp4", "mkv", "webm" -> "video/*"
            "mp3", "m4a", "wav", "flac" -> "audio/*"
            "jpg", "jpeg", "png", "webp", "gif" -> "image/*"
            "txt", "log" -> "text/plain"
            else -> "*/*"
        }
    }
}
