package com.example

import com.example.model.ChunkInfo
import com.example.model.ChunkStatus
import com.example.model.DownloadItem
import com.example.model.DownloadStatus
import com.example.ui.components.calculateEta
import com.example.ui.components.formatBytes
import com.example.ui.components.formatSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testDownloadItemProperties() {
    val apkItem = DownloadItem(
      id = "task-1",
      url = "https://example.com/app.apk",
      fileName = "sample_test.apk",
      filePath = "/storage/sample_test.apk",
      fileSize = 10_000_000L,
      downloadedBytes = 5_000_000L,
      status = DownloadStatus.DOWNLOADING,
      threadCount = 16
    )

    assertTrue(apkItem.isApk)
    assertFalse(apkItem.isArchive)
    assertEquals(0.5f, apkItem.progress, 0.001f)
    assertEquals("APK", apkItem.extension)
  }

  @Test
  fun testArchiveItemProperties() {
    val zipItem = DownloadItem(
      id = "task-2",
      url = "https://example.com/data.xapk",
      fileName = "game_pack.xapk",
      filePath = "/storage/game_pack.xapk"
    )

    assertTrue(zipItem.isApk)
    assertTrue(zipItem.isArchive)
  }

  @Test
  fun testCategoryMatching() {
    val video = DownloadItem("v1", "https://ex.com/film.mp4", "film.mp4", "/storage/film.mp4")
    val audio = DownloadItem("a1", "https://ex.com/song.flac", "song.flac", "/storage/song.flac")
    val apk = DownloadItem("p1", "https://ex.com/app.apk", "app.apk", "/storage/app.apk")

    assertTrue(video.isVideo)
    assertTrue(video.matchesCategory(com.example.model.DownloadCategory.VIDEOS))
    assertFalse(video.matchesCategory(com.example.model.DownloadCategory.AUDIO))

    assertTrue(audio.isAudio)
    assertTrue(audio.matchesCategory(com.example.model.DownloadCategory.AUDIO))
    assertFalse(audio.matchesCategory(com.example.model.DownloadCategory.VIDEOS))

    assertTrue(apk.isApk)
    assertTrue(apk.matchesCategory(com.example.model.DownloadCategory.APKS))
  }

  @Test
  fun testFormattingUtilities() {
    assertEquals("0 B", formatBytes(0L))
    assertEquals("500 B", formatBytes(500L))
    assertEquals("1.0 KB", formatBytes(1024L))
    assertEquals("1.0 MB", formatBytes(1024L * 1024L))
    assertEquals("1.50 GB", formatBytes((1.5 * 1024L * 1024L * 1024L).toLong()))

    assertEquals("0 KB/s", formatSpeed(0L))
    assertEquals("512 B/s", formatSpeed(512L))
    assertEquals("1.0 KB/s", formatSpeed(1024L))
    assertEquals("2.50 MB/s", formatSpeed((2.5 * 1024L * 1024L).toLong()))

    assertEquals("--:--", calculateEta(0L, 0L, 0L))
    assertEquals("00:10", calculateEta(1000L, 0L, 100L))
    assertEquals("01:00", calculateEta(6000L, 0L, 100L))
  }

  @Test
  fun testChunkCalculation() {
    val chunk = ChunkInfo(
      chunkIndex = 0,
      startByte = 0L,
      endByte = 999L,
      downloadedBytes = 500L,
      status = ChunkStatus.DOWNLOADING
    )

    assertEquals(1000L, chunk.totalBytes)
    assertEquals(0.5f, chunk.progress, 0.001f)
  }
}
