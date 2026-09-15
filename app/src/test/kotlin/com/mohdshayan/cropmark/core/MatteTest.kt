package com.mohdshayan.cropmark.core

import com.mohdshayan.cropmark.core.matte.Decontaminate
import com.mohdshayan.cropmark.core.matte.GuidedFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatteTest {
    @Test fun guidedFilterGivesASteeperEdgeThanBilinear() {
        val w = 160
        val h = 8
        // A sharp luminance edge at x = 83 in the image, a coarse 16 px mask whose edge sits near it.
        val guide = FloatArray(w * h) { i -> if (i % w < 83) 0.85f else 0.15f }
        val mask = FloatArray(16) { x -> if (x < 8) 1f else 0f }
        val coarse = FloatArray(16 * 1) { mask[it] }
        val up = GuidedFilter.bilinearUpsample(coarse, 16, 1, w, h)
        val refined = GuidedFilter.filter(guide, up, w, h, 6, 1e-4f)
        fun maxStep(a: FloatArray): Float {
            var m = 0f
            for (x in 1 until w) m = maxOf(m, kotlin.math.abs(a[4 * w + x] - a[4 * w + x - 1]))
            return m
        }
        assertTrue("refined ${maxStep(refined)} vs bilinear ${maxStep(up)}", maxStep(refined) > 2f * maxStep(up))
        assertTrue(refined[4 * w + 40] > 0.9f)
        assertTrue(refined[4 * w + 120] < 0.1f)
    }

    @Test fun decontaminationRemovesTheOldBackgroundTint() {
        val w = 21
        val h = 21
        val red = 0xFFC03020.toInt()
        val grey = 0xFF505050.toInt()
        val alpha = FloatArray(w * h) { i -> when { i % w < 9 -> 1f; i % w > 11 -> 0f; else -> 0.5f } }
        // Edge pixels are half grey hair, half red wall.
        val mix = 0xFF000000.toInt() or (((0xC0 + 0x50) / 2) shl 16) or (((0x30 + 0x50) / 2) shl 8) or ((0x20 + 0x50) / 2)
        val argb = IntArray(w * h) { i -> when { i % w < 9 -> grey; i % w > 11 -> red; else -> mix } }
        val fg = Decontaminate.estimateForeground(argb, alpha, w, h, 4)
        val edge = fg[10 * w + 10]
        val r = (edge shr 16) and 0xFF
        val g = (edge shr 8) and 0xFF
        assertTrue("red fringe reduced: r=$r g=$g", r - g < 30)
    }

    @Test fun featherLevelsAreMonotonic() {
        val a = floatArrayOf(0.3f)
        val values = (0..4).map { Decontaminate.feather(a, it)[0] }
        assertEquals(0f, values[0], 1e-4f)
        for (i in 1..4) assertTrue(values[i] >= values[i - 1])
    }
}
