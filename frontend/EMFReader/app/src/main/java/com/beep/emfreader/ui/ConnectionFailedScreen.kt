package com.beep.emfreader.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.beep.emfreader.R
import com.beep.emfreader.data.ConnectFailure
import com.beep.emfreader.data.EmfDevice
import com.beep.emfreader.data.EmfSppContract
import com.beep.emfreader.ui.theme.EMFReaderTheme

val ConnectionFailedIconSize = 32.dp
val ConnectionButtonGap = 8.dp

private val ConnectFailure.bodyRes: Int
    @StringRes get() = when (this) {
        ConnectFailure.REFUSED -> R.string.connect_reason_refused
        ConnectFailure.BUSY -> R.string.connect_reason_busy
        ConnectFailure.OUT_OF_RANGE -> R.string.connect_reason_out_of_range
        ConnectFailure.DISAPPEARED -> R.string.connect_reason_disappeared
        ConnectFailure.SILENT -> R.string.connect_reason_silent
    }

@Composable
fun ConnectionFailedScreen(
    device: EmfDevice,
    reason: ConnectFailure,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    EmfGateScreen(
        iconRes = R.drawable.connection_failed,
        title = stringResource(R.string.connect_failed_title),
        body = stringResource(reason.bodyRes, device.name),
        iconWidth = ConnectionFailedIconSize,
        iconHeight = ConnectionFailedIconSize,
        modifier = modifier
    ) {
        EmfPrimaryButton(
            text = stringResource(R.string.connect_cta_retry),
            onClick = onRetry
        )

        Spacer(Modifier.height(ConnectionButtonGap))

        EmfSecondaryButton(
            text = stringResource(R.string.connect_cta_back),
            onClick = onBack
        )
    }
}

private val previewDevice = EmfDevice(
    name = EmfSppContract.RELAY_NAME,
    address = "20:50:0D:2A:61:B2"
)

@Preview(showBackground = true, backgroundColor = 0xFF1B1B1A)
@Composable
private fun ConnectionFailedScreenPreview() {
    EMFReaderTheme {
        ConnectionFailedScreen(
            device = previewDevice,
            reason = ConnectFailure.BUSY,
            onRetry = {},
            onBack = {}
        )
    }
}
