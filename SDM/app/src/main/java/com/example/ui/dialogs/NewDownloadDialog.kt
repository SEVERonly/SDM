package com.example.ui.dialogs

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.NeonOutlineCardButton
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

enum class NewDownloadMode {
    SINGLE,
    BATCH
}

@Composable
fun NewDownloadDialog(
    defaultThreads: Int,
    initialAutoInstall: Boolean = true,
    onDismiss: () -> Unit,
    onSubmitSingleUrl: (url: String, threads: Int, autoInstall: Boolean) -> Unit,
    onSubmitBatchUrls: (urls: List<String>, threads: Int, autoInstall: Boolean) -> Unit
) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(NewDownloadMode.SINGLE) }

    // State for single download
    var singleUrlText by remember { mutableStateOf("") }
    var singleThreads by remember { mutableIntStateOf(if (defaultThreads in listOf(4, 8, 16)) defaultThreads else 8) }
    var autoInstallApk by remember { mutableStateOf(initialAutoInstall) }
    var singleError by remember { mutableStateOf<String?>(null) }

    // State for batch download
    var batchText by remember { mutableStateOf("") }
    var batchThreads by remember { mutableIntStateOf(if (defaultThreads in listOf(4, 8, 16)) defaultThreads else 8) }
    var batchError by remember { mutableStateOf<String?>(null) }

    val parsedBatchUrls = remember(batchText) {
        batchText.lines()
            .map { it.trim() }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .distinct()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = ObsidianSurface,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .border(1.dp, ObsidianCardBorder, RoundedCornerShape(24.dp))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Top Header with Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SMART DOWNLOAD MANAGER",
                            color = TextCyanAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = if (mode == NewDownloadMode.SINGLE) "Quick Download" else "Batch Downloader",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Apple-style Mode Switcher Segmented Control
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianSurfaceElevated)
                        .border(1.dp, ObsidianBorderSubtle, RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    // Single Link Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (mode == NewDownloadMode.SINGLE) ObsidianSurfaceHighlight else Color.Transparent)
                            .clickable { mode = NewDownloadMode.SINGLE }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = if (mode == NewDownloadMode.SINGLE) NeonCyan else TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Single URL",
                                color = if (mode == NewDownloadMode.SINGLE) TextPrimary else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (mode == NewDownloadMode.SINGLE) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }

                    // Batch Downloader Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (mode == NewDownloadMode.BATCH) ObsidianSurfaceHighlight else Color.Transparent)
                            .clickable { mode = NewDownloadMode.BATCH }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DynamicFeed,
                                contentDescription = null,
                                tint = if (mode == NewDownloadMode.BATCH) NeonCyan else TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Batch Downloader",
                                color = if (mode == NewDownloadMode.BATCH) TextPrimary else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (mode == NewDownloadMode.BATCH) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (mode == NewDownloadMode.SINGLE) {
                    // 1. Single URL Input
                    OutlinedTextField(
                        value = singleUrlText,
                        onValueChange = {
                            singleUrlText = it
                            singleError = null
                        },
                        label = { Text("Download URL (HTTP/HTTPS)", color = TextSecondary, fontSize = 12.sp) },
                        placeholder = { Text("https://example.com/app.apk", color = TextMuted, fontSize = 12.sp) },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clip = cm?.primaryClip
                                    if (clip != null && clip.itemCount > 0) {
                                        val text = clip.getItemAt(0).text?.toString() ?: ""
                                        if (text.startsWith("http://") || text.startsWith("https://")) {
                                            singleUrlText = text
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste from Clipboard",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = ObsidianCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_quick_url")
                    )

                    if (singleError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = singleError ?: "", color = Color(0xFFFF3D71), fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Thread count selection chips (4x, 8x, 16x)
                    Text(
                        text = "PARALLEL STREAMS",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(4, 8, 16).forEach { threads ->
                            val isSelected = singleThreads == threads
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) ObsidianSurfaceHighlight else ObsidianSurfaceElevated)
                                    .border(
                                        1.dp,
                                        if (isSelected) NeonCyan else ObsidianBorderSubtle,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { singleThreads = threads }
                                    .padding(vertical = 10.dp)
                                    .testTag("chip_threads_$threads"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${threads}x Streams",
                                    color = if (isSelected) NeonCyan else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Toggle: 'Auto-Install APK after download'
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, ObsidianBorderSubtle, RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.InstallMobile,
                                    contentDescription = null,
                                    tint = MatrixGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Auto-Install APK",
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Launch installer upon download complete",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Switch(
                                checked = autoInstallApk,
                                onCheckedChange = { autoInstallApk = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ObsidianBlack,
                                    checkedTrackColor = MatrixGreen,
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = ObsidianSurfaceHighlight
                                ),
                                modifier = Modifier.testTag("switch_quick_autoinstall")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Sleek Neon Outline Start Button Card
                    NeonOutlineCardButton(
                        text = "START DOWNLOAD",
                        icon = Icons.Default.Bolt,
                        glowColor = NeonCyan,
                        contentColor = NeonCyan,
                        onClick = {
                            val trimmed = singleUrlText.trim()
                            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                                onSubmitSingleUrl(trimmed, singleThreads, autoInstallApk)
                            } else {
                                singleError = "Please enter a valid HTTP or HTTPS download URL"
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("btn_quick_start")
                    )

                } else {
                    // 2. Batch Downloader View
                    Column {
                        OutlinedTextField(
                            value = batchText,
                            onValueChange = {
                                batchText = it
                                batchError = null
                            },
                            label = { Text("Paste Multiple URLs (one per line)", color = TextSecondary, fontSize = 12.sp) },
                            placeholder = {
                                Text(
                                    "https://example.com/file1.zip\nhttps://example.com/app2.apk\nhttps://example.com/video3.mp4",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val clip = cm?.primaryClip
                                        if (clip != null && clip.itemCount > 0) {
                                            val text = clip.getItemAt(0).text?.toString() ?: ""
                                            if (text.isNotBlank()) {
                                                batchText = if (batchText.isBlank()) text else "$batchText\n$text"
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "Paste Clipboard",
                                        tint = NeonCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = ObsidianCardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            minLines = 4,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth().testTag("input_batch_urls")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // URL counter status badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (parsedBatchUrls.isNotEmpty()) MatrixGreen.copy(alpha = 0.15f) else ObsidianSurfaceElevated)
                                    .border(
                                        0.5.dp,
                                        if (parsedBatchUrls.isNotEmpty()) MatrixGreen.copy(alpha = 0.4f) else ObsidianBorderSubtle,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${parsedBatchUrls.size} VALID URLS DETECTED",
                                    color = if (parsedBatchUrls.isNotEmpty()) MatrixGreen else TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            if (batchText.isNotBlank()) {
                                Text(
                                    text = "Clear",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .clickable { batchText = "" }
                                        .padding(4.dp)
                                )
                            }
                        }

                        if (batchError != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = batchError ?: "", color = Color(0xFFFF3D71), fontSize = 11.sp)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Thread Count selection for Batch
                        Text(
                            text = "STREAMS PER BATCH FILE",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(4, 8, 16).forEach { threads ->
                                val isSelected = batchThreads == threads
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) ObsidianSurfaceHighlight else ObsidianSurfaceElevated)
                                    .border(
                                        1.dp,
                                        if (isSelected) NeonCyan else ObsidianBorderSubtle,
                                        RoundedCornerShape(10.dp)
                                    )
                                        .clickable { batchThreads = threads }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${threads}x Streams",
                                        color = if (isSelected) NeonCyan else TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Sleek Neon Outline Batch Download Button
                        NeonOutlineCardButton(
                            text = "QUEUE ${parsedBatchUrls.size} BATCH DOWNLOADS",
                            icon = Icons.Default.DynamicFeed,
                            glowColor = NeonCyan,
                            contentColor = NeonCyan,
                            onClick = {
                                if (parsedBatchUrls.isNotEmpty()) {
                                    onSubmitBatchUrls(parsedBatchUrls, batchThreads, autoInstallApk)
                                } else {
                                    batchError = "Please paste at least one valid HTTP or HTTPS download URL"
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("btn_batch_start")
                        )
                    }
                }
            }
        }
    }
}
