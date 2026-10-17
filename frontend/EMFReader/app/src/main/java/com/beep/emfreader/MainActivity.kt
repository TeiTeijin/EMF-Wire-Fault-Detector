package com.beep.emfreader

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.beep.emfreader.data.EmfDevice
import com.beep.emfreader.ui.BluetoothAccessScreen
import com.beep.emfreader.ui.BluetoothDeniedScreen
import com.beep.emfreader.ui.EmfScanHost
import com.beep.emfreader.ui.EmfSessionHost
import com.beep.emfreader.ui.requiredBluetoothPermissions
import com.beep.emfreader.ui.theme.EMFReaderTheme

private const val GATE_PREFS = "emf_permission_gate"
private const val KEY_HAS_REQUESTED = "has_requested"

class MainActivity : ComponentActivity() {
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            EMFReaderTheme {
                EmfAppRoot(resumeTick)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-evaluates permissions after the user returns from system Settings.
        resumeTick++
    }
}

@Composable
private fun EmfAppRoot(resumeTick: Int) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val permissions = requiredBluetoothPermissions()
    val prefs = remember { context.getSharedPreferences(GATE_PREFS, Context.MODE_PRIVATE) }

    // Persisted so the denied screen survives the process being killed while
    // the user is away in system Settings.
    var hasRequested by remember {
        mutableStateOf(prefs.getBoolean(KEY_HAS_REQUESTED, false))
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    val requestPermission: () -> Unit = {
        hasRequested = true
        prefs.edit().putBoolean(KEY_HAS_REQUESTED, true).apply()
        launcher.launch(permissions)
    }

    val granted = remember(resumeTick) {
        permissions.isNotEmpty() &&
            permissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
    }

    val canRequestAgain = remember(resumeTick) {
        activity != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            activity.shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)
    }

    // Survives rotation, so turning the phone does not drop an active session
    // back to the device list.
    val deviceSaver = remember {
        listSaver<EmfDevice?, String>(
            save = { device -> device?.let { listOf(it.name, it.address) } ?: emptyList() },
            restore = { saved ->
                if (saved.size == 2) EmfDevice(saved[0], saved[1]) else null
            }
        )
    }

    var device by rememberSaveable(stateSaver = deviceSaver) { mutableStateOf<EmfDevice?>(null) }

    val backToScan: () -> Unit = { device = null }

    val connected = device
    when {
        permissions.isEmpty() || granted -> {
            if (connected != null) {
                EmfSessionHost(
                    device = connected,
                    onDisconnect = backToScan,
                    onSessionLost = backToScan
                )
            } else {
                // The bond table changes while the user is away pairing in
                // system Settings, so the list reloads on every resume.
                EmfScanHost(onDeviceSelected = { device = it })
            }
        }
        !hasRequested -> BluetoothAccessScreen(onRequest = requestPermission)
        else -> BluetoothDeniedScreen(
            canRequestAgain = canRequestAgain,
            onRetry = requestPermission
        )
    }
}