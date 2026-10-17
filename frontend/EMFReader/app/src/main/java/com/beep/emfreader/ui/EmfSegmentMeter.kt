package com.beep.emfreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.beep.emfreader.ui.theme.EMFReaderTheme
import com.beep.emfreader.ui.theme.EmfSignalGreen
import com.beep.emfreader.ui.theme.EmfText
import kotlin.math.floor

val SegmentCount = 10
val SegmentWidth = 26.dp
val SegmentHeight = 28.dp
val SegmentGap = 5.dp
val SegmentShape = RoundedCornerShape(3.dp)
val SegmentThresholdWidth = 2.dp
val SegmentThresholdOverhang = 4.dp

/** How many of the ten cells are lit for [percent]. */
fun segmentCount(percent: Int): Int {
    val steps = percent.coerceIn(0, 100) / (100f / SegmentCount)
    // Round half up, not ties-to-even: otherwise 5% would light zero cells and
    // the meter would appear to drop a cell on the way up.
    return floor(steps + 0.5f).toInt().coerceIn(0, SegmentCount)
}

/**
 * One channel across ten cells. 10% lights a single cell, so the row reads as
 * a level at a glance instead of needing the number.
 *
 * [thresholdAfterCells] draws a notch at the boundary after that many cells.
 * It is painted over the row rather than laid out beside it, so marking a
 * threshold does not steal width from the cells and squeeze them.
 */
@Composable
fun EmfSegmentMeter(
    percent: Int,
    modifier: Modifier = Modifier,
    thresholdAfterCells: Int? = null
) {
    val filled = segmentCount(percent)

    Box(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(SegmentGap)) {
            repeat(SegmentCount) { index ->
                Box(
                    modifier = Modifier
                        .width(SegmentWidth)
                        .height(SegmentHeight)
                        .background(
                            color = if (index < filled) EmfSignalGreen else EmfText,
                            shape = SegmentShape
                        )
                )
            }
        }

        if (thresholdAfterCells != null && thresholdAfterCells in 1 until SegmentCount) {
            // Centre of the gap after that many cells.
            val pitch = SegmentWidth.value + SegmentGap.value
            val boundary =
                ((thresholdAfterCells - 1) * pitch + SegmentWidth.value + SegmentGap.value / 2f).dp

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = boundary, y = -SegmentThresholdOverhang / 2)
                    .width(SegmentThresholdWidth)
                    .height(SegmentHeight + SegmentThresholdOverhang)
                    .background(EmfText)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1B1B1A)
@Composable
private fun EmfSegmentMeterPreview() {
    EMFReaderTheme {
        EmfSegmentMeter(percent = 30)
    }
}
