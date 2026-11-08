package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.model.DownloadStatus
import com.example.ui.MainViewModel
import com.example.ui.dialogs.ArchiveExtractorDialog
import com.example.ui.dialogs.LinkCaptureDialog
import com.example.ui.dialogs.NewDownloadDialog
import com.example.ui.screens.AnalyticsAndThreadsScreen
import com.example.ui.screens.CompletedScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MatrixGreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

enum class SdmNavDestination(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DASHBOARD("dashboard", "Active", Icons.Filled.Download, Icons.Outlined.Download),
    COMPLETED("completed", "Completed", Icons.Filled.FolderZip, Icons.Outlined.FolderZip),
    THREADS("threads", "Streams", Icons.Filled.Memory, Icons.Outlined.Memory),
    SETTINGS("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission granted or denied handled gracefully
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request POST_NOTIFICATIONS on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Handle incoming browser download link intent
        handleIncomingIntent(intent)

        setContent {
            MyApplicationTheme {
                SdmMainApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        val action = intent.action
        val data: Uri? = intent.data

        var detectedUrl: String? = null

        if (Intent.ACTION_VIEW == action && data != null) {
            detectedUrl = data.toString()
        } else if (Intent.ACTION_SEND == action) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) {
                val urlMatch = "https?://[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]+".toRegex().find(text)
                detectedUrl = urlMatch?.value ?: text.trim()
            }
        }

        if (!detectedUrl.isNullOrBlank() && (detectedUrl.startsWith("http://") || detectedUrl.startsWith("https://"))) {
            viewModel.openLinkCaptureDialog(detectedUrl)
        }
    }
}

@Composable
fun SdmMainApp(viewModel: MainViewModel) {
    var currentDestination by remember { mutableStateOf(SdmNavDestination.DASHBOARD) }
    var showManualAddDialog by remember { mutableStateOf(false) }

    val activeDownloads by viewModel.activeDownloads.collectAsState()
    val completedDownloads by viewModel.completedDownloads.collectAsState()
    val linkCaptureData by viewModel.linkCaptureDialog.collectAsState()
    val archiveViewerData by viewModel.archiveViewer.collectAsState()
    val settings by viewModel.settings.collectAsState()

    val activeDownloadingCount = activeDownloads.count { it.status == DownloadStatus.DOWNLOADING }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(ObsidianBlack),
        bottomBar = {
            NavigationBar(
                containerColor = ObsidianSurface,
                modifier = Modifier
                    .border(0.5.dp, ObsidianCardBorder)
                    .navigationBarsPadding()
            ) {
                SdmNavDestination.values().forEach { destination ->
                    val isSelected = destination == currentDestination

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentDestination = destination },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (destination == SdmNavDestination.DASHBOARD && activeDownloadingCount > 0) {
                                        Badge(containerColor = NeonCyan, contentColor = ObsidianBlack) {
                                            Text(
                                                text = "$activeDownloadingCount",
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else if (destination == SdmNavDestination.COMPLETED && completedDownloads.isNotEmpty()) {
                                        Badge(containerColor = ObsidianSurfaceElevated, contentColor = MatrixGreen) {
                                            Text(
                                                text = "${completedDownloads.size}",
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = destination.title,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        },
                        label = {
                            Text(
                                text = destination.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ObsidianBlack,
                            selectedTextColor = NeonCyan,
                            indicatorColor = NeonCyan,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_${destination.route}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(ObsidianBlack)
        ) {
            when (currentDestination) {
                SdmNavDestination.DASHBOARD -> DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToThreads = { taskId ->
                        viewModel.selectTaskForThreadVisualizer(taskId)
                        currentDestination = SdmNavDestination.THREADS
                    },
                    onOpenNewDownload = { showManualAddDialog = true }
                )
                SdmNavDestination.COMPLETED -> CompletedScreen(viewModel = viewModel)
                SdmNavDestination.THREADS -> AnalyticsAndThreadsScreen(viewModel = viewModel)
                SdmNavDestination.SETTINGS -> SettingsScreen(viewModel = viewModel)
            }

            // Link Capture Dialog (Triggered automatically by browser intents or manual link)
            linkCaptureData?.let { captureData ->
                LinkCaptureDialog(
                    data = captureData,
                    destinationPath = viewModel.getDefaultStoragePath(),
                    onDismiss = { viewModel.dismissLinkCaptureDialog() },
                    onConfirm = { url, fileName, threads ->
                        viewModel.confirmDownload(
                            url = url,
                            fileName = fileName,
                            threadCount = threads,
                            fileSize = captureData.fileSize,
                            supportsRanges = captureData.supportsRanges,
                            mimeType = captureData.mimeType
                        )
                    }
                )
            }

            // Quick Download & Batch Downloader Dialog
            if (showManualAddDialog) {
                NewDownloadDialog(
                    defaultThreads = settings.defaultThreadCount,
                    initialAutoInstall = settings.autoInstallApk,
                    onDismiss = { showManualAddDialog = false },
                    onSubmitSingleUrl = { url, threads, autoInstall ->
                        showManualAddDialog = false
                        viewModel.setDefaultThreads(threads)
                        viewModel.setAutoInstallApk(autoInstall)
                        viewModel.openLinkCaptureDialog(url)
                    },
                    onSubmitBatchUrls = { urls, threads, autoInstall ->
                        showManualAddDialog = false
                        viewModel.startBatchDownloads(urls, threads, autoInstall)
                    }
                )
            }

            // Built-in Zip/RAR Archive Inspector & Extractor Dialog
            archiveViewerData?.let { archiveData ->
                ArchiveExtractorDialog(
                    data = archiveData,
                    onDismiss = { viewModel.dismissArchiveViewer() },
                    onExtract = { viewModel.extractCurrentArchive() }
                )
            }
        }
    }
}
