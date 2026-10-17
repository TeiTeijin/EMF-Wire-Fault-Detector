package com.beep.emfreader.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import java.util.UUID

object PairedDeviceLoader {

    @SuppressLint("MissingPermission")
    fun load(adapter: BluetoothAdapter?): List<EmfDevice> {
        val bonded = try {
            adapter?.bondedDevices.orEmpty()
        } catch (_: SecurityException) {
            return emptyList()
        }

        return bonded
            .filter { isSensor(it.name, it.uuids?.map { uuid -> uuid.uuid }) }
            .map { EmfDevice(name = it.name ?: it.address, address = it.address) }
            .sortedWith(SENSOR_FIRST)
    }

    fun isSensor(name: String?, uuids: Collection<UUID>?): Boolean =
        name == EmfSppContract.RELAY_NAME || uuids?.contains(EmfSppContract.SPP_UUID) == true

    private val SENSOR_FIRST = compareBy<EmfDevice> { it.name != EmfSppContract.RELAY_NAME }
        .thenBy { it.name.lowercase() }
        .thenBy { it.address }
}
