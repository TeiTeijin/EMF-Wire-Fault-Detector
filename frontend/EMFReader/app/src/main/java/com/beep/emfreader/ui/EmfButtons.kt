package com.beep.emfreader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.beep.emfreader.ui.theme.EmfBackground
import com.beep.emfreader.ui.theme.EmfBorderSubtle
import com.beep.emfreader.ui.theme.EmfText
import com.beep.emfreader.ui.theme.EmfTextMuted

val EmfButtonMaxWidth = 342.dp
val EmfButtonHeight = 56.dp
val EmfButtonShape = CircleShape
val EmfButtonBorderWidth = 1.2.dp
val EmfCompactButtonHeight = 44.dp
val EmfCompactButtonPaddingH = 22.dp
val EmfCompactButtonPaddingV = 10.dp

@Composable
fun EmfPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        shape = EmfButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = EmfText,
            contentColor = EmfBackground
        ),
        modifier = modifier
            .widthIn(max = EmfButtonMaxWidth)
            .fillMaxWidth()
            .height(EmfButtonHeight)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
fun EmfCompactButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        shape = EmfButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = EmfText,
            contentColor = EmfBackground
        ),
        contentPadding = PaddingValues(
            horizontal = EmfCompactButtonPaddingH,
            vertical = EmfCompactButtonPaddingV
        ),
        modifier = modifier.height(EmfCompactButtonHeight)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
fun EmfSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        shape = EmfButtonShape,
        border = BorderStroke(EmfButtonBorderWidth, EmfBorderSubtle),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = EmfText),
        modifier = modifier
            .widthIn(max = EmfButtonMaxWidth)
            .fillMaxWidth()
            .height(EmfButtonHeight)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}
