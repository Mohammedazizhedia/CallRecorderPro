package com.callrecorder.pro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Simple waveform from pre-computed min/max buckets; tapping seeks.
 * Buckets are computed once per file by decodePeaks() below.
 */
@Composable
fun WaveformView(
    peaks: FloatArray,       // normalized 0..1 interleaved min,max per bucket
    progress: Float,         // 0..1
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    playedColor: Color = Color(0xFF0B57D0),
    remainingColor: Color = Color(0xFF9AA5B1)
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .pointerInput(peaks) {
                detectTapGestures { pos ->
                    if (peaks.isNotEmpty() && size.width > 0) {
                        onSeek((pos.x / size.width).coerceIn(0f, 1f))
                    }
                }
            }
    ) {
        if (peaks.isEmpty()) return@Canvas
        val bucketCount = peaks.size / 2
        val bucketW = size.width / bucketCount
        val midY = size.height / 2
        for (i in 0 until bucketCount) {
            val minV = peaks[i * 2]
            val maxV = peaks[i * 2 + 1]
            val x = i * bucketW + bucketW / 2
            val frac = i.toFloat() / bucketCount
            val color = if (frac <= progress) playedColor else remainingColor
            drawLine(
                color = color,
                start = Offset(x, midY - maxV * midY),
                end = Offset(x, midY - minV * midY),
                strokeWidth = bucketW * 0.6f
            )
        }
    }
}
