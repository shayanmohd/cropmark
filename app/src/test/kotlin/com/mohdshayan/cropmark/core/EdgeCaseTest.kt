package com.mohdshayan.cropmark.core

import com.mohdshayan.cropmark.core.backup.BackupCapture
import com.mohdshayan.cropmark.core.backup.BackupCodec
import com.mohdshayan.cropmark.core.backup.BackupManifest
import com.mohdshayan.cropmark.core.backup.ImportPlanner
import com.mohdshayan.cropmark.core.backup.NotABackupException
import com.mohdshayan.cropmark.core.crop.CropSolver
import com.mohdshayan.cropmark.core.crop.FaceGeometry
import com.mohdshayan.cropmark.core.crop.Nudge
import com.mohdshayan.cropmark.core.jpeg.JfifDensity
import com.mohdshayan.cropmark.core.jpeg.JpegEncoder
import com.mohdshayan.cropmark.core.jpeg.SizeOutcome
import com.mohdshayan.cropmark.core.jpeg.SizeSearch
import com.mohdshayan.cropmark.core.matte.Decontaminate
import com.mohdshayan.cropmark.core.matte.GuidedFilter
import com.mohdshayan.cropmark.core.review.ReviewPolicy
import com.mohdshayan.cropmark.core.sheet.Paper
import com.mohdshayan.cropmark.core.sheet.SheetLayout
import com.mohdshayan.cropmark.core.spec.CustomField
import com.mohdshayan.cropmark.core.spec.CustomSpecInput
import com.mohdshayan.cropmark.core.spec.CustomSpecRules
import com.mohdshayan.cropmark.core.spec.DigitalRule
import com.mohdshayan.cropmark.core.spec.DocSpec
import com.mohdshayan.cropmark.core.spec.PrintSize
import com.mohdshayan.cropmark.core.spec.Range
import com.mohdshayan.cropmark.core.spec.SizeUnit
import com.mohdshayan.cropmark.core.spec.SpecCatalog
import com.mohdshayan.cropmark.core.spec.SpecMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** Zero, negative, huge, unit boundaries, time zones, DST, empty input and corrupt files. */
class EdgeCaseTest {

    // Custom size

    private val okInput = CustomSpecInput("Gym card", SizeUnit.Mm, 30f, 40f, 300, 60f, 75f, null, null, null, null)
    private fun fields(i: CustomSpecInput) = CustomSpecRules.validate(i).map { it.field }.toSet()

    @Test fun customSizeUnitBoundariesAreInclusive() {
        assertTrue(fields(okInput.copy(width = 10f, height = 150f)).isEmpty())
        assertEquals(setOf(CustomField.Width), fields(okInput.copy(width = 9.99f)))
        assertEquals(setOf(CustomField.Height), fields(okInput.copy(height = 150.01f)))
        val px = okInput.copy(unit = SizeUnit.Px, width = 100f, height = 4000f)
        assertTrue(fields(px).isEmpty())
        assertEquals(setOf(CustomField.Width), fields(px.copy(width = 99f)))
        assertEquals(setOf(CustomField.Height), fields(px.copy(height = 4001f)))
        assertTrue(fields(okInput.copy(dpi = 72)).isEmpty())
        assertTrue(fields(okInput.copy(dpi = 1200)).isEmpty())
        assertEquals(setOf(CustomField.Dpi), fields(okInput.copy(dpi = 0)))
    }

    @Test fun customSizeRefusesZeroNegativeEmptyAndNaN() {
        assertEquals(setOf(CustomField.Width, CustomField.Height), fields(okInput.copy(width = 0f, height = -45f)))
        assertEquals(setOf(CustomField.Width), fields(okInput.copy(width = null)))
        // "NaN".toFloatOrNull() is NaN, not null, and NaN fails every comparison.
        assertEquals("NaN".toFloatOrNull()!!.isNaN(), true)
        assertEquals(setOf(CustomField.Width, CustomField.Height), fields(okInput.copy(width = Float.NaN, height = Float.NaN)))
        assertEquals(setOf(CustomField.Head), fields(okInput.copy(headMinPct = Float.NaN)))
        assertEquals(setOf(CustomField.Eye), fields(okInput.copy(eyeMinPct = Float.NaN, eyeMaxPct = 60f)))
        assertEquals(setOf(CustomField.Width), fields(okInput.copy(width = Float.POSITIVE_INFINITY)))
        assertEquals(setOf(CustomField.Name), fields(okInput.copy(name = "   ")))
        assertEquals(setOf(CustomField.Kb), fields(okInput.copy(minKb = 0, maxKb = 4)))
        assertEquals(setOf(CustomField.Kb), fields(okInput.copy(minKb = 100, maxKb = 100)))
    }

