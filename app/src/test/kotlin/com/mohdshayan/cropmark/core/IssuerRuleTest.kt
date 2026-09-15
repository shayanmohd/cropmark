package com.mohdshayan.cropmark.core

import com.mohdshayan.cropmark.core.check.CheckId
import com.mohdshayan.cropmark.core.check.CheckInput
import com.mohdshayan.cropmark.core.check.ComplianceChecker
import com.mohdshayan.cropmark.core.check.PhotoStats
import com.mohdshayan.cropmark.core.crop.CropSolver
import com.mohdshayan.cropmark.core.crop.CrownFinder
import com.mohdshayan.cropmark.core.crop.FaceGeometry
import com.mohdshayan.cropmark.core.crop.FrameGuides
import com.mohdshayan.cropmark.core.jpeg.JpegEncoder
import com.mohdshayan.cropmark.core.jpeg.SizeOutcome
import com.mohdshayan.cropmark.core.jpeg.SizeSearch
import com.mohdshayan.cropmark.core.spec.BackgroundKind
import com.mohdshayan.cropmark.core.spec.SpecCatalog
import com.mohdshayan.cropmark.data.db.PhotoEdit
import com.mohdshayan.cropmark.ui.editor.Pipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Rules that come from the issuers: the crown IRCC measures to, kept backgrounds, DS-160 compression, edit locks. */
class IssuerRuleTest {
    private val specs = SpecCatalog.parse(File("src/main/assets/specs/specs.json").readText())
    private fun spec(id: String) = specs.first { it.id == id }

    /** Hair piled well above the skull: the matte crown sits far above the forehead-proportion estimate. */
    private val bigHair = FaceGeometry(
        faceCount = 1, imageWidth = 1536, imageHeight = 2048,
        chinY = 1300f, foreheadY = 800f, crownY = 560f, crownEstimated = false,
        leftEyeX = 680f, leftEyeY = 1000f, rightEyeX = 860f, rightEyeY = 1000f, midlineX = 770f,
        faceLeftX = 600f, faceRightX = 940f,
    )

    @Test fun canadaMeasuresToTheSkullNotTheHair() {
        val ca = spec("ca-visa")
        assertTrue(ca.crownAtSkull)
        val skull = CrownFinder.estimate(bigHair.foreheadY, bigHair.chinY)
        assertEquals(skull, CropSolver.crownFor(bigHair, ca), 1e-3f)
        val crop = CropSolver.solve(bigHair, ca)
        // Chin to skull lands mid-range in millimetres, so the photo passes IRCC's own measurement.
        val skullMm = (bigHair.chinY - skull) * crop.mmPerPx
        assertEquals(33.5f, skullMm, 0.6f)
        // The same face under a hair-counting rule is scaled by the hair instead.
        val cn = spec("cn-visa")
        assertFalse(cn.crownAtSkull)
        assertEquals(bigHair.crownY, CropSolver.crownFor(bigHair, cn), 1e-3f)
        // The frame's crown bracket follows the same crown.
        val guides = FrameGuides.fromCrop(bigHair, crop, ca)
        assertEquals((skull - crop.top) / crop.height, guides.crown, 1e-4f)
    }

    @Test fun aSkullRuleNeverMovesTheCrownAboveTheMatte() {
        // Close-cropped hair: the matte crown is lower than the estimate, and it wins.
        val shaved = bigHair.copy(crownY = 720f)
        assertEquals(720f, CropSolver.crownFor(shaved, spec("ca-visa")), 1e-3f)
    }

    private val tight = bigHair.copy(crownY = 700f, imageWidth = 900, imageHeight = 1400)
    private val kept = PhotoStats(0.55f, 0.56f, 0.52f, backgroundStd = 0.02f, backgroundMean = 0.9f, backgroundTexture = 0.01f)

    @Test fun aKeptBackgroundThatRunsOutFailsTheBackgroundCheck() {
        val us = spec("us-passport")
        val crop = CropSolver.solve(tight, us)
        assertTrue("the 2 x 2 in crop needs more photo than a tight selfie has", CropSolver.overhangs(crop, tight.imageWidth, tight.imageHeight))
        val r = ComplianceChecker.run(CheckInput(us, tight, crop, kept, 600, keptBackgroundOverhangs = true)).associateBy { it.id }
        assertFalse(r.getValue(CheckId.Background).passed)
        assertEquals(ComplianceChecker.OVERHANG, r.getValue(CheckId.Background).measured)
        assertEquals(12, r.size)
        val fine = ComplianceChecker.run(CheckInput(us, tight, crop, kept, 600)).associateBy { it.id }
        assertTrue(fine.getValue(CheckId.Background).passed)
    }

