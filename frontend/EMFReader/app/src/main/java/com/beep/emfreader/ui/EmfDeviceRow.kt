package com.beep.emfreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import com.beep.emfreader.R
import com.beep.emfreader.data.EmfDevice
import com.beep.emfreader.data.EmfContract
import com.beep.emfreader.ui.theme.EMFReaderTheme
import com.beep.emfreader.ui.theme.EmfSurface
import com.beep.emfreader.ui.theme.EmfText
import com.beep.emfreader.ui.theme.EmfTextMuted
import com.beep.emfreader.ui.theme.InterFamily

val EmfDeviceRowShape = RoundedCornerShape(22.dp)
val EmfDeviceRowPaddingH = 20.dp
val EmfDeviceRowPaddingV = 16.dp
val EmfDeviceRowNameGap = 4.dp

@Composable
fun EmfDeviceRow(
    device: EmfDevice,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(EmfSurface, EmfDeviceRowShape)
            .padding(horizontal = EmfDeviceRowPaddingH, vertical = EmfDeviceRowPaddingV),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = device.name,
                fontSize = 19.sp,
                lineHeight = 24.sp,
                fontFamily = InterFamily,
                fontWeight = FontWeight.Bold,
                color = EmfText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(EmfDeviceRowNameGap))
            Text(
                text = stringResource(R.string.scan_device_transport),
                style = MaterialTheme.typography.bodySmall,
                color = EmfTextMuted,
                maxLines = 1
            )
        }

        EmfCompactButton(
            text = stringResource(R.string.scan_cta_connect),
            onClick = onConnect,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF181818)
@Composable
private fun EmfDeviceRowPreview() {
    EMFReaderTheme {
        EmfDeviceRow(
            device = EmfDevice(EmfContract.RELAY_NAME, "20:50:0D:2A:61:B2"),
            onConnect = {}
        )
    }
}