    // Review prompt days

    @Test fun reviewDaysFollowTheLocalCalendarNotUtc() {
        val kolkata = ZoneId.of("Asia/Kolkata")
        // 00:30 and 23:30 on 14 September 2026 in India fall on two different UTC dates.
        val early = ZonedDateTime.of(2026, 9, 14, 0, 30, 0, 0, kolkata).toInstant().toEpochMilli()
        val late = ZonedDateTime.of(2026, 9, 14, 23, 30, 0, 0, kolkata).toInstant().toEpochMilli()
        assertTrue(early / 86_400_000L != late / 86_400_000L)
        assertEquals(ReviewPolicy.localDay(early, kolkata), ReviewPolicy.localDay(late, kolkata))
        var s = ReviewPolicy.State(0, -1, false)
        s = ReviewPolicy.recordSave(s, ReviewPolicy.localDay(early, kolkata))
        s = ReviewPolicy.recordSave(s, ReviewPolicy.localDay(late, kolkata))
        assertEquals(1, s.saveDays)
    }

    @Test fun reviewDaysSurviveDaylightSavingChanges() {
        val ny = ZoneId.of("America/New_York")
        // 8 March 2026 is 23 hours long in New York, 1 November 2026 is 25 hours long.
        for ((y, m, d) in listOf(Triple(2026, 3, 8), Triple(2026, 11, 1))) {
            val first = ZonedDateTime.of(y, m, d, 0, 5, 0, 0, ny).toInstant().toEpochMilli()
            val last = ZonedDateTime.of(y, m, d, 23, 55, 0, 0, ny).toInstant().toEpochMilli()
            val next = ZonedDateTime.of(y, m, d + 1, 0, 5, 0, 0, ny).toInstant().toEpochMilli()
            assertEquals(ReviewPolicy.localDay(first, ny), ReviewPolicy.localDay(last, ny))
            assertEquals(ReviewPolicy.localDay(first, ny) + 1, ReviewPolicy.localDay(next, ny))
        }
    }

    // Backup import

    @Test fun corruptManifestsAreNotBackups() {
        val bad = listOf(
            "",
            "   ",
            "[]",
            "null",
            """{"format":1""",
            """{"format":"one","exportedAt":1,"captures":[]}""",
            """{"format":1,"exportedAt":"yesterday","captures":[]}""",
            """{"format":1,"exportedAt":1,"captures":[{"id":1}]}""",
            """{"format":1.5,"exportedAt":1,"captures":[]}""",
        )
        for (text in bad) {
            try {
                BackupCodec.decode(text)
                fail("accepted <$text>")
            } catch (_: NotABackupException) {
            }
        }
        val empty = BackupCodec.decode("""{"format":1,"exportedAt":0,"captures":[],"extra":"ignored"}""")
        assertTrue(empty.captures.isEmpty())
        assertTrue(ImportPlanner.plan(empty, emptySet(), emptySet()).captures.isEmpty())
    }

    @Test fun importSkipsCapturesWhoseHashCouldEscapeTheFolder() {
        fun cap(sha: String) = BackupCapture(1, 1, "gallery", null, sha, 10, 10, null, false, 1)
        val good = "0123456789abcdef".repeat(4)
        val manifest = BackupManifest(
            1, 1,
            listOf(cap("../../databases/app"), cap(""), cap(good.uppercase()), cap(good + "0"), cap(good)),
        )
        val plan = ImportPlanner.plan(manifest, emptySet(), emptySet())
        assertEquals(listOf(good), plan.captures.map { it.originalSha256 })
        assertEquals(0, plan.skippedDuplicates)
    }

    @Test fun importedNamesKeepCountingPastTheFirstClash() {
        val specs = List(3) { com.mohdshayan.cropmark.core.backup.BackupCustomSpec(it.toLong(), "Card", 30f, 40f, headMinPct = 60f, headMaxPct = 70f, backgroundArgb = -1, createdAt = 1) }
        val plan = ImportPlanner.plan(BackupManifest(1, 1, emptyList(), specs), emptySet(), setOf("Card"))
        assertEquals(listOf("Card (imported)", "Card (imported 2)", "Card (imported 3)"), plan.customSpecs.map { it.name })
        assertEquals("custom-abc", ImportPlanner.remapSpecId("custom-abc", mapOf(1L to 2L)))
        assertEquals("custom-9", ImportPlanner.remapSpecId("custom-9", mapOf(1L to 2L)))
    }