    @Test fun aPhotoWithRoomAroundTheHeadDoesNotOverhang() {
        val roomy = tight.copy(imageWidth = 3000, imageHeight = 3000)
        assertFalse(CropSolver.overhangs(CropSolver.solve(roomy, spec("us-passport")), roomy.imageWidth, roomy.imageHeight))
    }

    /** Bytes grow with pixels and quality; a smooth portrait squeezes hard at high quality. */
    private val smooth = JpegEncoder { w, h, q -> ByteArray((w.toLong() * h * 3 * (q / 100.0) / 19.0 * (600.0 / w)).toInt()) }

    @Test fun ds160AtFullWidthStepsDownUntilTheRatioHolds() {
        val ds = spec("us-ds160").digital!!
        val out = SizeSearch.search(
            1200, ds.minPx, ds.maxPx, { it }, null, SizeSearch.maxBytes(ds.maxKb), smooth,
            maxQuality = 100, maxCompression = ds.maxCompression,
        ) as SizeOutcome.Fits
        assertTrue("ratio within 20:1 at ${out.widthPx} px, ${out.bytes.size} bytes", SizeSearch.withinCompression(out, 20))
        assertTrue(out.widthPx < 1200)
        assertTrue(out.bytes.size <= 240_000)
    }

    @Test fun whenNoWidthMeetsTheRatioTheRequestedWidthStands() {
        val flat = JpegEncoder { w, h, q -> ByteArray((w * h * q / 100.0 / 40.0).toInt()) }
        val out = SizeSearch.search(1200, 600, 1200, { it }, null, 240_000, flat, maxQuality = 100, maxCompression = 20) as SizeOutcome.Fits
        assertEquals(1200, out.widthPx)
        assertFalse(SizeSearch.withinCompression(out, 20))
    }

    @Test fun ratioBoundaryIsInclusive() {
        val f = SizeOutcome.Fits(600, 600, 100, ByteArray(54_000))
        assertTrue(SizeSearch.withinCompression(f, 20))
        assertFalse(SizeSearch.withinCompression(f.copy(bytes = ByteArray(53_999)), 20))
    }

    @Test fun lockedSpecsKeepThePhotoAsShot() {
        val edited = PhotoEdit(1, "us-passport", BackgroundKind.LightBlue.key, BackgroundKind.LightBlue.argb, 3, 0.7f, 1.1f, 0.5f, -0.5f)
        for (id in listOf("us-passport", "us-ds160", "in-passport", "in-oci", "ca-visa")) {
            val locked = Pipeline.forSpec(edited, spec(id))
            assertEquals(id, BackgroundKind.Keep.key, locked.backgroundMode)
            assertEquals(id, 0f, locked.exposureEv)
            // The crop is still the user's to adjust.
            assertEquals(1.1f, locked.nudgeScale)
        }
        for (id in listOf("in-evisa", "in-pan", "cn-visa")) {
            assertEquals(id, edited, Pipeline.forSpec(edited, spec(id)))
        }
    }

    @Test fun facingCameraReportsTheAxisNearestItsLimit() {
        val us = spec("us-passport")
        fun facing(yaw: Float, pitch: Float) = ComplianceChecker.run(
            CheckInput(us, bigHair.copy(yawDeg = yaw, pitchDeg = pitch), CropSolver.solve(bigHair, us), null, 600),
        ).first { it.id == CheckId.FacingCamera }
        // 8.7 degrees of tip is inside the 12 degree pitch limit: it passes and says so.
        facing(3f, -8.7f).let { assertTrue(it.passed); assertTrue(it.pitchAxis); assertEquals(8.7f, it.measured, 1e-3f) }
        // 7 degrees sideways is nearer its 8 degree limit than 9 of tip is to 12.
        facing(7f, 9f).let { assertTrue(it.passed); assertFalse(it.pitchAxis); assertEquals(7f, it.measured, 1e-3f) }
        facing(-8f, 0f).let { assertFalse(it.passed); assertFalse(it.pitchAxis) }
    }

    @Test fun everyLiveFrameLeavesRoomForTheRuler() {
        for (s in specs) for (view in listOf(3f / 4f, 4f / 3f)) {
            val f = com.mohdshayan.cropmark.core.check.LiveGuide.frameFor(s, view)
            // The ruler needs at least 18dp beside a frame in a viewfinder about 360dp wide.
            assertTrue("${s.id} at $view: right margin ${1f - f.right}", 1f - f.right >= 0.099f)
            assertEquals("${s.id} keeps its aspect", s.aspect, f.width * view / f.height, 1e-3f)
            assertTrue(f.top >= 0f && f.bottom <= 1f)
        }
    }
}
