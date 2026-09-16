package com.mohdshayan.cropmark.core.spec

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Range(val min: Float, val max: Float) {
    val mid: Float get() = (min + max) / 2f
    operator fun contains(v: Float): Boolean = v >= min - EPS && v <= max + EPS

    private companion object {
        const val EPS = 1e-4f
    }
}

@Serializable
data class PrintSize(val widthMm: Float, val heightMm: Float, val label: String)

/**
 * A digital upload rule. Width runs from [minPx] to [maxPx]; height follows [aspectW]:[aspectH].
 * KB limits are in kilobytes of 1000 bytes, the way upload forms count them.
 */
@Serializable
data class DigitalRule(
    val minPx: Int,
    val maxPx: Int,
    val aspectW: Int,
    val aspectH: Int,
    val minKb: Int? = null,
    val maxKb: Int? = null,
    /**
     * The largest compression ratio the issuer accepts (the State Department asks for 20:1 or less).
     * The exporter encodes such files at full quality so the ratio stays as low as the photo allows.
     */
    val maxCompression: Int? = null,
) {
    fun heightFor(widthPx: Int): Int = Math.round(widthPx.toDouble() * aspectH / aspectW).toInt()
}

enum class BackgroundKind(val key: String, val argb: Int) {
    Keep("keep", 0),
    White("white", 0xFFFFFFFF.toInt()),
    LightBlue("light_blue", 0xFFDCE9F5.toInt()),
    LightGrey("light_grey", 0xFFE4E5E7.toInt()),
    Custom("custom", 0);

    companion object {
        fun fromKey(key: String): BackgroundKind = entries.firstOrNull { it.key == key } ?: White
    }
}

@Serializable
data class DocSpec(
    val id: String,
    val name: String,
    val country: String,
    val countryCode: String,
    val print: PrintSize? = null,
    val digital: DigitalRule? = null,
    val headMm: Range? = null,
    val headPct: Range? = null,
    val eyeMm: Range? = null,
    val eyePct: Range? = null,
    val icaoDefault: Boolean = false,
    val backgrounds: List<String> = listOf("white"),
    val notes: String = "",
    val sourceUrl: String = "",
    val verifiedOn: String = "",
    val custom: Boolean = false,
    /** Resolution written into the file header, and used to size a print-only spec in pixels. */
    val dpi: Int = SpecMath.PRINT_DPI,
    /**
     * False when the issuer rejects photos changed with software. Cropmark then only crops and sizes:
     * the background stays as shot and exposure is left alone.
     */
    val editsAllowed: Boolean = true,
    /**
     * True when the issuer measures head height to the top of the skull, not the top of the hair
     * (IRCC: "where the top of the head or skull would be if it could be seen").
     */
    val crownAtSkull: Boolean = false,
    /**
     * True when [backgrounds] is the issuer's whole rule, so replacing the background with any
     * other colour is a breach. False where the issuer allows a family of colours ("plain light
     * coloured") or names none at all, and the list is only Cropmark's recommendation.
     */
    val backgroundsExhaustive: Boolean = false,
    /**
     * True when the issuer rejects a white background outright (India OCI asks for a plain light
     * colour that is not white), so a kept background that measures near white is worth a warning.
     */
    val whiteBackgroundRejected: Boolean = false,
) {
    /** Output width over height. Print size wins; a digital-only rule uses its pixel aspect. */
    val aspect: Float
        get() = print?.let { it.widthMm / it.heightMm }
            ?: digital?.let { it.aspectW.toFloat() / it.aspectH }
            ?: 1f

    /** Physical size in mm. A digital-only spec is drawn as if printed at its dpi from its smallest size. */
    val widthMm: Float get() = print?.widthMm ?: SpecMath.pxToMm(digital?.minPx ?: 600, dpi)
    val heightMm: Float get() = print?.heightMm ?: (widthMm / aspect)

    /** Chin to crown as a fraction of the photo height. */
    val headFraction: Range
        get() = when {
            headMm != null -> Range(headMm.min / heightMm, headMm.max / heightMm)
            headPct != null -> Range(headPct.min / 100f, headPct.max / 100f)
            else -> ICAO_HEAD
        }

    /** Eye line measured up from the bottom edge, as a fraction of the height, when the issuer gives one. */
    val eyeFraction: Range?
        get() = when {
            eyeMm != null -> Range(eyeMm.min / heightMm, eyeMm.max / heightMm)
            eyePct != null -> Range(eyePct.min / 100f, eyePct.max / 100f)
            else -> null
        }

    val sizeLabel: String
        get() = print?.label ?: digital?.let {
            if (it.minPx == it.maxPx) "${it.minPx} x ${it.heightFor(it.minPx)} px" else "${it.minPx} to ${it.maxPx} px"
        } ?: ""

    val defaultBackground: BackgroundKind
        get() = BackgroundKind.fromKey(backgrounds.firstOrNull() ?: "white")

    companion object {
        /** The common ICAO range used when an issuer gives no head size. */
        val ICAO_HEAD = Range(0.70f, 0.80f)
    }
}

