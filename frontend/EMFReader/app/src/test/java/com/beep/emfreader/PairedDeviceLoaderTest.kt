package com.beep.emfreader

import com.beep.emfreader.data.PairedDeviceLoader
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class PairedDeviceLoaderTest {

    private val spp = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val a2dp = UUID.fromString("0000110d-0000-1000-8000-00805f9b34fb")
    private val hid = UUID.fromString("00001124-0000-1000-8000-00805f9b34fb")

    @Test
    fun acceptsTheRelayByItsBluetoothName() {
        assertTrue(PairedDeviceLoader.isSensor("EMF_Wire_Detector", emptyList()))
    }

    @Test
    fun acceptsAnyDeviceExposingTheSerialPortService() {
        assertTrue(PairedDeviceLoader.isSensor("Workshop Relay", listOf(spp)))
    }

    @Test
    fun acceptsTheRelayEvenIfItsUuidIsNotRecorded() {
        assertTrue(PairedDeviceLoader.isSensor("EMF_Wire_Detector", null))
    }

    @Test
    fun rejectsHeadphonesAndOtherAudioDevices() {
        assertFalse(PairedDeviceLoader.isSensor("TWS V5.4", listOf(a2dp)))
        assertFalse(PairedDeviceLoader.isSensor("BT-speaker", listOf(a2dp)))
        assertFalse(PairedDeviceLoader.isSensor("LILIAN-S", listOf(a2dp, hid)))
    }

    @Test
    fun rejectsUnknownDevicesWithNoUuidInformation() {
        assertFalse(PairedDeviceLoader.isSensor("Some Headset", emptyList()))
        assertFalse(PairedDeviceLoader.isSensor(null, null))
    }
}
