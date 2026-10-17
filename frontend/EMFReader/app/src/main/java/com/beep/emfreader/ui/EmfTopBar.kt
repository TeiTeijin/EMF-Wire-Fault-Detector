package com.beep.emfreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.beep.emfreader.R
import com.beep.emfreader.ui.theme.EMFReaderTheme
import com.beep.emfreader.ui.theme.EmfText
import com.beep.emfreader.ui.theme.EmfTextFaint

val EmfTopBarHeight = 44.dp

val EmfNavLineHeight = 2.dp
val EmfNavLineColor = EmfTextFaint.copy(alpha = 0.12f)

/**
 * Hairline under the nav bar, shared so every screen carries the same line.
 * The scan screen fills this same line with scan progress.
 */
@Composable
fun EmfNavDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(EmfNavLineHeight)
            .background(EmfNavLineColor)
    )
}

@Composable
fun EmfTopBar(
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.app_title)
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(EmfTopBarHeight),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = EmfText
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1B1B1A)
@Composable
private fun EmfTopBarPreview() {
    EMFReaderTheme {
        EmfTopBar()
    }
}
