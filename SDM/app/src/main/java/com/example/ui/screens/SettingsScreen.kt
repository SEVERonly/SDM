package com.example.ui.screens

import android.content.Context
import android.os.Build
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.MatrixGreen
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.ObsidianSurfaceHighlight
import com.example.ui.theme.TextCyanAccent
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        // Top Header
        Surface(
            color = ObsidianSurface,
            modifier = Modifier.fillMaxWidth().border(0.5.dp, ObsidianCardBorder)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    text = "ENGINE CONFIGURATION",
                    color = TextCyanAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Settings & Control Panel",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Download Speed Limiter
            item {
                SpeedLimiterSection(
                    currentLimit = settings.speedLimitKbps,
                    onSelectLimit = { viewModel.setSpeedLimit(it) }
                )
            }

            // 2. Max Concurrent Downloads (1 to 10)
            item {
                ConcurrentDownloadsSection(
                    currentMax = settings.maxConcurrentDownloads,
                    onUpdateMax = { viewModel.setMaxConcurrent(it) }
                )
            }

            // 3. Dynamic Multi-Threading (4, 8, 16, 32)
            item {
                MultiThreadingSection(
                    currentThreadCount = settings.defaultThreadCount,
                    onSelectThreads = { viewModel.setDefaultThreads(it) }
                )
            }

            // 4. Custom Storage Path Selector (with automatic default)
            item {
                StoragePathSection(
                    customPath = settings.customStoragePath,
                    defaultPathDisplay = viewModel.getDefaultStoragePath(),
                    onUpdatePath = { viewModel.setCustomStoragePath(it) }
                )
            }

            // 5. Network Rules (Wi-Fi Only vs Mobile)
            item {
                NetworkRulesSection(
                    wifiOnly = settings.wifiOnly,
                    onToggleWifiOnly = { viewModel.setWifiOnly(it) }
                )
            }

            // 6. Universal Package Installer & System Integration
            item {
                PackageInstallerSection(
                    autoInstall = settings.autoInstallApk,
                    onToggleAutoInstall = { viewModel.setAutoInstallApk(it) },
                    onCheckPermissions = {
                        val canInstall = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.packageManager.canRequestPackageInstalls()
                        } else true

                        if (!canInstall && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                android.net.Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    }
                )
            }

            // 7. Notifications
            item {
                NotificationSection(
                    notifyComplete = settings.notifyOnComplete,
                    onToggleNotify = { viewModel.setNotifyOnComplete(it) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}

@Composable
private fun SpeedLimiterSection(
    currentLimit: Int,
    onSelectLimit: (Int) -> Unit
) {
    SettingsCard(
        icon = Icons.Default.Speed,
        title = "Download Speed Limiter",
        subtitle = "Limit bandwidth to leave room for video streaming or gaming."
    ) {
        val speedPresets = listOf(
            0 to "Unlimited",
            500 to "500 KB/s",
            1024 to "1 MB/s",
            2048 to "2 MB/s",
            5120 to "5 MB/s",
            10240 to "10 MB/s"
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        ) {
            items(speedPresets) { (kbps, label) ->
                val isSelected = currentLimit == kbps
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) NeonCyan else ObsidianSurfaceElevated)
                        .border(1.dp, if (isSelected) NeonCyan else ObsidianCardBorder, RoundedCornerShape(8.dp))
                        .clickable { onSelectLimit(kbps) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("speed_limit_$kbps")
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) ObsidianBlack else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun ConcurrentDownloadsSection(
    currentMax: Int,
    onUpdateMax: (Int) -> Unit
) {
    SettingsCard(
        icon = Icons.Default.Tune,
        title = "Max Concurrent Downloads",
        subtitle = "Number of files downloaded simultaneously ($currentMax active limit)."
    ) {
        Column(modifier = Modifier.padding(top = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "1 File", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(NeonCyan)
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$currentMax CONCURRENT",
                        color = ObsidianBlack,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(text = "10 Files", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }

            Slider(
                value = currentMax.toFloat(),
                onValueChange = { onUpdateMax(it.toInt()) },
                valueRange = 1f..10f,
                steps = 8,
                colors = SliderDefaults.colors(
                    thumbColor = NeonCyan,
                    activeTrackColor = NeonCyan,
                    inactiveTrackColor = ObsidianSurfaceHighlight
                ),
                modifier = Modifier.fillMaxWidth().testTag("slider_concurrent_downloads")
            )
        }
    }
}

@Composable
private fun MultiThreadingSection(
    currentThreadCount: Int,
    onSelectThreads: (Int) -> Unit
) {
    SettingsCard(
        icon = Icons.Default.Stream,
        title = "Dynamic Multi-Threading (Parallel Streams)",
        subtitle = "Number of parallel HTTP range streams per file for maximum speeds."
    ) {
        val threadOptions = listOf(
            4 to "4 Streams (Standard)",
            8 to "8 Streams (Recommended)",
            16 to "16 Streams (High-Speed)",
            32 to "32 Streams (Extreme Bandwidth)"
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            threadOptions.forEach { (count, desc) ->
                val isSelected = currentThreadCount == count
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) ObsidianSurfaceHighlight else ObsidianSurfaceElevated)
                        .border(
                            1.dp,
                            if (isSelected) NeonCyan else ObsidianCardBorder,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectThreads(count) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("threads_$count"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = desc,
                            color = if (isSelected) TextPrimary else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(NeonCyan)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "ACTIVE",
                                color = ObsidianBlack,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoragePathSection(
    customPath: String,
    defaultPathDisplay: String,
    onUpdatePath: (String) -> Unit
) {
    var textValue by remember(customPath) { mutableStateOf(customPath) }

    SettingsCard(
        icon = Icons.Default.Folder,
        title = "Storage Path & Destination",
        subtitle = "Files default automatically to SDM_Downloads without failing."
    ) {
        Column(modifier = Modifier.padding(top = 10.dp)) {
            // Default Path Indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianSurfaceElevated)
                    .border(0.5.dp, ObsidianCardBorder, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Column {
                    Text(text = "DEFAULT FOLDER (AUTOMATIC):", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = defaultPathDisplay, color = NeonCyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Custom Folder Input
            OutlinedTextField(
                value = textValue,
                onValueChange = {
                    textValue = it
                    onUpdatePath(it)
                },
                placeholder = { Text("Leave empty to use automatic default", color = TextMuted, fontSize = 12.sp) },
                label = { Text("Custom Storage Folder Path", color = TextSecondary, fontSize = 12.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = ObsidianCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("input_storage_path")
            )

            if (textValue.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        textValue = ""
                        onUpdatePath("")
                    }) {
                        Text(text = "Reset to Default", color = NeonCyan, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkRulesSection(
    wifiOnly: Boolean,
    onToggleWifiOnly: (Boolean) -> Unit
) {
    SettingsCard(
        icon = Icons.Default.Wifi,
        title = "Network Rules & Data Saver",
        subtitle = "Control data usage over cellular and Wi-Fi networks."
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Download on Wi-Fi Only",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (wifiOnly) "Downloads pause automatically when on cellular data" else "Downloads will use Wi-Fi and Mobile Data freely",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Switch(
                checked = wifiOnly,
                onCheckedChange = onToggleWifiOnly,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianBlack,
                    checkedTrackColor = NeonCyan,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = ObsidianSurfaceHighlight
                ),
                modifier = Modifier.testTag("switch_wifi_only")
            )
        }
    }
}

@Composable
private fun PackageInstallerSection(
    autoInstall: Boolean,
    onToggleAutoInstall: (Boolean) -> Unit,
    onCheckPermissions: () -> Unit
) {
    val context = LocalContext.current
    val hasPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true
    }

    SettingsCard(
        icon = Icons.Default.InstallMobile,
        title = "Universal Package & Zip Installer",
        subtitle = "Auto-detect and launch .apk, .xapk, and .apks packages upon download."
    ) {
        Column(modifier = Modifier.padding(top = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Trigger APK Installation",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Instantly open PackageInstaller when an APK finishes",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Switch(
                    checked = autoInstall,
                    onCheckedChange = onToggleAutoInstall,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ObsidianBlack,
                        checkedTrackColor = MatrixGreen,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = ObsidianSurfaceHighlight
                    ),
                    modifier = Modifier.testTag("switch_auto_install")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Permission status banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianSurfaceElevated)
                    .border(0.5.dp, ObsidianCardBorder, RoundedCornerShape(8.dp))
                    .clickable { onCheckPermissions() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "INSTALL UNKNOWN APPS PERMISSION",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (hasPermission) "Permission Granted" else "Tap to Grant System Permission",
                        color = if (hasPermission) MatrixGreen else NeonCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (hasPermission) MatrixGreen.copy(alpha = 0.2f) else NeonCyan)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (hasPermission) "ACTIVE" else "CONFIGURE",
                        color = if (hasPermission) MatrixGreen else ObsidianBlack,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationSection(
    notifyComplete: Boolean,
    onToggleNotify: (Boolean) -> Unit
) {
    SettingsCard(
        icon = Icons.Default.Notifications,
        title = "Notifications & Sounds",
        subtitle = "Foreground progress notifications and completion banners."
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Notify on Download Complete",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Display notification banner when file download finishes",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Switch(
                checked = notifyComplete,
                onCheckedChange = onToggleNotify,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianBlack,
                    checkedTrackColor = NeonCyan,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = ObsidianSurfaceHighlight
                )
            )
        }
    }
}

@Composable
private fun SettingsCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ObsidianSurfaceElevated)
                        .border(0.5.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(text = subtitle, color = TextSecondary, fontSize = 12.sp)
                }
            }

            content()
        }
    }
}
