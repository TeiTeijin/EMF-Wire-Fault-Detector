package com.beep.emfreader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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

val SparklineHeight = 96.dp
val SparklineStroke = 1.6.dp
val SparklinePeakRadius = 3.dp
val SparklineLineColor = Color(0xFFD4D4D1)

@Composable
fun EmfSparkline(
    values: List<Int>,
    modifier: Modifier = Modifier,
    peakIndex: Int = -1,
    lineColor: Color = SparklineLineColor
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

        val points = values.mapIndexed { index, value ->
            Offset(xAt(index), yAt(value))
        }

        val line = Path()
        if (points.size == 1) {
            drawCircle(
                color = lineColor,
                radius = SparklineStroke.toPx() / 2f,
                center = points[0]
            )
            return@Canvas
        }

        // Catmull-Rom style smoothing so the trace reads like the design.
        line.moveTo(points[0].x, points[0].y)
        for (i in 0 until points.size - 1) {
            val p0 = points[(i - 1).coerceAtLeast(0)]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = points[(i + 2).coerceAtMost(points.size - 1)]
            val c1 = Offset(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f)
            val c2 = Offset(p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f)
            line.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
        }

        val stroke = Stroke(
            width = SparklineStroke.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        drawPath(path = line, color = lineColor, style = stroke)

        if (peakIndex in values.indices) {
            drawCircle(
                color = EmfSignalGreen,
                radius = SparklinePeakRadius.toPx(),
                center = Offset(xAt(peakIndex), yAt(values[peakIndex]))
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF181818)
@Composable
private fun EmfSparklinePreview() {
    EMFReaderTheme {
        EmfSparkline(
            values = listOf(18, 20, 24, 22, 30, 46, 71, 52, 34, 26, 22, 25, 31, 28, 24, 22),
            modifier = Modifier
                .fillMaxWidth()
                .height(SparklineHeight)
        )
    }
}
