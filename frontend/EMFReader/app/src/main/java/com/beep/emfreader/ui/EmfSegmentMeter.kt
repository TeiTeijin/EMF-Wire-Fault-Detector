package com.beep.emfreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.beep.emfreader.ui.theme.EMFReaderTheme
import com.beep.emfreader.ui.theme.EmfSignalGreen
import com.beep.emfreader.ui.theme.EmfText
import kotlin.math.floor

val SegmentCount = 10
val SegmentHeight = 18.dp
val SegmentGap = 6.dp
val SegmentShape = RoundedCornerShape(4.dp)

fun segmentCount(percent: Int): Int {
    val steps = percent.coerceIn(0, 100) / (100f / SegmentCount)
    return floor(steps + 0.5f).toInt().coerceIn(0, SegmentCount)
}

@Composable
fun EmfSegmentMeter(
    percent: Int,
    modifier: Modifier = Modifier,
    filledColor: Color = EmfSignalGreen,
    emptyColor: Color = Color(0xFF2E2E2E)
) {
    val filled = segmentCount(percent)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(SegmentGap)
    ) {
        repeat(SegmentCount) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(SegmentHeight)
                    .background(
                        color = if (index < filled) filledColor else emptyColor,
                        shape = SegmentShape
                    )
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF181818)
@Composable
private fun EmfSegmentMeterPreview() {
    EMFReaderTheme {
        Box(Modifier.width(320.dp)) {
            EmfSegmentMeter(percent = 30)
        }
    }
}
