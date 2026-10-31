package com.beep.emfreader

import com.beep.emfreader.data.LineBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LineBufferTest {

    private fun feed(buffer: LineBuffer, chunks: List<String>): List<String> {
        val lines = mutableListOf<String>()
        for (chunk in chunks) {
            val bytes = chunk.toByteArray()
            buffer.feed(bytes, bytes.size) { lines.add(it) }
        }
        return lines
    }

    @Test
    fun emitsASingleChunkSingleLine() {
        val lines = feed(LineBuffer(), listOf("CLEAR,0,0,0,0\n"))

        assertEquals(listOf("CLEAR,0,0,0,0"), lines)
    }

    @Test
    fun reassemblesALineSplitAcrossThreeChunks() {
        val lines = feed(LineBuffer(), listOf("WIR", "E,44", ",12,44,20\n"))

        assertEquals(listOf("WIRE,44,12,44,20"), lines)
    }

    @Test
    fun emitsTwoLinesArrivingInOneChunk() {
        val lines = feed(LineBuffer(), listOf("CLEAR,0,0,0,0\nWIRE,44,0,44,20\n"))

        assertEquals(listOf("CLEAR,0,0,0,0", "WIRE,44,0,44,20"), lines)
    }

    @Test
    fun toleratesCarriageReturnBeforeTheNewline() {
        val lines = feed(LineBuffer(), listOf("WIRE,44,12,44,20\r\n"))

        assertEquals(listOf("WIRE,44,12,44,20"), lines)
    }

    @Test
    fun dropsAnOverlongLineWithoutEmittingAnything() {
        val chunk = StringBuilder()
        repeat(161) { chunk.append('x') }
        chunk.append("\nCLEAR,0,0,0,0\n")

        val lines = feed(LineBuffer(), listOf(chunk.toString()))

        assertEquals(listOf("CLEAR,0,0,0,0"), lines)
    }

    @Test
    fun doesNotEmitAPartialLineUntilItsNewlineArrives() {
        val lines = feed(LineBuffer(), listOf("part", "ial", " line", "\n"))

        assertEquals(listOf("partial line"), lines)
    }

    @Test
    fun emitsALineAtExactlyTheMaximumLength() {
        val chunk = StringBuilder()
        repeat(160) { chunk.append('y') }
        chunk.append("\n")

        val lines = feed(LineBuffer(maxLineLength = 160), listOf(chunk.toString()))

        assertEquals(1, lines.size)
        assertEquals(160, lines[0].length)
        assertTrue(lines[0].all { it == 'y' })
    }

    @Test
    fun anOverlongLineDoesNotPoisonTheFollowingLine() {
        val chunk = StringBuilder()
        repeat(200) { chunk.append('z') }
        chunk.append("\n")
        val lines = feed(LineBuffer(maxLineLength = 16), listOf(chunk.toString(), "OK,1\n"))

        assertEquals(listOf("OK,1"), lines)
    }
}
