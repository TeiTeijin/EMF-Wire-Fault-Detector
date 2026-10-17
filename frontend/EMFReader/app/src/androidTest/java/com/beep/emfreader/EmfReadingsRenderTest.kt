package com.beep.emfreader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.beep.emfreader.data.EmfAlertLevel
import com.beep.emfreader.data.EmfDevice
import com.beep.emfreader.data.EmfSppContract
import com.beep.emfreader.data.ReadingHistory
import com.beep.emfreader.ui.EmfLiveScreen
import com.beep.emfreader.ui.theme.EMFReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmfReadingsRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private val device = EmfDevice(EmfSppContract.RELAY_NAME, "20:50:0D:2A:61:B2")

    private fun readingsFrom(line: String) =
        requireNotNull(EmfSppContract.parseLine(line))

    @Test
    fun sensorLineDecodesAndRendersTheThreeChannels() {
        val readings = readingsFrom("WIRE,44,12,44,20")

        compose.setContent {
            EMFReaderTheme {
                EmfLiveScreen(
                    device = device,
                    readings = readings,
                    onDisconnect = {}
                )
            }
        }

        compose.onNodeWithText("12").assertIsDisplayed()
        compose.onNodeWithText("44").assertIsDisplayed()
        compose.onNodeWithText("20").assertIsDisplayed()

        compose.onNodeWithText("Field").assertIsDisplayed()
        compose.onNodeWithText("Op-Amp").assertIsDisplayed()
        compose.onNodeWithText("Jitter").assertIsDisplayed()
    }

    @Test
    fun aQuietRoomRendersClear() {
        val readings = readingsFrom("CLEAR,8,8,3,2")
        assertEquals(EmfAlertLevel.CLEAR, readings.alertLevel)

        compose.setContent {
            EMFReaderTheme {
                EmfLiveScreen(device = device, readings = readings, onDisconnect = {})
            }
        }

        compose.onNodeWithText("Clear").assertIsDisplayed()
    }

    @Test
    fun aSteadySignalRendersWireDetected() {
        val readings = readingsFrom("WIRE,44,12,44,20")
        assertEquals(EmfAlertLevel.WIRE, readings.alertLevel)

        compose.setContent {
            EMFReaderTheme {
                EmfLiveScreen(device = device, readings = readings, onDisconnect = {})
            }
        }

        compose.onNodeWithText("Wire Detected").assertIsDisplayed()
        compose.onNodeWithText("POSSIBLE FAULT").assertDoesNotExist()
    }

    @Test
    fun anErraticSignalRendersPossibleFault() {
        val readings = readingsFrom("FAULT,88,70,88,71")
        assertEquals(EmfAlertLevel.FAULT, readings.alertLevel)

        compose.setContent {
            EMFReaderTheme {
                EmfLiveScreen(device = device, readings = readings, onDisconnect = {})
            }
        }

        compose.onNodeWithText("POSSIBLE FAULT").assertIsDisplayed()
    }

    @Test
    fun peakTracksTheStrongestReadingInTheWindow() {
        val history = ReadingHistory()
        listOf(
            readingsFrom("WIRE,12,10,12,8"),
            readingsFrom("WIRE,51,44,51,30"),
            readingsFrom("WIRE,20,18,20,9")
        ).forEach { history.add(it) }

        val trace = history.snapshot().map { it.signalPercent }
        val peak = history.peakSignal()

        assertEquals(listOf(12, 51, 20), trace)
        assertEquals(51, peak)

        compose.setContent {
            EMFReaderTheme {
                EmfLiveScreen(
                    device = device,
                    readings = readingsFrom("WIRE,20,18,20,9"),
                    traceValues = trace,
                    peak = peak,
                    onDisconnect = {}
                )
            }
        }

        compose.onNodeWithText("Peak").assertIsDisplayed()
        compose.onNodeWithText("51").assertIsDisplayed()
    }

    @Test
    fun theDisconnectControlIsOnScreen() {
        compose.setContent {
            EMFReaderTheme {
                EmfLiveScreen(
                    device = device,
                    readings = readingsFrom("CLEAR,8,8,3,2"),
                    onDisconnect = {}
                )
            }
        }

        compose.onNodeWithText("Disconnect").assertIsDisplayed()
    }
}
