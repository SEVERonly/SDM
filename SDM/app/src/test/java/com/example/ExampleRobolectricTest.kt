package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.SettingsManager
import com.example.model.ChunkInfo
import com.example.model.ChunkStatus
import com.example.model.DownloadItem
import com.example.model.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SDM", appName)
  }

  @Test
  fun `test settings manager default values and storage resolution`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val settingsManager = SettingsManager(context)

    val settings = settingsManager.settings.value
    assertEquals(8, settings.defaultThreadCount)
    assertEquals(3, settings.maxConcurrentDownloads)
    assertEquals(0, settings.speedLimitKbps)

    val dir = settingsManager.resolveDownloadDirectory()
    assertTrue(dir.exists())
  }

  @Test
  fun `test download item properties`() {
    val apkItem = DownloadItem(
      id = "1",
      url = "https://example.com/app.apk",
      fileName = "app.apk",
      filePath = "/tmp/app.apk",
      fileSize = 1000L,
      downloadedBytes = 500L,
      status = DownloadStatus.DOWNLOADING
    )
    assertTrue(apkItem.isApk)
    assertEquals(0.5f, apkItem.progress, 0.001f)
    assertEquals("APK", apkItem.extension)

    val zipItem = DownloadItem(
      id = "2",
      url = "https://example.com/archive.zip",
      fileName = "archive.zip",
      filePath = "/tmp/archive.zip"
    )
    assertTrue(zipItem.isArchive)
  }
}
