package com.beep.emfreader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.beep.emfreader.ui.theme.EMFReaderTheme
import com.beep.emfreader.ui.theme.EmfBorderSubtle
import com.beep.emfreader.ui.theme.EmfSignalGreen
import com.beep.emfreader.ui.theme.EmfTextMuted

val SparklineHeight = 56.dp
val SparklineStroke = 1.5.dp
val SparklinePeakRadius = 3.dp
val SparklineFillTopAlpha = 0.10f

/**
 * Recent levels of one reading, oldest at the left. The point of the trace is
 * the shape: a steady wire draws a flat-ish line, arcing load draws a spike, so
 * a fault is visible as motion rather than only as a colour change.
 */
@Composable
fun EmfSparkline(
    values: List<Int>,
    modifier: Modifier = Modifier,
    peakIndex: Int = -1
) {
    Canvas(modifier) {
        if (values.isEmpty()) {
            drawLine(
                color = EmfBorderSubtle,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx()
            )
            return@Canvas
        }

        val span = (values.size - 1).coerceAtLeast(1).toFloat()
        val stepX = size.width / span
        val xAt = { index: Int -> if (values.size == 1) size.width / 2f else index * stepX }
        val yAt = { value: Int -> size.height - (value.coerceIn(0, 100) / 100f) * size.height }

        val line = Path()
        val fill = Path()
        values.forEachIndexed { index, value ->
            val x = xAt(index)
            val y = yAt(value)
            if (index == 0) {
                line.moveTo(x, y)
                fill.moveTo(x, size.height)
                fill.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(xAt(values.lastIndex), size.height)
        fill.close()

        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                colors = listOf(EmfTextMuted.copy(alpha = SparklineFillTopAlpha), Color.Transparent)
            )
        )

        val stroke = Stroke(
            width = SparklineStroke.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        if (values.size == 1) {
            drawCircle(
                color = EmfTextMuted,
                radius = SparklineStroke.toPx() / 2f,
                center = Offset(xAt(0), yAt(values[0]))
            )
        } else {
            drawPath(path = line, color = EmfTextMuted, style = stroke)
        }

        if (peakIndex in values.indices) {
            val x = xAt(peakIndex)
            val y = yAt(values[peakIndex])
            drawLine(
                color = EmfSignalGreen,
                start = Offset(x, size.height),
                end = Offset(x, y),
                strokeWidth = 1.dp.toPx()
            )
            drawCircle(
                color = EmfSignalGreen,
                radius = SparklinePeakRadius.toPx(),
                center = Offset(x, y)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1B1B1A)
@Composable
private fun EmfSparklinePreview() {
    EMFReaderTheme {
        EmfSparkline(
            values = listOf(18, 20, 24, 22, 30, 46, 71, 52, 34, 26, 22, 25, 31, 28, 24, 22),
            peakIndex = 6,
            modifier = Modifier
                .fillMaxWidth()
                .height(SparklineHeight)
        )
    }
}
