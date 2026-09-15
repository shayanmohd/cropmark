package com.mohdshayan.cropmark.core

import com.mohdshayan.cropmark.core.crop.CropSolver
import com.mohdshayan.cropmark.core.crop.CrownFinder
import com.mohdshayan.cropmark.core.crop.FaceGeometry
import com.mohdshayan.cropmark.core.crop.Nudge
import com.mohdshayan.cropmark.core.spec.DocSpec
import com.mohdshayan.cropmark.core.spec.PrintSize
import com.mohdshayan.cropmark.core.spec.Range
import com.mohdshayan.cropmark.core.spec.SpecMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CropSolverTest {
    private val usPassport = DocSpec(
        id = "us-passport", name = "US passport", country = "United States", countryCode = "US",
        print = PrintSize(50.8f, 50.8f, "2 x 2 in"), headMm = Range(25.4f, 34.9f), eyeMm = Range(28.6f, 34.9f),
    )
    private val uk = DocSpec(
        id = "uk", name = "UK passport", country = "United Kingdom", countryCode = "GB",
        print = PrintSize(35f, 45f, "35 x 45 mm"), headMm = Range(29f, 34f),
    )

    private val face = FaceGeometry(
        faceCount = 1, imageWidth = 1536, imageHeight = 2048,
        chinY = 1300f, foreheadY = 800f, crownY = 700f, crownEstimated = false,
        leftEyeX = 680f, leftEyeY = 1000f, rightEyeX = 860f, rightEyeY = 1000f, midlineX = 770f,
        faceLeftX = 600f, faceRightX = 940f,
    )

    @Test fun headLandsMidRangeWithinOnePixel() {
        val crop = CropSolver.solve(face, usPassport)
        val outH = SpecMath.mmToPx(50.8f)
        val headOut = (face.chinY - face.crownY) / crop.height * outH
        val wantedOut = usPassport.headFraction.mid * outH
        assertEquals(wantedOut, headOut, 1f)
        assertEquals(1f, crop.width / crop.height, 1e-4f)
        assertTrue(crop.eyeFraction in usPassport.eyeFraction!!)
        assertEquals(0f, crop.centerOffset, 1e-4f)
    }

    @Test fun nudgesAreClampedToTheSpec() {
        val big = CropSolver.solve(face, usPassport, Nudge(scale = 3f, xMm = 40f, yMm = -80f))
        assertTrue("head stays under max", big.headFraction <= usPassport.headFraction.max)
        assertTrue("eyes stay in band", big.eyeFraction in usPassport.eyeFraction!!)
        assertTrue("centring stays within tolerance", abs(big.centerOffset) <= CropSolver.MAX_CENTER_SHIFT + 1e-4f)
        val small = CropSolver.solve(face, uk, Nudge(scale = 0.1f))
        assertTrue(small.headFraction >= uk.headFraction.min)
        assertTrue(small.top <= face.crownY)
    }

    @Test fun crownFromMatteAndFallback() {
        val w = 100
        val h = 200
        val matte = FloatArray(w * h) { i -> if (i / w >= 40) 1f else 0f }
        val measured = CrownFinder.find(matte, w, h, 50f, 80f, 150f, 20f)
        assertFalse(measured.estimated)
        assertEquals(40f, measured.y, 0.5f)

        val empty = FloatArray(w * h)
        val guessed = CrownFinder.find(empty, w, h, 50f, 80f, 150f, 20f)
        assertTrue(guessed.estimated)
        assertEquals(80f - 70f * CrownFinder.FOREHEAD_TO_CROWN, guessed.y, 0.01f)
        assertTrue(CrownFinder.find(null, w, h, 50f, 80f, 150f, 20f).estimated)
    }
}
