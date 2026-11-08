package com.example.ui.components

import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ChunkInfo
import com.example.model.ChunkStatus
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.MatrixGreen
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonCyanBright
import com.example.ui.theme.NeonCyanGlow
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
import java.util.Locale

data class StorageStats(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long,
    val usedRatio: Float
)

/**
 * Smart Storage Indicator: Real-time progress bar showing free vs used internal storage
 */
@Composable
fun SmartStorageIndicator(modifier: Modifier = Modifier) {
    val stats = remember {
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val total = totalBlocks * blockSize
            val free = availableBlocks * blockSize
            val used = (total - free).coerceAtLeast(0L)
            val ratio = if (total > 0L) (used.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
            StorageStats(total, free, used, ratio)
        } catch (e: Exception) {
            StorageStats(128L * 1024 * 1024 * 1024, 64L * 1024 * 1024 * 1024, 64L * 1024 * 1024 * 1024, 0.5f)
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(ObsidianSurfaceElevated)
                            .border(0.5.dp, NeonCyan.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "INTERNAL STORAGE",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.8.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ObsidianSurfaceElevated)
                        .border(0.5.dp, ObsidianBorderSubtle, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${(stats.usedRatio * 100).toInt()}% USED",
                        color = NeonCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Glowing Storage Bar
            GlowingProgressBar(progress = stats.usedRatio, height = 7.dp)

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${formatBytes(stats.freeBytes)} available",
                    color = MatrixGreen,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "Total ${formatBytes(stats.totalBytes)}",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * Sleek Neon Cyan Outline Card Button (Replaces generic flat buttons)
 */
@Composable
fun NeonOutlineCardButton(
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glowColor: Color = NeonCyan,
    contentColor: Color = TextPrimary,
    containerColor: Color = ObsidianSurfaceElevated
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .border(1.dp, glowColor.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = glowColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp
        )
    }
}

/**
 * High-tech Glowing Cyan Progress Bar
 */
@Composable
fun GlowingProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 8.dp,
    glowColor: Color = NeonCyan
) {
    val clampedProgress = progress.coerceIn(0f, 1f)

    val infiniteTransition = rememberInfiniteTransition(label = "glow_pulse")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(ObsidianSurfaceElevated)
            .border(0.5.dp, ObsidianBorderSubtle, RoundedCornerShape(height / 2))
    ) {
        if (clampedProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clampedProgress)
                    .fillMaxHeight()
                    .drawBehind {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    glowColor.copy(alpha = 0.85f * shimmerAlpha),
                                    ElectricBlue.copy(alpha = 0.95f)
                                )
                            )
                        )
                    }
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                glowColor.copy(alpha = shimmerAlpha),
                                ElectricBlue
                            )
                        )
                    )
            )
        }
    }
}

/**
 * Segmented Thread Stream Chunk Bar: Displays each thread's segment visually
 */
@Composable
fun MultiThreadSegmentBar(
    chunks: List<ChunkInfo>,
    modifier: Modifier = Modifier
) {
    if (chunks.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(2.5.dp))
            .background(ObsidianSurfaceElevated),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        chunks.forEach { chunk ->
            val color = when (chunk.status) {
                ChunkStatus.COMPLETED -> MatrixGreen
                ChunkStatus.DOWNLOADING -> NeonCyan
                ChunkStatus.FAILED -> Color(0xFFFF3D71)
                ChunkStatus.PENDING -> ObsidianSurfaceHighlight
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(color)
            )
        }
    }
}

/**
 * Real-time Glowing Speed Curve Line Chart
 */