    // Sheets

    @Test fun aPhotoLargerThanThePaperFitsNothingWithoutCrashing() {
        val plan = SheetLayout.plan(Paper.FourBySix, 150f, 150f)
        assertEquals(0, plan.capacity)
        assertTrue(plan.cells.isEmpty())
        assertTrue(SheetLayout.plan(Paper.FiveBySeven, 150f, 150f, copies = 3).cells.isEmpty())
        assertTrue(SheetLayout.plan(Paper.A4, 150f, 150f).capacity >= 1)
    }

    @Test fun copiesAreClampedToOneAndCapacity() {
        assertEquals(1, SheetLayout.plan(Paper.FourBySix, 35f, 45f, copies = 0).cells.size)
        assertEquals(1, SheetLayout.plan(Paper.FourBySix, 35f, 45f, copies = -5).cells.size)
        assertEquals(8, SheetLayout.plan(Paper.FourBySix, 35f, 45f, copies = 1_000_000).cells.size)
        val tiny = SheetLayout.plan(Paper.Letter, 10f, 10f)
        assertTrue(tiny.capacity > 300)
        tiny.cells.forEach { assertTrue(it.x >= 0f && it.y >= 0f && it.x + it.w <= tiny.pageWidthMm && it.y + it.h <= tiny.pageHeightMm) }
    }

    @Test fun letterAndFiveBySevenHoldTheCommonSizes() {
        assertEquals(9, SheetLayout.plan(Paper.FiveBySeven, 35f, 45f).capacity)
        val letter = SheetLayout.plan(Paper.Letter, 50.8f, 50.8f)
        assertFalse(letter.landscape)
        assertEquals(20, letter.capacity)
        assertTrue(letter.cells.maxOf { it.y + it.h } < letter.rulerYmm!! - 2f)
    }

    // JPEG size search and header

    private val linear = JpegEncoder { w, h, q -> ByteArray((w.toLong() * h * q / 400).toInt()) }

    @Test fun requestedWidthOutsideTheRuleIsClamped() {
        for (req in listOf(0, -600, Int.MAX_VALUE)) {
            val out = SizeSearch.search(req, 600, 1200, { it }, null, null, linear) as SizeOutcome.Fits
            assertTrue(out.widthPx in 600..1200)
            assertEquals(SizeSearch.MAX_QUALITY, out.quality)
        }
        assertEquals(600 to 600, SpecMath.formPixels(ds160, -1))
        assertEquals(1200 to 1200, SpecMath.formPixels(ds160, Int.MAX_VALUE))
        assertEquals(0, SpecMath.mmToPx(0f))
        assertEquals(240_000, SizeSearch.maxBytes(240))
        assertEquals(40_960, SizeSearch.minBytes(40))
        assertNull(SizeSearch.maxBytes(null))
    }

    @Test fun kilobyteBoundariesAreExact() {
        // A file of exactly the cap fits; one byte over does not.
        val exact = JpegEncoder { _, _, q -> ByteArray(if (q == SizeSearch.MAX_QUALITY) 240_000 else 100) }
        assertEquals(SizeSearch.MAX_QUALITY, (SizeSearch.search(600, 600, 600, { it }, null, 240_000, exact) as SizeOutcome.Fits).quality)
        val over = JpegEncoder { _, _, q -> ByteArray(if (q == SizeSearch.MAX_QUALITY) 240_001 else 239_999) }
        val fit = SizeSearch.search(600, 600, 600, { it }, null, 240_000, over) as SizeOutcome.Fits
        assertTrue(fit.quality < SizeSearch.MAX_QUALITY && fit.bytes.size <= 240_000)
        // A minimum met exactly is accepted.
        val atMin = JpegEncoder { _, _, _ -> ByteArray(40_960) }
        assertTrue(SizeSearch.search(354, 354, 420, { it }, 40_960, 120_000, atMin) is SizeOutcome.Fits)
    }

