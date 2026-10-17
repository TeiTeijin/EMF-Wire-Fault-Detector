package com.beep.emfreader.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.beep.emfreader.R
import com.beep.emfreader.ui.theme.EMFReaderTheme

@Composable
fun BluetoothAccessScreen(
    onRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    EmfGateScreen(
        iconRes = R.drawable.access_required,
        title = stringResource(R.string.access_required_title),
        body = stringResource(R.string.access_required_body),
        modifier = modifier
    ) {
        EmfPrimaryButton(
            text = stringResource(R.string.access_required_cta),
            onClick = onRequest
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1B1B1A)
@Composable
private fun BluetoothAccessScreenPreview() {
    EMFReaderTheme {
        BluetoothAccessScreen(onRequest = {})
    }
}
