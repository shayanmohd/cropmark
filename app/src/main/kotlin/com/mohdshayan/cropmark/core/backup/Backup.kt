package com.mohdshayan.cropmark.core.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class BackupEdit(
    val specId: String,
    val backgroundMode: String,
    val backgroundArgb: Int,
    val featherLevel: Int,
    val exposureEv: Float,
    val nudgeScale: Float,
    val nudgeXmm: Float,
    val nudgeYmm: Float,
)

@Serializable
data class BackupExport(
    val specId: String,
    val kind: String,
    val paper: String? = null,
    val copies: Int? = null,
    val widthPx: Int,
    val heightPx: Int,
    val bytes: Long,
    val fileName: String,
    val checksPassed: Int,
    val checksTotal: Int,
    val createdAt: Long,
)

@Serializable
data class BackupCapture(
    val id: Long,
    val createdAt: Long,
    val source: String,
    val lensFacing: String? = null,
    val originalSha256: String,
    val widthPx: Int,
    val heightPx: Int,
    val faceJson: String? = null,
    val hasMatte: Boolean = false,
    val updatedAt: Long,
    val edit: BackupEdit? = null,
    val exports: List<BackupExport> = emptyList(),
)

@Serializable
data class BackupCustomSpec(
    val id: Long,
    val name: String,
    val widthMm: Float? = null,
    val heightMm: Float? = null,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val dpi: Int = 300,
    val headMinPct: Float,
    val headMaxPct: Float,
    val eyeMinPct: Float? = null,
    val eyeMaxPct: Float? = null,
    val minKb: Int? = null,
    val maxKb: Int? = null,
    val backgroundArgb: Int,
    val createdAt: Long,
)

@Serializable
data class BackupManifest(
    val format: Int,
    val exportedAt: Long,
    val captures: List<BackupCapture>,
    val customSpecs: List<BackupCustomSpec> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
)

class NotABackupException : Exception("This file is not a Cropmark backup.")

object BackupCodec {
    const val FORMAT = 1
    const val MANIFEST = "manifest.json"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(manifest: BackupManifest): String = json.encodeToString(BackupManifest.serializer(), manifest)

    fun decode(text: String): BackupManifest {
        val obj: JsonObject = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw NotABackupException()
        }
        val format = try { obj["format"]?.jsonPrimitive?.int } catch (e: Exception) { null }
        if (format != FORMAT) throw NotABackupException()
        return try {
            json.decodeFromJsonElement(BackupManifest.serializer(), obj)
        } catch (e: Exception) {
            throw NotABackupException()
        }
    }

    fun captureEntry(sha: String) = "captures/$sha.jpg"
    fun matteEntry(sha: String) = "mattes/$sha.png"
}

data class CustomSpecImport(val source: BackupCustomSpec, val name: String)

data class ImportPlan(
    val captures: List<BackupCapture>,
    val skippedDuplicates: Int,
    val customSpecs: List<CustomSpecImport>,
)

/**
 * Decides what a restore adds: captures whose original photo is already on the phone are skipped,
 * custom sizes whose name is taken get " (imported)", and new ids come from the database.
 */
object ImportPlanner {
    const val CUSTOM_PREFIX = "custom-"
    private val SHA256 = Regex("[0-9a-f]{64}")

    fun plan(manifest: BackupManifest, knownSha: Set<String>, existingCustomNames: Set<String>): ImportPlan {
        val seen = knownSha.toMutableSet()
        val captures = mutableListOf<BackupCapture>()
        var skipped = 0
        for (c in manifest.captures) {
            // The hash names files on disk, so anything but a SHA-256 hex string is not a capture of ours.
            if (!SHA256.matches(c.originalSha256)) continue
            if (!seen.add(c.originalSha256)) skipped++ else captures += c
        }
        val names = existingCustomNames.toMutableSet()
        val specs = manifest.customSpecs.map { s ->
            var name = s.name
            if (name in names) {
                name = "${s.name} (imported)"
                var n = 2
                while (name in names) name = "${s.name} (imported $n)".also { n++ }
            }
            names += name
            CustomSpecImport(s, name)
        }
        return ImportPlan(captures, skipped, specs)
    }

    /** Points an edit or export at the custom size's new id; bundled spec ids pass through. */
    fun remapSpecId(specId: String, customIds: Map<Long, Long>): String {
        if (!specId.startsWith(CUSTOM_PREFIX)) return specId
        val old = specId.removePrefix(CUSTOM_PREFIX).toLongOrNull() ?: return specId
        return customIds[old]?.let { "$CUSTOM_PREFIX$it" } ?: specId
    }
}
