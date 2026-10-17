package com.beep.emfreader.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class SppDeviceSession(
    private val device: EmfDevice,
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
) : DeviceSession {

    @Volatile
    private var socket: BluetoothSocket? = null

    override fun events(): Flow<SessionEvent> = channelFlow {
        try {
            if (adapter == null) {
                send(SessionEvent.Failed(ConnectFailure.OUT_OF_RANGE))
                return@channelFlow
            }

            val opened = openSocket()
            if (opened == null) {
                send(SessionEvent.Failed(ConnectFailure.REFUSED))
                return@channelFlow
            }

            Log.i(TAG, "connected to ${device.name} (${device.address})")
            send(SessionEvent.Connected)

            var lines = 0
            var dropped = 0

            val silent = readLines(opened) { line ->
                val readings = EmfSppContract.parseLine(line)
                if (readings == null) {
                    if (dropped < 2) Log.d(TAG, "skipped: '$line'")
                    dropped++
                } else {
                    if (lines == 0) Log.i(TAG, "first line: $line")
                    lines++
                    send(SessionEvent.Reading(readings))
                }
            }

            Log.i(
                TAG,
                "stream ended for ${device.name}: lines=$lines dropped=$dropped silent=$silent"
            )
            send(if (silent) SessionEvent.Failed(ConnectFailure.SILENT) else SessionEvent.LinkLost)
        } finally {
            close()
        }
    }.flowOn(Dispatchers.IO)

    private fun openSocket(): BluetoothSocket? {
        val remote = try {
            adapter?.getRemoteDevice(device.address)
        } catch (_: IllegalArgumentException) {
            Log.w(TAG, "not a valid address: ${device.address}")
            return null
        } ?: return null

        val candidate = try {
            @SuppressLint("MissingPermission")
            remote.createRfcommSocketToServiceRecord(EmfSppContract.SPP_UUID)
        } catch (_: IOException) {
            Log.w(TAG, "could not create an RFCOMM socket for ${device.name}")
            return null
        }

        socket = candidate

        val worker = Executors.newSingleThreadExecutor()
        val startedAt = System.currentTimeMillis()
        try {
            val attempt = worker.submit {
                try {
                    @SuppressLint("MissingPermission")
                    candidate.connect()
                } catch (_: IOException) {
                }
            }

            attempt.get(CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            Log.w(
                TAG,
                "no answer from ${device.name} after " +
                    "${System.currentTimeMillis() - startedAt}ms; is it powered on?"
            )
            closeQuietly(candidate)
            worker.shutdownNow()
            socket = null
            return null
        } catch (_: Exception) {
            worker.shutdownNow()
            closeQuietly(candidate)
            socket = null
            return null
        } finally {
            worker.shutdown()
        }

        if (!candidate.isConnected) {
            Log.w(TAG, "RFCOMM channel to ${device.name} did not come up")
            closeQuietly(candidate)
            socket = null
            return null
        }

        return candidate
    }

    private suspend fun readLines(
        source: BluetoothSocket,
        onLine: suspend (String) -> Unit
    ): Boolean {
        val lines = Channel<String>(Channel.UNLIMITED)

        val reader = Thread {
            val chunk = ByteArray(CHUNK_BYTES)
            val pending = StringBuilder()
            try {
                val input = source.inputStream
                while (true) {
                    val read = input.read(chunk)
                    if (read < 0) break

                    for (i in 0 until read) {
                        val char = chunk[i].toInt().toChar()
                        if (char != EmfSppContract.LINE_TERMINATOR) {
                            if (pending.length >= EmfSppContract.MAX_LINE_LENGTH) pending.clear()
                            pending.append(char)
                        } else {
                            lines.trySend(pending.toString())
                            pending.setLength(0)
                        }
                    }
                }
            } catch (_: IOException) {
            } finally {
                lines.close()
            }
        }.apply {
            isDaemon = true
            name = "emf-spp-reader"
            start()
        }

        val first = withTimeoutOrNull(EmfSppContract.FIRST_LINE_TIMEOUT_MILLIS) {
            lines.receiveCatching()
        } ?: run {
            Log.w(TAG, "${device.name} never sent a line; Arduino may be unpowered")
            return true
        }

        if (first.isClosed) return false
        onLine(first.getOrThrow())

        for (line in lines) onLine(line)
        return false
    }

    override fun close() {
        closeQuietly(socket)
        socket = null
    }

    private fun closeQuietly(target: BluetoothSocket?) {
        try {
            target?.close()
        } catch (_: IOException) {
        }
    }

    private companion object {
        const val TAG = "EmfSppSession"

        const val CONNECT_TIMEOUT_MILLIS = 10_000L

        const val CHUNK_BYTES = 256
    }
}
