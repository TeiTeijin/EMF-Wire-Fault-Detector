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

/**
 * A live Bluetooth Classic (SPP) link to the ESP32 relay.
 *
 * The relay is a line source, not a notification stack: the Arduino pushes a
 * status line every 500 ms and the ESP32 forwards each one. So there is no
 * subscribe step and no command to send, just open the socket and read.
 *
 * Reads poll [InputStream.available] rather than blocking on a line reader.
 * A blocking read cannot be interrupted by cancellation, so the socket would
 * stay open until the process died; polling keeps teardown immediate and lets
 * the first-line timeout be honoured exactly.
 */
class SppDeviceSession(
    private val device: EmfDevice,
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
) : DeviceSession {

    /** Set as soon as the socket exists, so teardown can abort a connect in progress. */
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
                    // Expected: the sketch interleaves a raw-adc trace that
                    // carries no percentages. Logged briefly to make an
                    // unexpected sender diagnosable.
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
            // Whether the feed ended, timed out or was cancelled, the channel
            // and its reader thread go away with the flow.
            close()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Opens the RFCOMM channel, giving up after [CONNECT_TIMEOUT_MILLIS].
     *
     * `BluetoothSocket.connect` blocks inside native code and cannot be
     * cancelled, and an ESP32 that is powered off never answers at all, so the
     * call is bounded here and the socket closed from this thread on timeout,
     * which is what makes the blocked call return.
     */
    private fun openSocket(): BluetoothSocket? {
        val remote = try {
            adapter?.getRemoteDevice(device.address)
        } catch (_: IllegalArgumentException) {
            // Not a valid MAC, which happens when a stale device row outlives
            // a re-pair that produced a different address.
            Log.w(TAG, "not a valid address: ${device.address}")
            return null
        } ?: return null

        val candidate = try {
            @SuppressLint("MissingPermission") // gated by the BLUETOOTH_CONNECT grant
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
                    // Reported through the future's result, not by escaping.
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

        // connect() swallowed the IOException above, so success is whatever the
        // socket reports now: a failed channel is not connected.
        if (!candidate.isConnected) {
            Log.w(TAG, "RFCOMM channel to ${device.name} did not come up")
            closeQuietly(candidate)
            socket = null
            return null
        }

        return candidate
    }

    /**
     * Streams complete lines off [source] until it ends.
     *
     * The blocking read happens on its own thread because
     * `BluetoothSocket.getInputStream().available()` does not return 0 when the
     * peer is idle - it blocks - so polling it cannot implement a silence
     * timeout. Reading on a worker and handing finished lines over a channel
     * keeps the blocking call while leaving the timeout cancellable.
     *
     * Returns true when the peer stayed silent long enough to look broken
     * before it ever sent a line.
     */
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
                            // A sender that never terminates must not grow this
                            // forever.
                            if (pending.length >= EmfSppContract.MAX_LINE_LENGTH) pending.clear()
                            pending.append(char)
                        } else {
                            // UNLIMITED capacity, so this never blocks and
                            // never drops.
                            lines.trySend(pending.toString())
                            pending.setLength(0)
                        }
                    }
                }
            } catch (_: IOException) {
                // Socket closed underneath us, which is the normal way out.
            } finally {
                lines.close()
            }
        }.apply {
            isDaemon = true
            name = "emf-spp-reader"
            start()
        }

        // The first line is what proves the Arduino is actually talking. Waiting
        // on the channel keeps that deadline honest.
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
            // Closing a socket that is already gone is not a failure worth raising.
        }
    }

    private companion object {
        const val TAG = "EmfSppSession"

        /** A healthy RFCOMM connect is a second or two; anything past this is dead air. */
        const val CONNECT_TIMEOUT_MILLIS = 10_000L

        const val CHUNK_BYTES = 256
    }
}