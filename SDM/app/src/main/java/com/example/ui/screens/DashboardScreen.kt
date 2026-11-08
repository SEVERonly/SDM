package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DownloadCategory
import com.example.model.DownloadItem
import com.example.model.DownloadStatus
import com.example.ui.MainViewModel
import com.example.ui.components.FileTypeIcon
import com.example.ui.components.GlowingProgressBar
import com.example.ui.components.MultiThreadSegmentBar
import com.example.ui.components.NeonOutlineCardButton
import com.example.ui.components.RealTimeSpeedChart
import com.example.ui.components.SmartStorageIndicator
import com.example.ui.components.calculateEta
import com.example.ui.components.formatBytes
import com.example.ui.components.formatSpeed
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.LaserRed
import com.example.ui.theme.MatrixGreen
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianBorderSubtle
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.ObsidianSurfaceHighlight
import com.example.ui.theme.TextCyanAccent
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToThreads: (String) -> Unit,
    onOpenNewDownload: () -> Unit
) {
    val activeDownloads by viewModel.activeDownloads.collectAsState()
    val totalSpeed by viewModel.totalSpeedBytes.collectAsState()
    val speedHistory by viewModel.speedHistory.collectAsState()
    var showSpeedChart by remember { mutableStateOf(true) }
    var selectedCategory by remember { mutableStateOf(DownloadCategory.ALL) }

    val filteredActive = remember(activeDownloads, selectedCategory) {
        activeDownloads.filter { it.matchesCategory(selectedCategory) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Apple-style Minimalist Header Stats Bar
            HeaderStatsBar(
                totalSpeed = totalSpeed,
                activeCount = activeDownloads.count { it.status == DownloadStatus.DOWNLOADING },
                showChart = showSpeedChart,
                onToggleChart = { showSpeedChart = !showSpeedChart }
            )

            // Real-time Speed Chart
            AnimatedVisibility(
                visible = showSpeedChart,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    RealTimeSpeedChart(speedHistory = speedHistory)
                }
            }

            // Top Smart Storage Indicator (Real-time progress bar showing free vs used internal storage)
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                SmartStorageIndicator()
            }

            // File Categorization Chips (All, APKs, Videos, Archives, Audio)
            CategoryFilterBar(
                selectedCategory = selectedCategory,
                onSelectCategory = { selectedCategory = it }
            )

            // Downloads List or Empty State
            if (activeDownloads.isEmpty()) {
                EmptyActiveDownloadsView(
                    onAddUrl = onOpenNewDownload,
                    onQuickTest = { url, name, threads ->
                        viewModel.confirmDownload(
                            url = url,
                            fileName = name,
                            threadCount = threads
                        )
                    }
                )
            } else if (filteredActive.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No active downloads in '${selectedCategory.label}'",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredActive, key = { it.id }) { item ->
                        ActiveDownloadCard(
                            item = item,
                            onPause = { viewModel.pauseDownload(item.id) },
                            onResume = { viewModel.resumeDownload(item) },
                            onCancel = { viewModel.cancelDownload(item.id) },
                            onViewThreads = {
                                viewModel.selectTaskForThreadVisualizer(item.id)
                                onNavigateToThreads(item.id)
                            }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(88.dp))
                    }
                }
            }
        }

        // Floating Download Button (+) with glowing cyan styling
        FloatingActionButton(
            onClick = onOpenNewDownload,
            containerColor = NeonCyan,
            contentColor = ObsidianBlack,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("fab_add_download")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Quick Download (+)",
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun HeaderStatsBar(
    totalSpeed: Long,
    activeCount: Int,
    showChart: Boolean,
    onToggleChart: () -> Unit
) {
    Surface(
        color = ObsidianSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, ObsidianBorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(ObsidianSurfaceElevated)
                        .border(1.dp, NeonCyan.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Ultra Speed",
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "BANDWIDTH ENGINE",
                        color = TextCyanAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = formatSpeed(totalSpeed),
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ObsidianSurfaceElevated)
                        .border(0.5.dp, ObsidianBorderSubtle, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$activeCount ACTIVE",
                        color = if (activeCount > 0) MatrixGreen else TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = onToggleChart,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (showChart) Icons.Default.KeyboardArrowUp else Icons.Default.ShowChart,
                        contentDescription = "Toggle Speed Chart",
                        tint = if (showChart) NeonCyan else TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterBar(
    selectedCategory: DownloadCategory,
    onSelectCategory: (DownloadCategory) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(DownloadCategory.values()) { category ->
            val isSelected = category == selectedCategory
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) ObsidianSurfaceHighlight else ObsidianSurface)
                    .border(
                        1.dp,
                        if (isSelected) NeonCyan else ObsidianBorderSubtle,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelectCategory(category) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .testTag("filter_${category.name.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = category.label,
                    color = if (isSelected) NeonCyan else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ActiveDownloadCard(
    item: DownloadItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onViewThreads: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(18.dp))
            .testTag("download_card_${item.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: File Icon, Name, Size & Streams
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FileTypeIcon(fileName = item.fileName)

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.fileName,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${formatBytes(item.downloadedBytes)} / ${if (item.fileSize > 0) formatBytes(item.fileSize) else "Unknown"}",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(ObsidianSurfaceElevated)
                                .border(0.5.dp, ObsidianBorderSubtle, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "${item.threadCount}x",
                                color = NeonCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Quick Controls: Pause/Resume, Cancel
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.CONNECTING) {
                        IconButton(
                            onClick = onPause,
                            modifier = Modifier.size(34.dp).testTag("btn_pause_${item.id}")
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause", tint = Color(0xFFFBBF24), modifier = Modifier.size(18.dp))
                        }
                    } else if (item.status == DownloadStatus.PAUSED || item.status == DownloadStatus.FAILED) {
                        IconButton(
                            onClick = onResume,
                            modifier = Modifier.size(34.dp).testTag("btn_resume_${item.id}")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = MatrixGreen, modifier = Modifier.size(18.dp))
                        }
                    }

                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(34.dp).testTag("btn_cancel_${item.id}")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = LaserRed, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Glowing Progress Bar
            GlowingProgressBar(progress = item.progress, height = 6.dp)

            Spacer(modifier = Modifier.height(8.dp))

            // Multi-thread Segment bar
            if (item.chunks.isNotEmpty()) {
                MultiThreadSegmentBar(chunks = item.chunks)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Stats and Sleek Action Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (item.status == DownloadStatus.DOWNLOADING) formatSpeed(item.speedBytesPerSec) else item.status.name,
                        color = if (item.status == DownloadStatus.DOWNLOADING) NeonCyan else TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Text(
                        text = "${(item.progress * 100).toInt()}%",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    if (item.status == DownloadStatus.DOWNLOADING) {
                        Text(
                            text = "ETA ${calculateEta(item.fileSize, item.downloadedBytes, item.speedBytesPerSec)}",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Sleek Neon Cyan Outline Card Button for Stream Inspection
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ObsidianSurfaceElevated)
                        .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable { onViewThreads() }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Stream,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "STREAMS",
                            color = NeonCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            if (item.errorMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.errorMessage,
                    color = LaserRed,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun EmptyActiveDownloadsView(
    onAddUrl: () -> Unit,
    onQuickTest: (url: String, name: String, threads: Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(ObsidianSurfaceElevated)
                .border(1.dp, NeonCyan.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ElectricBolt,
                contentDescription = null,
                tint = NeonCyan,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "No Active Downloads",
            color = TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Tap (+) below to paste single/batch links or test SDM multi-thread speed benchmarks.",
            color = TextSecondary,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Quick Benchmark Card
        Card(
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ObsidianCardBorder, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "⚡ SPEED BENCHMARKS (DIRECT TEST)",
                    color = TextCyanAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                BenchmarkFileRow(
                    title = "10MB High-Speed Test File",
                    subtitle = "16 Streams • CDN Speed Test",
                    onClick = {
                        onQuickTest(
                            "https://proof.ovh.net/files/10Mb.dat",
                            "benchmark_10mb.dat",
                            16
                        )
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                BenchmarkFileRow(
                    title = "100MB Extreme 32-Thread Test",
                    subtitle = "32 Streams • Maximum Bandwidth Test",
                    onClick = {
                        onQuickTest(
                            "https://speed.hetzner.de/100MB.bin",
                            "benchmark_100mb.bin",
                            32
                        )
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                BenchmarkFileRow(
                    title = "Sample Android APK Package",
                    subtitle = "8 Streams • Auto-Package Installer Test",
                    onClick = {
                        onQuickTest(
                            "https://f-droid.org/F-Droid.apk",
                            "fdroid_app.apk",
                            8
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun BenchmarkFileRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ObsidianSurfaceElevated)
            .border(0.5.dp, ObsidianBorderSubtle, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = NeonCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(NeonCyan)
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(text = "DOWNLOAD", color = ObsidianBlack, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
    }
}
