package com.mohdshayan.cropmark.core

import com.mohdshayan.cropmark.core.jpeg.JfifDensity
import com.mohdshayan.cropmark.core.jpeg.JpegEncoder
import com.mohdshayan.cropmark.core.jpeg.SizeOutcome
import com.mohdshayan.cropmark.core.jpeg.SizeSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class JpegTest {
    /** Size grows with pixels and quality, roughly like a real portrait JPEG. */
    private fun stub(bytesPerPixelAt95: Double) = JpegEncoder { w, h, q ->
        val f = 0.08 + 0.92 * ((q - 30) / 65.0).let { it * it }
        ByteArray((w * h * bytesPerPixelAt95 * f).toInt())
    }

    @Test fun ds160LandsJustUnderTheCap() {
        val out = SizeSearch.search(600, 600, 1200, { it }, null, SizeSearch.maxBytes(240), stub(1.2))
        out as SizeOutcome.Fits
        assertEquals(600, out.widthPx)
        assertTrue("under 240 KB: ${out.bytes.size}", out.bytes.size <= 240_000)
        assertTrue("not needlessly small: ${out.bytes.size}", out.bytes.size >= 200_000)
    }

    @Test fun chinaMinimumRaisesPixelsFirst() {
        val calls = mutableListOf<Int>()
        val enc = JpegEncoder { w, h, q -> calls += w; ByteArray((w * h * 0.12 * (q / 95.0)).toInt()) }
        val out = SizeSearch.search(354, 354, 420, { w -> w * 472 / 354 }, SizeSearch.minBytes(40), SizeSearch.maxBytes(120), enc)
        // 354 x 472 at q95 is about 20 KB, so the width climbs before giving up.
        assertTrue(calls.any { it > 354 })
        assertTrue(out is SizeOutcome.TooSmall)
        val fits = SizeSearch.search(354, 354, 420, { w -> w * 472 / 354 }, SizeSearch.minBytes(40), SizeSearch.maxBytes(120), stub(0.5))
        fits as SizeOutcome.Fits
        assertTrue(fits.bytes.size in 40 * 1024..120_000)
    }

    @Test fun impossibleCapIsReported() {
        val out = SizeSearch.search(600, 600, 600, { it }, null, SizeSearch.maxBytes(20), stub(3.0))
        assertTrue(out is SizeOutcome.TooLarge)
        assertEquals(600, (out as SizeOutcome.TooLarge).widthPx)
    }

    @Test fun densityIsWrittenExifIsStrippedAndTheFileStillDecodes() {
        // A 16 x 12 baseline JPEG written by libjpeg, embedded so the test needs no image library.
        val raw = Base64.getDecoder().decode(TINY_JPEG)
        // Splice a fake EXIF APP1 segment right after SOI.
        val exif = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), 0x00, 0x08, 'E'.code.toByte(), 'x'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), 0, 0)
        val withExif = raw.copyOfRange(0, 2) + exif + raw.copyOfRange(2, raw.size)
        assertTrue(JfifDensity.segments(withExif).any { it.marker == 0xE1 })

        val fixed = JfifDensity.withDensity(withExif, 300)
        assertEquals(300, JfifDensity.readDpi(fixed))
        assertFalse(JfifDensity.segments(fixed).any { it.marker == 0xE1 })
        assertEquals(1, JfifDensity.segments(fixed).count { it.marker == 0xE0 })
        // The compressed data survives byte for byte: same scan, same end of image.
        val scanAt = JfifDensity.segments(fixed).last().offset
        val rawScan = JfifDensity.segments(raw).last().offset
        assertEquals(0xDA, JfifDensity.segments(fixed).last().marker)
        assertTrue(fixed.copyOfRange(scanAt, fixed.size).contentEquals(raw.copyOfRange(rawScan, raw.size)))
        assertEquals(0xD9, fixed.last().toInt() and 0xFF)
    }

    @Test fun compressionCappedFormsEncodeAtFullQuality() {
        val qualities = mutableListOf<Int>()
        val enc = JpegEncoder { w, h, q -> qualities += q; ByteArray((w * h * 0.1 * q / 100).toInt()) }
        val out = SizeSearch.search(600, 600, 1200, { it }, null, SizeSearch.maxBytes(240), enc, maxQuality = 100) as SizeOutcome.Fits
        assertEquals(100, out.quality)
        // A cap still wins over quality.
        val capped = SizeSearch.search(600, 600, 600, { it }, null, SizeSearch.maxBytes(20), enc, maxQuality = 100) as SizeOutcome.Fits
        assertTrue(capped.quality < 100 && capped.bytes.size <= 20_000)
    }

    @Test fun densityHeaderCarriesTheSpecDpi() {
        val raw = Base64.getDecoder().decode(TINY_JPEG)
        assertEquals(200, JfifDensity.readDpi(JfifDensity.withDensity(raw, 200)))
        assertEquals(600, JfifDensity.readDpi(JfifDensity.withDensity(raw, 600)))
    }

    private companion object {
        const val TINY_JPEG = "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAYEBQYFBAYGBQYHBwYIChAKCgkJChQODwwQFxQYGBcUFhYaHSUfGhsjHBYWICwgIyYnKSopGR8tMC0oMCUoKSj/2wBDAQcHBwoIChMKChMoGhYaKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCj/wAARCAAMABADASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwDMooor50/Rz//Z"
    }
}