@Composable
fun RealTimeSpeedChart(
    speedHistory: List<Long>,
    modifier: Modifier = Modifier
) {
    val maxSpeed = (speedHistory.maxOrNull() ?: 1L).coerceAtLeast(1024L)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(82.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianSurface)
            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            if (speedHistory.size < 2) return@Canvas

            val stepX = width / (speedHistory.size - 1)
            val path = Path()
            val fillPath = Path()

            speedHistory.forEachIndexed { index, speed ->
                val x = index * stepX
                val normalizedY = 1f - (speed.toFloat() / maxSpeed.toFloat()).coerceIn(0f, 1f)
                val y = normalizedY * (height - 12.dp.toPx()) + 6.dp.toPx()

                if (index == 0) {
                    path.moveTo(x, y)
                    fillPath.moveTo(x, height)
                    fillPath.lineTo(x, y)
                } else {
                    val prevX = (index - 1) * stepX
                    val prevSpeed = speedHistory[index - 1]
                    val prevNormY = 1f - (prevSpeed.toFloat() / maxSpeed.toFloat()).coerceIn(0f, 1f)
                    val prevY = prevNormY * (height - 12.dp.toPx()) + 6.dp.toPx()

                    val controlX = (prevX + x) / 2
                    path.cubicTo(controlX, prevY, controlX, y, x, y)
                    fillPath.cubicTo(controlX, prevY, controlX, y, x, y)
                }

                if (index == speedHistory.lastIndex) {
                    fillPath.lineTo(x, height)
                    fillPath.close()
                }
            }

            // Draw glowing fill gradient
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        NeonCyanGlow,
                        Color.Transparent
                    )
                )
            )

            // Draw main neon line
            drawPath(
                path = path,
                color = NeonCyan,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw current speed dot on the last point
            val lastX = width
            val lastSpeed = speedHistory.lastOrNull() ?: 0L
            val lastNormY = 1f - (lastSpeed.toFloat() / maxSpeed.toFloat()).coerceIn(0f, 1f)
            val lastY = lastNormY * (height - 12.dp.toPx()) + 6.dp.toPx()

            drawCircle(
                color = NeonCyan,
                radius = 3.5.dp.toPx(),
                center = Offset(lastX, lastY)
            )
            drawCircle(
                color = Color.White,
                radius = 1.8.dp.toPx(),
                center = Offset(lastX, lastY)
            )
        }

        // Peak speed label
        Text(
            text = "PEAK: ${formatSpeed(maxSpeed)}",
            color = TextMuted,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

/**
 * File icon mapper based on filename extension
 */
@Composable
fun FileTypeIcon(fileName: String, modifier: Modifier = Modifier) {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    val (icon, color) = when (ext) {
        "apk", "apks" -> Icons.Default.Android to MatrixGreen
        "xapk", "zip", "rar", "7z", "tar", "gz" -> Icons.Default.Archive to NeonCyan
        "mp4", "mkv", "webm", "avi", "mov" -> Icons.Default.VideoFile to ElectricBlue
        "mp3", "m4a", "wav", "flac" -> Icons.Default.Audiotrack to Color(0xFFC084FC)
        "pdf", "doc", "docx", "txt" -> Icons.Default.Description to Color(0xFFFBBF24)
        else -> Icons.Default.InsertDriveFile to TextSecondary
    }

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(ObsidianSurfaceElevated)
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = ext,
            tint = color,
            modifier = Modifier.size(22.dp)
        )
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    return when {
        bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0L) return "0 KB/s"
    return when {
        bytesPerSec >= 1024L * 1024L -> String.format(Locale.US, "%.2f MB/s", bytesPerSec / (1024.0 * 1024.0))
        bytesPerSec >= 1024L -> String.format(Locale.US, "%.1f KB/s", bytesPerSec / 1024.0)
        else -> "$bytesPerSec B/s"
    }
}

fun calculateEta(totalBytes: Long, downloadedBytes: Long, speedBytesPerSec: Long): String {
    if (totalBytes <= 0L || speedBytesPerSec <= 0L || downloadedBytes >= totalBytes) {
        return "--:--"
    }
    val remainingBytes = totalBytes - downloadedBytes
    val seconds = remainingBytes / speedBytesPerSec
    val mins = seconds / 60
    val secs = seconds % 60
    return if (mins >= 60) {
        val hrs = mins / 60
        val remMins = mins % 60
        String.format(Locale.US, "%02d:%02d:%02d", hrs, remMins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}
