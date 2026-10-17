package com.beep.emfreader

import com.beep.emfreader.data.EmfAlertLevel
import com.beep.emfreader.data.EmfReadings
import org.junit.Assert.assertEquals
import org.junit.Test

class EmfReadingsTest {

    @Test
    fun `signal is the stronger of the two sensing paths`() {
        assertEquals(40, EmfReadings(fieldPercent = 40, opampPercent = 12).signalPercent)
        assertEquals(71, EmfReadings(fieldPercent = 12, opampPercent = 71).signalPercent)
        assertEquals(55, EmfReadings(fieldPercent = 55, opampPercent = 55).signalPercent)
    }

    @Test
    fun `signal below the low threshold reads as clear`() {
        val readings = EmfReadings(fieldPercent = 9, opampPercent = 12, jitterPercent = 80)
        assertEquals(EmfAlertLevel.CLEAR, readings.alertLevel)
    }

    @Test
    fun `signal at the low threshold is no longer clear`() {
        val readings = EmfReadings(fieldPercent = 15, opampPercent = 0, jitterPercent = 0)
        assertEquals(EmfAlertLevel.WIRE, readings.alertLevel)
    }

    @Test
    fun `jitter at the fault threshold is a fault`() {
        val readings = EmfReadings(fieldPercent = 30, opampPercent = 30, jitterPercent = 50)
        assertEquals(EmfAlertLevel.FAULT, readings.alertLevel)
    }

    @Test
    fun `jitter just under the fault threshold is only a wire`() {
        val readings = EmfReadings(fieldPercent = 30, opampPercent = 30, jitterPercent = 49)
        assertEquals(EmfAlertLevel.WIRE, readings.alertLevel)
    }

    @Test
    fun `clear wins over fault while the signal is below the low threshold`() {
        val readings = EmfReadings(fieldPercent = 0, opampPercent = 14, jitterPercent = 99)
        assertEquals(EmfAlertLevel.CLEAR, readings.alertLevel)
    }

    @Test
    fun `zero readings are clear`() {
        assertEquals(EmfAlertLevel.CLEAR, EmfReadings().alertLevel)
        assertEquals(0, EmfReadings().signalPercent)
    }
}
