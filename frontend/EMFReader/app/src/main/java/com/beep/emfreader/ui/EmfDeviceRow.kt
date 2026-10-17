package com.beep.emfreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.beep.emfreader.R
import com.beep.emfreader.data.EmfDevice
import com.beep.emfreader.data.EmfSppContract
import com.beep.emfreader.ui.theme.EMFReaderTheme
import com.beep.emfreader.ui.theme.EmfBackground
import com.beep.emfreader.ui.theme.EmfText

val EmfDeviceRowBorderWidth = 1.dp
val EmfDeviceRowPaddingH = 16.dp
val EmfDeviceRowPaddingV = 16.dp

@Composable
fun EmfDeviceRow(
    device: EmfDevice,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(EmfBackground)
            .border(EmfDeviceRowBorderWidth, EmfText, EmfButtonShape)
            .padding(horizontal = EmfDeviceRowPaddingH, vertical = EmfDeviceRowPaddingV),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = device.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = EmfText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )

        EmfCompactButton(
            text = stringResource(R.string.scan_cta_connect),
            onClick = onConnect,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1B1B1A)
@Composable
private fun EmfDeviceRowPreview() {
    EMFReaderTheme {
        EmfDeviceRow(
            device = EmfDevice(EmfSppContract.RELAY_NAME, "20:50:0D:2A:61:B2"),
            onConnect = {}
        )
    }
}
