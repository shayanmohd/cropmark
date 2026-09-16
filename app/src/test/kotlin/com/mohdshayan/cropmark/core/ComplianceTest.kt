package com.mohdshayan.cropmark.core

import com.mohdshayan.cropmark.core.check.CheckId
import com.mohdshayan.cropmark.core.check.CheckInput
import com.mohdshayan.cropmark.core.check.ComplianceChecker
import com.mohdshayan.cropmark.core.check.FrameRect
import com.mohdshayan.cropmark.core.check.LiveFace
import com.mohdshayan.cropmark.core.check.LiveGuide
import com.mohdshayan.cropmark.core.check.LiveHint
import com.mohdshayan.cropmark.core.check.PhotoStats
import com.mohdshayan.cropmark.core.crop.CropSolver
import com.mohdshayan.cropmark.core.crop.FaceGeometry
import com.mohdshayan.cropmark.core.spec.DocSpec
import com.mohdshayan.cropmark.core.spec.PrintSize
import com.mohdshayan.cropmark.core.spec.Range
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComplianceTest {
    private val spec = DocSpec(
        id = "uk", name = "UK passport", country = "United Kingdom", countryCode = "GB",
        print = PrintSize(35f, 45f, "35 x 45 mm"), headMm = Range(29f, 34f),
    )
    private val base = FaceGeometry(
        faceCount = 1, imageWidth = 1536, imageHeight = 2048,
        chinY = 1300f, foreheadY = 800f, crownY = 700f, crownEstimated = false,
        leftEyeX = 680f, leftEyeY = 1000f, rightEyeX = 860f, rightEyeY = 1000f, midlineX = 770f,
        faceLeftX = 600f, faceRightX = 940f,
    )
    private val goodStats = PhotoStats(0.55f, 0.56f, 0.52f, null)

    private fun run(face: FaceGeometry, stats: PhotoStats = goodStats) =
        ComplianceChecker.run(CheckInput(spec, face, CropSolver.solve(face, spec), stats, 531)).associateBy { it.id }

    @Test fun aGoodPhotoPassesAllTwelve() {
        val r = run(base)
        assertEquals(12, r.size)
        assertEquals(12, r.values.count { it.passed })
    }

    @Test fun rollOfSevenFailsAndThreePasses() {
        assertFalse(run(base.copy(rollDeg = 7f))[CheckId.Level]!!.passed)
        assertTrue(run(base.copy(rollDeg = -3f))[CheckId.Level]!!.passed)
    }

    @Test fun closedEyesUnevenLightAndBusyWallFail() {
        assertFalse(run(base.copy(leftEyeBlink = 0.8f))[CheckId.EyesOpen]!!.passed)
        assertFalse(run(base, PhotoStats(0.5f, 0.7f, 0.35f, null))[CheckId.Lighting]!!.passed)
        assertFalse(run(base, PhotoStats(0.5f, 0.5f, 0.5f, 0.2f))[CheckId.Background]!!.passed)
        // A brick wall: even overall but dark and patterned.
        assertFalse(run(base, PhotoStats(0.5f, 0.5f, 0.5f, 0.05f, backgroundMean = 0.48f, backgroundTexture = 0.08f))[CheckId.Background]!!.passed)
        assertTrue(run(base, PhotoStats(0.5f, 0.5f, 0.5f, 0.02f, backgroundMean = 0.9f, backgroundTexture = 0.01f))[CheckId.Background]!!.passed)
        assertFalse(run(base, PhotoStats(0.12f, 0.12f, 0.12f, null))[CheckId.Exposure]!!.passed)
    }

    @Test fun turnedHeadFailsOnYawButAllowsModestPitch() {
        assertFalse(run(base.copy(yawDeg = 9f))[CheckId.FacingCamera]!!.passed)
        assertTrue(run(base.copy(yawDeg = 3f, pitchDeg = 9f))[CheckId.FacingCamera]!!.passed)
        assertFalse(run(base.copy(pitchDeg = -14f))[CheckId.FacingCamera]!!.passed)
    }

    @Test fun twoFacesFailEverything() {
        val r = ComplianceChecker.run(CheckInput(spec, base.copy(faceCount = 2), null, null, 531))
        assertEquals(12, r.size)
        assertEquals(0, ComplianceChecker.passedCount(r))
    }

    @Test fun liveGuideOrdersItsHints() {
        val frame = LiveGuide.frameFor(spec, 3f / 4f)
        val cx = frame.left + frame.width / 2
        fun face(crown: Float, chin: Float, mid: Float = cx, roll: Float = 0f) =
            LiveFace(crown, chin, crown + (chin - crown) * 0.5f, mid, roll, 0f, 0f)
        val headH = spec.headFraction.mid * frame.height
        val crownY = frame.top + frame.height * 0.1f
        assertEquals(LiveHint.NoFace, LiveGuide.evaluate(0, null, frame, spec))
        assertEquals(LiveHint.MoveCloser, LiveGuide.evaluate(1, face(crownY, crownY + headH * 0.5f), frame, spec))
        assertEquals(LiveHint.CenterFace, LiveGuide.evaluate(1, face(crownY, crownY + headH, mid = cx + frame.width * 0.3f), frame, spec))
        assertEquals(LiveHint.TiltLevel, LiveGuide.evaluate(1, face(crownY, crownY + headH, roll = 9f), frame, spec))
        assertEquals(LiveHint.Ready, LiveGuide.evaluate(1, face(crownY, crownY + headH), frame, spec))
    }

    /**
     * The viewfinder measures tilt on a 480 x 640 analysis frame. Normalising x and y by different
     * pixel extents before the atan2 reported 4.5 degrees for a 6 degree tilt, so the live hint said
     * Ready and the Checks screen then failed Level on the same head.
     */
    @Test fun theLiveRollIsTheAngleOnTheWallNotInTheFrame() {
        val drop = (120f * kotlin.math.tan(Math.toRadians(6.0))).toFloat()
        val frameFace = FaceGeometry(
            faceCount = 1, imageWidth = 480, imageHeight = 640,
            chinY = 420f, foreheadY = 260f, crownY = 228f,
            leftEyeX = 180f, leftEyeY = 300f - drop / 2f, rightEyeX = 300f, rightEyeY = 300f + drop / 2f,
            midlineX = 240f, faceLeftX = 150f, faceRightX = 330f,
            rollDeg = FaceGeometry.rollFromEyes(180f, 300f - drop / 2f, 300f, 300f + drop / 2f),
        )
        val live = LiveFace.from(frameFace, mirror = false)
        assertEquals(6f, live.rollDeg, 0.01f)
        assertTrue(kotlin.math.abs(live.rollDeg) >= ComplianceChecker.MAX_ROLL)
        // Positions are still normalised to the frame, which is what the guides compare against.
        assertEquals(0.5f, live.midX, 1e-4f)
        assertEquals(420f / 640f, live.chinY, 1e-4f)
        assertEquals(228f / 640f, live.crownY, 1e-4f)
        // The front lens mirrors the frame sideways; it does not make the head any more level.
        val mirrored = LiveFace.from(frameFace.copy(midlineX = 120f), mirror = true)
        assertEquals(0.75f, mirrored.midX, 1e-4f)
        assertEquals(live.rollDeg, mirrored.rollDeg, 1e-6f)
        // The hint and the still check now agree: this head is tilted.
        val frame = LiveGuide.frameFor(spec, 3f / 4f)
        val cx = frame.left + frame.width / 2f
        val crown = frame.top + frame.height * 0.1f
        val chin = crown + spec.headFraction.mid * frame.height
        val inFrame = LiveFace(crown, chin, (crown + chin) / 2f, cx, live.rollDeg, 0f, 0f)
        assertEquals(LiveHint.TiltLevel, LiveGuide.evaluate(1, inFrame, frame, spec))
        assertFalse(run(base.copy(rollDeg = live.rollDeg))[CheckId.Level]!!.passed)
    }
}
