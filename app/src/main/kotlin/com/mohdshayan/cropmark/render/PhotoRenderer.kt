package com.mohdshayan.cropmark.render

import android.graphics.Bitmap
import com.mohdshayan.cropmark.core.check.CheckInput
import com.mohdshayan.cropmark.core.check.CheckResult
import com.mohdshayan.cropmark.core.check.ComplianceChecker
import com.mohdshayan.cropmark.core.check.PhotoStatsCalc
import com.mohdshayan.cropmark.core.crop.CropResult
import com.mohdshayan.cropmark.core.crop.CropSolver
import com.mohdshayan.cropmark.core.crop.Nudge
import com.mohdshayan.cropmark.core.matte.Decontaminate
import com.mohdshayan.cropmark.core.spec.BackgroundKind
import com.mohdshayan.cropmark.core.spec.DocSpec
import com.mohdshayan.cropmark.core.spec.SpecMath
import com.mohdshayan.cropmark.data.db.PhotoEdit
import com.mohdshayan.cropmark.data.repo.PhotoSession

data class Solved(val crop: CropResult, val checks: List<CheckResult>) {
    val passed: Int get() = checks.count { it.passed }
    val total: Int get() = checks.size
}

/** Crop, checks and pixels for one capture under one spec and one edit. */
object PhotoRenderer {

    fun backgroundArgb(edit: PhotoEdit): Int = when (BackgroundKind.fromKey(edit.backgroundMode)) {
        BackgroundKind.Custom -> edit.backgroundArgb
        BackgroundKind.Keep -> BackgroundKind.White.argb
        else -> BackgroundKind.fromKey(edit.backgroundMode).argb
    }

    fun keepsBackground(edit: PhotoEdit) = edit.backgroundMode == BackgroundKind.Keep.key

    fun solve(session: PhotoSession, spec: DocSpec, edit: PhotoEdit): Solved? {
        if (session.face.faceCount != 1) return null
        val crop = CropSolver.solve(session.face, spec, Nudge(edit.nudgeScale, edit.nudgeXmm, edit.nudgeYmm))
        val keep = keepsBackground(edit) || session.matte == null
        val stats = PhotoStatsCalc.measure(
            session.argb, session.width, session.height, session.face, session.matte,
            session.matteWidth, session.matteHeight, crop, keep, edit.exposureEv,
        )
        val outH = SpecMath.formPixels(spec).second
        val overhangs = keep && CropSolver.overhangs(crop, session.width, session.height)
        val checks = ComplianceChecker.run(CheckInput(spec, session.face, crop, stats, outH, overhangs))
        return Solved(crop, checks)
    }

    fun render(session: PhotoSession, crop: CropResult, edit: PhotoEdit, outW: Int, outH: Int): Bitmap {
        val keep = keepsBackground(edit) || session.matte == null
        val alpha = session.matte?.let { if (keep) it else Decontaminate.feather(it, edit.featherLevel) }
        val px = Decontaminate.renderCrop(
            session.argb, session.width, session.height, alpha, session.bgEstimate,
            session.matteWidth, session.matteHeight,
            crop.left, crop.top, crop.width, crop.height, outW, outH,
            backgroundArgb(edit), edit.exposureEv, keep,
        )
        return Bitmap.createBitmap(px, outW, outH, Bitmap.Config.ARGB_8888)
    }

    /** Output size for print at 300 dpi or the form file's pixels. */
    fun printPixels(spec: DocSpec): Pair<Int, Int> =
        SpecMath.mmToPx(spec.widthMm) to SpecMath.mmToPx(spec.heightMm)
}