    @Test fun headerRewriteRejectsOrSurvivesGarbage() {
        try {
            JfifDensity.withDensity(ByteArray(0), 300)
            fail("empty accepted")
        } catch (_: IllegalArgumentException) {
        }
        try {
            JfifDensity.withDensity("PNG not jpeg".toByteArray(), 300)
            fail("garbage accepted")
        } catch (_: IllegalArgumentException) {
        }
        assertNull(JfifDensity.readDpi(ByteArray(3)))
        // Truncated after SOI and a broken segment length: no crash, still starts with SOI and one APP0.
        val truncated = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(), 0x7F, 0x7F, 1, 2)
        val out = JfifDensity.withDensity(truncated, 0)
        assertEquals(0xD8, out[1].toInt() and 0xFF)
        assertEquals(1, JfifDensity.readDpi(out))
        assertEquals(65535, JfifDensity.readDpi(JfifDensity.withDensity(truncated, 1_000_000)))
    }

    // Crop, matte and catalogue

    private val ds160 = DocSpec(
        id = "us-ds160", name = "DS-160", country = "United States", countryCode = "US",
        digital = DigitalRule(600, 1200, 1, 1, maxKb = 240), headPct = Range(50f, 69f), eyePct = Range(56f, 69f),
    )

    @Test fun degenerateFacesGiveFiniteCrops() {
        val spec = DocSpec("uk", "UK", "GB", "GB", print = PrintSize(35f, 45f, "35 x 45 mm"), headMm = Range(29f, 34f))
        val flat = FaceGeometry(1, 2048, 2048, chinY = 900f, foreheadY = 900f, crownY = 900f, midlineX = 1000f, leftEyeY = 900f, rightEyeY = 900f)
        val inverted = flat.copy(crownY = 1200f)
        val huge = flat.copy(chinY = 1e7f, crownY = -1e7f, leftEyeY = 0f, rightEyeY = 0f)
        for (f in listOf(flat, inverted, huge)) {
            for (n in listOf(Nudge(), Nudge(0f, -1e6f, 1e6f), Nudge(1e6f, 1e6f, -1e6f))) {
                val c = CropSolver.solve(f, spec, n)
                listOf(c.left, c.top, c.width, c.height, c.headFraction, c.eyeFraction, c.mmPerPx).forEach {
                    assertTrue("finite for $f $n: $c", it.isFinite())
                }
                assertTrue(c.width > 0f && c.height > 0f)
            }
        }
    }

    @Test fun oddImageSizesInTheMattePath() {
        val one = GuidedFilter.boxMean(floatArrayOf(0.4f), 1, 1, 50)
        assertEquals(0.4f, one[0], 1e-6f)
        val refined = GuidedFilter.refine(floatArrayOf(1f), 1, 1, intArrayOf(0xFF808080.toInt(), 0xFF808080.toInt()), 2, 1)
        assertTrue(refined.all { it in 0f..1f })
        val a = floatArrayOf(0f, 0.5f, 1f)
        assertTrue(Decontaminate.feather(a, -3).contentEquals(Decontaminate.feather(a, 0)))
        assertTrue(Decontaminate.feather(a, 99).contentEquals(Decontaminate.feather(a, 4)))
        assertEquals(1f, Decontaminate.exposureGain(0f), 1e-6f)
        // A crop entirely outside the photo is all background, never an index error.
        val px = Decontaminate.renderCrop(IntArray(4) { -1 }, 2, 2, null, null, 2, 2, 1e5f, -1e5f, 10f, 10f, 3, 3, 0xFFDCE9F5.toInt(), 0f, false)
        assertTrue(px.all { it == 0xFFDCE9F5.toInt() })
    }

    @Test fun catalogueHelpersTakeEmptyAndOddInput() {
        val specs = listOf(ds160)
        assertEquals(specs, SpecCatalog.search("", specs))
        assertEquals(specs, SpecCatalog.search("   \t", specs))
        assertTrue(SpecCatalog.search(".*[(", specs).isEmpty())
        assertEquals(specs, SpecCatalog.search("DS-160", specs))
        assertTrue(SpecCatalog.suggestionsFor("", emptyList()).isEmpty())
        assertNull(SpecCatalog.monthsSince("", 2026, 9))
        assertNull(SpecCatalog.monthsSince("soon", 2026, 9))
        assertEquals(0, SpecCatalog.monthsSince("2026-09-14", 2026, 9))
        assertEquals(-4, SpecCatalog.monthsSince("2027-01-01", 2026, 9))
        assertEquals(18, SpecCatalog.monthsSince("2025-03-31", 2026, 9))
        try {
            SpecCatalog.parse("""{"format":2,"specs":[]}""")
            fail("format 2 accepted")
        } catch (_: IllegalArgumentException) {
        }
    }
}
