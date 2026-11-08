package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.DownloadItem
import com.example.model.DownloadStatus
import com.example.model.ChunkInfo
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val url: String,
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val downloadedBytes: Long,
    val status: String,
    val threadCount: Int,
    val supportsRanges: Boolean,
    val errorMessage: String?,
    val createdAt: Long,
    val completedAt: Long?,
    val mimeType: String?,
    val chunksJson: String = "[]"
) {
    fun toDomainModel(speedBytesPerSec: Long = 0L, liveChunks: List<ChunkInfo>? = null): DownloadItem {
        val parsedChunks = liveChunks ?: parseChunks(chunksJson)
        return DownloadItem(
            id = id,
            url = url,
            fileName = fileName,
            filePath = filePath,
            fileSize = fileSize,
            downloadedBytes = downloadedBytes,
            status = try { DownloadStatus.valueOf(status) } catch (e: Exception) { DownloadStatus.QUEUED },
            speedBytesPerSec = speedBytesPerSec,
            threadCount = threadCount,
            supportsRanges = supportsRanges,
            errorMessage = errorMessage,
            createdAt = createdAt,
            completedAt = completedAt,
            mimeType = mimeType,
            chunks = parsedChunks
        )
    }

    companion object {
        fun fromDomainModel(item: DownloadItem): DownloadEntity {
            return DownloadEntity(
                id = item.id,
                url = item.url,
                fileName = item.fileName,
                filePath = item.filePath,
                fileSize = item.fileSize,
                downloadedBytes = item.downloadedBytes,
                status = item.status.name,
                threadCount = item.threadCount,
                supportsRanges = item.supportsRanges,
                errorMessage = item.errorMessage,
                createdAt = item.createdAt,
                completedAt = item.completedAt,
                mimeType = item.mimeType,
                chunksJson = serializeChunks(item.chunks)
            )
        }

        fun serializeChunks(chunks: List<ChunkInfo>): String {
            val jsonArray = JSONArray()
            for (c in chunks) {
                val obj = JSONObject()
                obj.put("i", c.chunkIndex)
                obj.put("s", c.startByte)
                obj.put("e", c.endByte)
                obj.put("d", c.downloadedBytes)
                obj.put("st", c.status.name)
                jsonArray.put(obj)
            }
            return jsonArray.toString()
        }

        fun parseChunks(json: String): List<ChunkInfo> {
            val list = mutableListOf<ChunkInfo>()
            try {
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        ChunkInfo(
                            chunkIndex = obj.getInt("i"),
                            startByte = obj.getLong("s"),
                            endByte = obj.getLong("e"),
                            downloadedBytes = obj.getLong("d"),
                            status = try {
                                com.example.model.ChunkStatus.valueOf(obj.getString("st"))
                            } catch (e: Exception) {
                                com.example.model.ChunkStatus.PENDING
                            }
                        )
                    )
                }
            } catch (e: Exception) {
                // Return empty list if parsing fails
            }
            return list
        }
    }
}
