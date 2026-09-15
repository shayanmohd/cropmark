package com.mohdshayan.cropmark.core.jpeg

import java.io.ByteArrayOutputStream

/**
 * Rewrites a JPEG's header: one JFIF APP0 segment carrying the print density (so a kiosk prints
 * 35 mm as 35 mm) and no APP1 segments, which is where EXIF and XMP metadata such as location live.
 * The compressed image data is copied byte for byte.
 */
object JfifDensity {

    data class Segment(val marker: Int, val offset: Int, val length: Int)

    fun withDensity(jpeg: ByteArray, dpi: Int): ByteArray {
        require(isJpeg(jpeg)) { "Not a JPEG" }
        val out = ByteArrayOutputStream(jpeg.size + 18)
        out.write(0xFF); out.write(0xD8)
        out.write(app0(dpi))
        var i = 2
        while (i + 4 <= jpeg.size) {
            if (jpeg[i].toInt() and 0xFF != 0xFF) break
            val marker = jpeg[i + 1].toInt() and 0xFF
            if (marker == 0xDA) {
                out.write(jpeg, i, jpeg.size - i)
                return out.toByteArray()
            }
            val len = ((jpeg[i + 2].toInt() and 0xFF) shl 8) or (jpeg[i + 3].toInt() and 0xFF)
            val drop = marker == 0xE1 || (marker == 0xE0 && isJfif(jpeg, i + 4))
            // A length that runs past the end is a truncated file: copy what is there and stop.
            if (!drop) out.write(jpeg, i, minOf(len + 2, jpeg.size - i))
            i += len + 2
        }
        if (i < jpeg.size) out.write(jpeg, i, jpeg.size - i)
        return out.toByteArray()
    }

    fun readDpi(jpeg: ByteArray): Int? {
        if (!isJpeg(jpeg)) return null
        val seg = segments(jpeg).firstOrNull { it.marker == 0xE0 && isJfif(jpeg, it.offset + 4) } ?: return null
        val base = seg.offset + 4
        val units = jpeg[base + 7].toInt() and 0xFF
        val x = ((jpeg[base + 8].toInt() and 0xFF) shl 8) or (jpeg[base + 9].toInt() and 0xFF)
        return when (units) {
            1 -> x
            2 -> Math.round(x * 2.54f)
            else -> null
        }
    }

    /** Header segments up to and including start of scan. */
    fun segments(jpeg: ByteArray): List<Segment> {
        val list = mutableListOf<Segment>()
        var i = 2
        while (i + 4 <= jpeg.size && (jpeg[i].toInt() and 0xFF) == 0xFF) {
            val marker = jpeg[i + 1].toInt() and 0xFF
            val len = ((jpeg[i + 2].toInt() and 0xFF) shl 8) or (jpeg[i + 3].toInt() and 0xFF)
            list += Segment(marker, i, len)
            if (marker == 0xDA) break
            i += len + 2
        }
        return list
    }

    fun isJpeg(b: ByteArray): Boolean = b.size > 4 && (b[0].toInt() and 0xFF) == 0xFF && (b[1].toInt() and 0xFF) == 0xD8

    private fun isJfif(b: ByteArray, at: Int): Boolean =
        at + 5 <= b.size && b[at] == 'J'.code.toByte() && b[at + 1] == 'F'.code.toByte() &&
            b[at + 2] == 'I'.code.toByte() && b[at + 3] == 'F'.code.toByte() && b[at + 4] == 0.toByte()

    private fun app0(dpi: Int): ByteArray {
        val d = dpi.coerceIn(1, 65535)
        return byteArrayOf(
            0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10,
            'J'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 0x00,
            0x01, 0x02, 0x01,
            (d shr 8).toByte(), d.toByte(), (d shr 8).toByte(), d.toByte(),
            0x00, 0x00,
        )
    }
}
