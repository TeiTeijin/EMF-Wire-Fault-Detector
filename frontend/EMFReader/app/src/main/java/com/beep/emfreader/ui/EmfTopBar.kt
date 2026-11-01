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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.beep.emfreader.R
import com.beep.emfreader.ui.theme.EMFReaderTheme
import com.beep.emfreader.ui.theme.EmfDivider
import com.beep.emfreader.ui.theme.EmfText
import com.beep.emfreader.ui.theme.emfLabelStyle

val EmfTopBarHeight = 56.dp

val EmfNavLineHeight = 1.dp
val EmfNavLineColor = EmfDivider

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
    title: String = stringResource(R.string.app_title),
    statusBarPadding: Boolean = true
) {
    val base = if (statusBarPadding) {
        modifier.windowInsetsPadding(WindowInsets.statusBars)
    } else {
        modifier
    }
    Column(
        modifier = base
            .fillMaxWidth()
            .height(EmfTopBarHeight),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = emfLabelStyle(15),
            color = EmfText
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF181818)
@Composable
private fun EmfTopBarPreview() {
    EMFReaderTheme {
        Column {
            EmfTopBar()
            EmfNavDivider()
        }
    }
}
