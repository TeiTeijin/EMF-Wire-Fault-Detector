package com.beep.emfreader

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.beep.emfreader.data.ConnectFailure
import com.beep.emfreader.data.EmfContract
import com.beep.emfreader.data.EmfDevice
import com.beep.emfreader.data.EmfReadings
import com.beep.emfreader.ui.BluetoothAccessScreen
import com.beep.emfreader.ui.BluetoothDeniedScreen
import com.beep.emfreader.ui.ConnectionFailedScreen
import com.beep.emfreader.ui.EmfConnectingScreen
import com.beep.emfreader.ui.EmfDeviceRow
import com.beep.emfreader.ui.EmfLiveScreen
import com.beep.emfreader.ui.EmfScanScreen
import com.beep.emfreader.ui.EmfSegmentMeter
import com.beep.emfreader.ui.EmfSparkline
import com.beep.emfreader.ui.EmfTopBar
import com.beep.emfreader.ui.ScanUiState
import com.beep.emfreader.ui.SparklineHeight
import com.beep.emfreader.ui.theme.EMFReaderTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every app screen/state to a PNG with Roborazzi (JVM, no device).
 *
 *   .\gradlew.bat testDebugUnitTest -Proborazzi.test.record=true
 *
 * Images land in app/build/screenshots/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            EMFReaderTheme {
                content()
            }
        }
        val outDir = java.io.File(
            System.getProperty("screenshot.out.dir") ?: "build/screenshots"
        )
        outDir.mkdirs()
        compose.onRoot().captureRoboImage(java.io.File(outDir, "$name.png").absolutePath)
    }

    private val device = EmfDevice(EmfContract.RELAY_NAME, "20:50:0D:2A:61:B2")

    @Test
    fun bluetoothAccess() = shot("01-bluetooth-access") {
        BluetoothAccessScreen(onRequest = {})
    }

    @Test
    fun bluetoothDenied() = shot("02-bluetooth-denied") {
        BluetoothDeniedScreen(canRequestAgain = true, onRetry = {})
    }

    @Test
    fun scanLoading() = shot("03-scan-loading") {
        EmfScanScreen(state = ScanUiState.Loading, onRetry = {}, onDeviceSelected = {})
    }

    @Test
    fun scanFound() = shot("04-scan-found") {
        EmfScanScreen(
            state = ScanUiState.Found(
                listOf(
                    device,
                    EmfDevice("Workshop Relay", "24:6F:28:11:22:33")
                )
            ),
            onRetry = {},
            onDeviceSelected = {}
        )
    }

    @Test
    fun scanNotFound() = shot("05-scan-not-found") {
        EmfScanScreen(state = ScanUiState.NotFound, onRetry = {}, onDeviceSelected = {})
    }

    @Test
    fun deviceRow() = shot("06-device-row") {
        EmfDeviceRow(device = device, onConnect = {})
    }

    @Test
    fun connecting() = shot("07-connecting") {
        EmfConnectingScreen(deviceName = EmfContract.RELAY_NAME)
    }

    @Test
    fun liveClear() = shot("08-live-clear") {
        EmfLiveScreen(
            device = device,
            readings = EmfReadings(fieldPercent = 18, opampPercent = 27, jitterPercent = 34),
            traceValues = listOf(18, 20, 24, 22, 30, 31, 28, 27),
            peak = 31,
            onDisconnect = {}
        )
    }

    @Test
    fun liveFault() = shot("09-live-fault") {
        EmfLiveScreen(
            device = device,
            readings = EmfReadings(fieldPercent = 62, opampPercent = 88, jitterPercent = 71),
            traceValues = listOf(20, 24, 33, 48, 71, 88, 74, 51, 38, 29, 34, 62, 88, 71, 55, 44),
            peak = 88,
            reconnectSeconds = 3,
            onDisconnect = {}
        )
    }

    @Test
    fun connectionFailed() = shot("10-connection-failed") {
        ConnectionFailedScreen(
            device = device,
            reason = ConnectFailure.BUSY,
            onRetry = {},
            onBack = {}
        )
    }

    @Test
    fun segmentMeter() = shot("11-segment-meter") {
        EmfSegmentMeter(percent = 30)
    }

    @Test
    fun sparkline() = shot("12-sparkline") {
        EmfSparkline(
            values = listOf(18, 20, 24, 22, 30, 46, 71, 52, 34, 26, 22, 25, 31, 28, 24, 22),
            peakIndex = 6,
            modifier = Modifier.fillMaxWidth().height(SparklineHeight)
        )
    }

    @Test
    fun topBar() = shot("13-top-bar") {
        EmfTopBar()
    }
}
