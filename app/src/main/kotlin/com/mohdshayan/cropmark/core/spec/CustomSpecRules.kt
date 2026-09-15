package com.mohdshayan.cropmark.core.spec

enum class SizeUnit { Mm, Px }

data class CustomSpecInput(
    val name: String,
    val unit: SizeUnit,
    val width: Float?,
    val height: Float?,
    val dpi: Int,
    val headMinPct: Float?,
    val headMaxPct: Float?,
    val eyeMinPct: Float?,
    val eyeMaxPct: Float?,
    val minKb: Int?,
    val maxKb: Int?,
)

enum class CustomField { Name, Width, Height, Dpi, Head, Eye, Kb }

data class FieldError(val field: CustomField, val message: String)

object CustomSpecRules {
    fun validate(i: CustomSpecInput): List<FieldError> {
        val errors = mutableListOf<FieldError>()
        if (i.name.isBlank()) errors += FieldError(CustomField.Name, "Enter a name for this size")
        val (lo, hi, unit) = if (i.unit == SizeUnit.Mm) Triple(10f, 150f, "mm") else Triple(100f, 4000f, "px")
        // Written as "not in range" so NaN, which compares false both ways, is refused too.
        if (i.width == null || !(i.width >= lo && i.width <= hi)) {
            errors += FieldError(CustomField.Width, "Enter a width from ${lo.toInt()} to ${hi.toInt()} $unit")
        }
        if (i.height == null || !(i.height >= lo && i.height <= hi)) {
            errors += FieldError(CustomField.Height, "Enter a height from ${lo.toInt()} to ${hi.toInt()} $unit")
        }
        if (i.dpi !in 72..1200) errors += FieldError(CustomField.Dpi, "Enter a resolution from 72 to 1200 dpi")
        val hMin = i.headMinPct
        val hMax = i.headMaxPct
        if (hMin == null || hMax == null || !(hMin >= 30f && hMax <= 95f && hMin < hMax)) {
            errors += FieldError(CustomField.Head, "Enter a head range between 30 and 95 percent, smallest first")
        }
        if ((i.eyeMinPct == null) != (i.eyeMaxPct == null) ||
            (i.eyeMinPct != null && i.eyeMaxPct != null && !(i.eyeMinPct >= 30f && i.eyeMaxPct <= 90f && i.eyeMinPct < i.eyeMaxPct))
        ) {
            errors += FieldError(CustomField.Eye, "Enter both eye limits between 30 and 90 percent, or leave both empty")
        }
        if (i.minKb != null && i.maxKb != null && i.minKb >= i.maxKb) {
            errors += FieldError(CustomField.Kb, "The smallest file size must be under the largest")
        }
        if ((i.minKb != null && i.minKb < 1) || (i.maxKb != null && i.maxKb < 5)) {
            errors += FieldError(CustomField.Kb, "Enter a file size of at least 5 KB")
        }
        return errors
    }

    fun toDocSpec(
        id: Long,
        name: String,
        widthMm: Float?,
        heightMm: Float?,
        widthPx: Int?,
        heightPx: Int?,
        dpi: Int,
        headMinPct: Float,
        headMaxPct: Float,
        eyeMinPct: Float?,
        eyeMaxPct: Float?,
        minKb: Int?,
        maxKb: Int?,
        background: String,
        createdOn: String,
    ): DocSpec {
        val print = if (widthMm != null && heightMm != null) {
            PrintSize(widthMm, heightMm, "${fmt(widthMm)} x ${fmt(heightMm)} mm")
        } else null
        val digital = when {
            widthPx != null && heightPx != null -> DigitalRule(widthPx, widthPx, widthPx, heightPx, minKb, maxKb)
            print != null && (minKb != null || maxKb != null) -> {
                val w = SpecMath.mmToPx(print.widthMm, dpi)
                DigitalRule(w, w, w, SpecMath.mmToPx(print.heightMm, dpi), minKb, maxKb)
            }
            else -> null
        }
        return DocSpec(
            id = "${com.mohdshayan.cropmark.core.backup.ImportPlanner.CUSTOM_PREFIX}$id",
            name = name,
            country = "Your sizes",
            countryCode = "",
            print = print,
            digital = digital,
            headPct = Range(headMinPct, headMaxPct),
            eyePct = if (eyeMinPct != null && eyeMaxPct != null) Range(eyeMinPct, eyeMaxPct) else null,
            backgrounds = listOf(background),
            notes = "A size you added.",
            verifiedOn = createdOn,
            custom = true,
            dpi = dpi,
        )
    }

    private fun fmt(v: Float): String = if (v % 1f == 0f) v.toInt().toString() else String.format(java.util.Locale.ROOT, "%.1f", v)
}
