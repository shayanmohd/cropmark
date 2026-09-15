package com.mohdshayan.cropmark.core

import com.mohdshayan.cropmark.core.backup.BackupCapture
import com.mohdshayan.cropmark.core.backup.BackupCodec
import com.mohdshayan.cropmark.core.backup.BackupCustomSpec
import com.mohdshayan.cropmark.core.backup.BackupEdit
import com.mohdshayan.cropmark.core.backup.BackupManifest
import com.mohdshayan.cropmark.core.backup.ImportPlanner
import com.mohdshayan.cropmark.core.backup.NotABackupException
import com.mohdshayan.cropmark.core.review.ReviewPolicy
import com.mohdshayan.cropmark.core.spec.CustomField
import com.mohdshayan.cropmark.core.spec.CustomSpecInput
import com.mohdshayan.cropmark.core.spec.CustomSpecRules
import com.mohdshayan.cropmark.core.spec.SizeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupTest {
    private val aaa = "a".repeat(64)
    private val bbb = "b".repeat(64)

    private fun capture(id: Long, sha: String) = BackupCapture(
        id = id, createdAt = 1_757_000_000_000 + id, source = "gallery", originalSha256 = sha,
        widthPx = 1536, heightPx = 2048, faceJson = "{}", hasMatte = true, updatedAt = 1,
        edit = BackupEdit("custom-4", "white", -1, 2, 0.3f, 1f, 0f, 0.5f),
    )

    private val manifest = BackupManifest(
        format = 1, exportedAt = 1_757_900_000_000,
        captures = listOf(capture(1, aaa), capture(2, bbb), capture(3, aaa)),
        customSpecs = listOf(BackupCustomSpec(4, "Club card", 30f, 40f, null, null, 300, 60f, 70f, null, null, null, 80, -1, 5)),
        settings = mapOf("counter_mode" to "true"),
    )

    @Test fun roundTripKeepsEverything() {
        val back = BackupCodec.decode(BackupCodec.encode(manifest))
        assertEquals(manifest, back)
    }

    @Test fun filesWithoutFormatAreRejected() {
        for (text in listOf("""{"captures":[]}""", "not json", """{"format":7,"exportedAt":1,"captures":[]}""")) {
            try {
                BackupCodec.decode(text)
                fail("accepted $text")
            } catch (_: NotABackupException) {
            }
        }
    }

    @Test fun duplicatesSkippedNamesSuffixedIdsRemapped() {
        val plan = ImportPlanner.plan(manifest, knownSha = setOf(bbb), existingCustomNames = setOf("Club card"))
        assertEquals(listOf(aaa), plan.captures.map { it.originalSha256 })
        assertEquals(2, plan.skippedDuplicates)
        assertEquals("Club card (imported)", plan.customSpecs.single().name)
        assertEquals("custom-12", ImportPlanner.remapSpecId("custom-4", mapOf(4L to 12L)))
        assertEquals("us-passport", ImportPlanner.remapSpecId("us-passport", mapOf(4L to 12L)))
    }

    @Test fun reviewWaitsForThreeSeparateDays() {
        var s = ReviewPolicy.State(0, -1, false)
        s = ReviewPolicy.recordSave(s, 100)
        s = ReviewPolicy.recordSave(s, 100)
        s = ReviewPolicy.recordSave(s, 101)
        assertFalse(ReviewPolicy.shouldPrompt(s))
        s = ReviewPolicy.recordSave(s, 105)
        assertTrue(ReviewPolicy.shouldPrompt(s))
        assertFalse(ReviewPolicy.shouldPrompt(s.copy(prompted = true)))
    }

    @Test fun customSizeValidation() {
        val ok = CustomSpecInput("Gym card", SizeUnit.Mm, 30f, 40f, 300, 60f, 75f, null, null, null, 100)
        assertTrue(CustomSpecRules.validate(ok).isEmpty())
        val bad = CustomSpecRules.validate(ok.copy(height = 200f, headMinPct = 80f, headMaxPct = 70f))
        assertEquals(setOf(CustomField.Height, CustomField.Head), bad.map { it.field }.toSet())
        assertEquals("Enter a height from 10 to 150 mm", bad.first { it.field == CustomField.Height }.message)
    }
}
