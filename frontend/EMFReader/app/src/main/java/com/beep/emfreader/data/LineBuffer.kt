package com.beep.emfreader.data

/**
 * Reassembles a byte stream into newline-terminated lines.
 *
 * BLE GATT notifications can split a line across several chunks (or pack
 * several lines into one), so the transport feeds raw bytes here and this
 * class emits exactly one callback per complete line.
 *
 * - Splits on `\n`; a single trailing `\r` (CRLF) is stripped.
 * - A line that exceeds [maxLineLength] before its newline is dropped
 *   entirely (nothing is emitted and the overflow does not leak into the
 *   next line).
 * - Partial lines are held back until their newline arrives.
 */
class LineBuffer(private val maxLineLength: Int = EmfContract.MAX_LINE_LENGTH) {

    private val pending = StringBuilder()
    private var discarding = false

    fun feed(bytes: ByteArray, length: Int, onLine: (String) -> Unit) {
        for (i in 0 until length) {
            val char = bytes[i].toInt().toChar()
            if (char == EmfContract.LINE_TERMINATOR) {
                if (!discarding) {
                    val line = pending.toString()
                    val withoutCr = if (line.endsWith('\r')) line.dropLast(1) else line
                    onLine(withoutCr)
                }
                pending.setLength(0)
                discarding = false
                continue
            }

            if (discarding) continue

            if (char == '\r' && pending.isEmpty()) {
                // A stray CR before any content belongs to the next line.
                continue
            }

            if (pending.length >= maxLineLength) {
                pending.setLength(0)
                discarding = true
                continue
            }
            pending.append(char)
        }
    }
}
