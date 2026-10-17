package com.beep.emfreader.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.beep.emfreader.R
import com.beep.emfreader.ui.theme.EMFReaderTheme
import com.beep.emfreader.ui.theme.EmfTextFaint

@Composable
fun BluetoothDeniedScreen(
    canRequestAgain: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val openSettings = {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
        )
    }

    EmfGateScreen(
        iconRes = R.drawable.permission_denied,
        title = stringResource(R.string.permission_denied_title),
        body = stringResource(R.string.permission_denied_body),
        modifier = modifier
    ) {
        EmfPrimaryButton(
            text = stringResource(R.string.permission_denied_cta_settings),
            onClick = openSettings
        )

        if (canRequestAgain) {
            Spacer(Modifier.height(8.dp))
            EmfSecondaryButton(
                text = stringResource(R.string.permission_denied_cta_retry),
                onClick = onRetry
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(
                if (canRequestAgain) {
                    R.string.permission_denied_hint
                } else {
                    R.string.permission_denied_hint_blocked
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = EmfTextFaint,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1B1B1A)
@Composable
private fun BluetoothDeniedScreenPreview() {
    EMFReaderTheme {
        BluetoothDeniedScreen(canRequestAgain = true, onRetry = {})
    }
}
