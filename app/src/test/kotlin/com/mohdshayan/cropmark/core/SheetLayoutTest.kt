package com.mohdshayan.cropmark.core

import com.mohdshayan.cropmark.core.sheet.Paper
import com.mohdshayan.cropmark.core.sheet.SheetLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetLayoutTest {
    @Test fun thirtyFiveByFortyFiveOnFourBySixFitsEight() {
        val plan = SheetLayout.plan(Paper.FourBySix, 35f, 45f)
        assertEquals(8, plan.capacity)
        assertTrue(plan.landscape)
        assertFalse(plan.tight)
        plan.cells.forEach { c ->
            assertTrue(c.x >= SheetLayout.MARGIN_MM - 1e-3f && c.x + c.w <= plan.pageWidthMm - SheetLayout.MARGIN_MM + 1e-3f)
            assertTrue(c.y >= SheetLayout.MARGIN_MM - 1e-3f && c.y + c.h <= plan.pageHeightMm - SheetLayout.MARGIN_MM + 1e-3f)
        }
    }

    @Test fun twoInchOnFourBySixGoesEdgeToEdge() {
        assertEquals(2, SheetLayout.spaced(Paper.FourBySix, 50.8f, 50.8f).capacity)
        val plan = SheetLayout.plan(Paper.FourBySix, 50.8f, 50.8f)
        assertTrue(plan.tight)
        assertEquals(6, plan.capacity)
    }

    @Test fun a4HoldsThirtyAndTheRuler() {
        val plan = SheetLayout.plan(Paper.A4, 35f, 45f)
        assertEquals(30, plan.capacity)
        assertNotNull(plan.rulerYmm)
        val lastBottom = plan.cells.maxOf { it.y + it.h }
        assertTrue("photos clear the ruler band", lastBottom < plan.rulerYmm!! - 2f)
        assertEquals(4, SheetLayout.plan(Paper.A4, 35f, 45f, copies = 4).cells.size)
    }
}
