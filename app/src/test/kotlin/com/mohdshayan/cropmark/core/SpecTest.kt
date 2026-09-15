package com.mohdshayan.cropmark.core

import com.mohdshayan.cropmark.core.spec.SpecCatalog
import com.mohdshayan.cropmark.core.spec.SpecMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SpecTest {
    private val specs = SpecCatalog.parse(File("src/main/assets/specs/specs.json").readText())

    @Test fun millimetresToPixelsAt300Dpi() {
        assertEquals(413, SpecMath.mmToPx(35f))
        assertEquals(531, SpecMath.mmToPx(45f))
        assertEquals(600, SpecMath.mmToPx(50.8f))
    }

    @Test fun everyBundledRowIsSourcedDatedAndConsistent() {
        assertEquals(8, specs.size)
        assertEquals(specs.size, specs.map { it.id }.toSet().size)
        for (s in specs) {
            assertTrue("${s.id} source", s.sourceUrl.startsWith("https://"))
            assertTrue("${s.id} date", Regex("""\d{4}-\d{2}-\d{2}""").matches(s.verifiedOn))
            assertTrue("${s.id} has a size", s.print != null || s.digital != null)
            val head = s.headFraction
            assertTrue("${s.id} head range", head.min > 0.3f && head.max < 0.95f && head.min < head.max)
            s.eyeFraction?.let { assertTrue("${s.id} eye band", it.min < it.max && it.max < 1f) }
        }
    }

    @Test fun ds160IsSquareUnder240Kb() {
        val ds = specs.first { it.id == "us-ds160" }
        assertEquals(600 to 600, SpecMath.formPixels(ds))
        assertEquals(1200 to 1200, SpecMath.formPixels(ds, 5000))
        assertEquals(240, ds.digital!!.maxKb)
        val india = specs.first { it.id == "in-passport" }
        assertEquals(630 to 810, SpecMath.formPixels(india))
    }

    @Test fun searchAndSuggestions() {
        assertEquals("in-pan", SpecCatalog.search("pan", specs).single().id)
        assertTrue(SpecCatalog.search("35x45", specs).any { it.id == "ca-visa" })
        assertTrue(SpecCatalog.search("zzz", specs).isEmpty())
        assertEquals("in-pan", SpecCatalog.suggestionsFor("in", specs).first().id)
        for (region in listOf("IN", "US", "CA", "CN", "GB", "")) {
            assertTrue("suggestions for $region", SpecCatalog.suggestionsFor(region, specs).isNotEmpty())
        }
        assertEquals(19, SpecCatalog.monthsSince("2025-02-10", 2026, 9))
    }

    @Test fun issuerRulesCheckedOn14September2026() {
        val us = specs.first { it.id == "us-passport" }
        assertEquals(600 to 600, SpecMath.formPixels(us))
        assertEquals(25.4f, us.headMm!!.min, 0.01f)
        // The State Department, IRCC, Passport Seva and the OCI portal reject software-edited photos.
        for (id in listOf("us-passport", "us-ds160", "in-passport", "in-oci", "ca-visa")) {
            assertTrue("$id locks edits", !specs.first { it.id == id }.editsAllowed)
        }
        assertEquals(20, specs.first { it.id == "us-ds160" }.digital!!.maxCompression)

        // PAN: 3.5 x 2.5 cm scanned at 200 dpi, 20 KB or less.
        val pan = specs.first { it.id == "in-pan" }
        assertEquals(200, pan.dpi)
        assertEquals(197 to 276, SpecMath.formPixels(pan))
        assertEquals(20, pan.digital!!.maxKb)

        val oci = specs.first { it.id == "in-oci" }
        assertEquals(900 to 900, SpecMath.formPixels(oci, 5000))
        assertEquals(listOf("light_grey"), oci.backgrounds)

        val cn = specs.first { it.id == "cn-visa" }
        assertEquals(390 to 567, SpecMath.formPixels(cn))
        assertEquals(null, cn.digital)
    }

    @Test fun printOnlySpecsUseTheirOwnDpi() {
        val base = specs.first { it.id == "ca-visa" }
        assertEquals(413 to 531, SpecMath.formPixels(base))
        assertEquals(827 to 1063, SpecMath.formPixels(base.copy(dpi = 600)))
        val custom = com.mohdshayan.cropmark.core.spec.CustomSpecRules.toDocSpec(
            7, "Exam form", 30f, 40f, null, null, 200, 60f, 70f, null, null, null, null, "white", "2026-09-14",
        )
        assertEquals(200, custom.dpi)
        assertEquals(236 to 315, SpecMath.formPixels(custom))
        val customKb = com.mohdshayan.cropmark.core.spec.CustomSpecRules.toDocSpec(
            8, "Exam upload", 30f, 40f, null, null, 150, 60f, 70f, null, null, 10, 50, "white", "2026-09-14",
        )
        assertEquals(150, customKb.dpi)
        assertEquals(177 to 236, SpecMath.formPixels(customKb))
    }
}
