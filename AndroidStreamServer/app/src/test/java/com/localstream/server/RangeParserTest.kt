package com.localstream.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

data class ParsedRange(
    val start: Long,
    val end: Long,
    val contentLength: Long,
    val isValid: Boolean
)

fun parseHttpRange(rangeHeader: String, totalLength: Long): ParsedRange {
    if (!rangeHeader.startsWith("bytes=")) {
        return ParsedRange(0, totalLength - 1, totalLength, true)
    }

    val spec = rangeHeader.removePrefix("bytes=").trim()
    val parts = spec.split("-")
    var start: Long = 0
    var end: Long = totalLength - 1

    try {
        if (parts[0].isNotEmpty()) {
            start = parts[0].toLong()
            if (parts.size > 1 && parts[1].isNotEmpty()) {
                end = parts[1].toLong()
            }
        } else if (parts.size > 1 && parts[1].isNotEmpty()) {
            val suffix = parts[1].toLong()
            start = (totalLength - suffix).coerceAtLeast(0)
            end = totalLength - 1
        }
    } catch (e: Exception) {
        return ParsedRange(0, 0, 0, false)
    }

    if (start >= totalLength || start > end) {
        return ParsedRange(start, end, 0, false)
    }

    end = end.coerceAtMost(totalLength - 1)
    val length = end - start + 1
    return ParsedRange(start, end, length, true)
}

class RangeParserTest {

    @Test
    fun testStandardRangeRequest() {
        val total = 10000L
        val r = parseHttpRange("bytes=0-499", total)
        assertTrue(r.isValid)
        assertEquals(0L, r.start)
        assertEquals(499L, r.end)
        assertEquals(500L, r.contentLength)
    }

    @Test
    fun testOpenEndedRangeRequest() {
        val total = 10000L
        val r = parseHttpRange("bytes=2048-", total)
        assertTrue(r.isValid)
        assertEquals(2048L, r.start)
        assertEquals(9999L, r.end)
        assertEquals(7952L, r.contentLength)
    }

    @Test
    fun testSuffixRangeRequest() {
        val total = 10000L
        val r = parseHttpRange("bytes=-500", total)
        assertTrue(r.isValid)
        assertEquals(9500L, r.start)
        assertEquals(9999L, r.end)
        assertEquals(500L, r.contentLength)
    }

    @Test
    fun testOutOfBoundsRangeRequest() {
        val total = 10000L
        val r = parseHttpRange("bytes=12000-15000", total)
        assertFalse(r.isValid)
    }
}