@Serializable
data class SpecFile(val format: Int, val specs: List<DocSpec>)

object SpecCatalog {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): List<DocSpec> {
        val file = json.decodeFromString(SpecFile.serializer(), text)
        require(file.format == 1) { "Unknown spec format ${file.format}" }
        return file.specs
    }

    /**
     * Suggested documents for a region code, in order. Unknown regions get the two most searched
     * documents worldwide.
     */
    fun suggestionsFor(regionCode: String, specs: List<DocSpec>): List<DocSpec> {
        val ids = when (regionCode.uppercase()) {
            "IN" -> listOf("in-pan", "in-passport", "in-oci")
            "US" -> listOf("us-passport", "us-ds160")
            "CA" -> listOf("ca-visa", "us-ds160")
            "CN" -> listOf("cn-visa")
            else -> listOf("us-ds160", "in-evisa")
        }
        return ids.mapNotNull { id -> specs.firstOrNull { it.id == id } }
    }

    /** Case-insensitive match on name, country and size label. Blank query returns everything. */
    fun search(query: String, specs: List<DocSpec>): List<DocSpec> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return specs
        val tokens = q.split(Regex("\\s+"))
        return specs.filter { s ->
            val hay = "${s.name} ${s.country} ${s.sizeLabel} ${s.id}".lowercase().replace("x", " x ")
            tokens.all { t -> hay.contains(t) || hay.replace(" ", "").contains(t.replace(" ", "")) }
        }
    }

    /** Whole months between a yyyy-mm-dd date and today. */
    fun monthsSince(verifiedOn: String, todayYear: Int, todayMonth: Int): Int? {
        val parts = verifiedOn.split("-")
        if (parts.size < 2) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return (todayYear - y) * 12 + (todayMonth - m)
    }
}

object SpecMath {
    const val PRINT_DPI = 300
    const val MM_PER_INCH = 25.4f

    fun mmToPx(mm: Float, dpi: Int = PRINT_DPI): Int = Math.round(mm / MM_PER_INCH * dpi)

    fun pxToMm(px: Int, dpi: Int = PRINT_DPI): Float = px * MM_PER_INCH / dpi

    /**
     * The output pixel size for a form file. A digital rule picks the requested width clamped to its
     * range; a print-only spec is its print size at the spec's dpi.
     */
    fun formPixels(spec: DocSpec, requestedWidth: Int? = null): Pair<Int, Int> {
        val d = spec.digital
        if (d != null) {
            val w = (requestedWidth ?: d.minPx).coerceIn(d.minPx, d.maxPx)
            return w to d.heightFor(w)
        }
        val p = spec.print ?: return 600 to 600
        return mmToPx(p.widthMm, spec.dpi) to mmToPx(p.heightMm, spec.dpi)
    }

    /** Width choices offered on the export screen. */
    fun widthChoices(spec: DocSpec): List<Int> {
        val d = spec.digital ?: return listOf(formPixels(spec).first)
        if (d.minPx == d.maxPx) return listOf(d.minPx)
        val mid = ((d.minPx + d.maxPx) / 2 / 10) * 10
        return listOf(d.minPx, mid, d.maxPx).distinct()
    }
}
