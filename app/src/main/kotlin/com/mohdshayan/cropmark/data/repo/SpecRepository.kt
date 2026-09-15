package com.mohdshayan.cropmark.data.repo

import android.content.Context
import com.mohdshayan.cropmark.core.spec.BackgroundKind
import com.mohdshayan.cropmark.core.spec.CustomSpecRules
import com.mohdshayan.cropmark.core.spec.DocSpec
import com.mohdshayan.cropmark.core.spec.SpecCatalog
import com.mohdshayan.cropmark.data.db.CustomSpec
import com.mohdshayan.cropmark.data.db.CustomSpecDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Bundled document specs from assets plus the sizes the user added. */
class SpecRepository(private val context: Context, private val dao: CustomSpecDao) {

    val bundled: List<DocSpec> by lazy {
        SpecCatalog.parse(context.assets.open("specs/specs.json").bufferedReader().use { it.readText() })
    }

    val custom: Flow<List<DocSpec>> = dao.observeAll().map { list -> list.map(::toDoc) }

    val all: Flow<List<DocSpec>> = custom.map { bundled + it }

    suspend fun get(id: String): DocSpec? = bundled.firstOrNull { it.id == id } ?: custom.first().firstOrNull { it.id == id }

    suspend fun addCustom(spec: CustomSpec): Long = dao.insert(spec)

    suspend fun deleteCustom(id: Long) = dao.delete(id)

    companion object {
        fun toDoc(c: CustomSpec): DocSpec {
            val bg = BackgroundKind.entries.firstOrNull { it != BackgroundKind.Keep && it != BackgroundKind.Custom && it.argb == c.backgroundArgb }
            return CustomSpecRules.toDocSpec(
                c.id, c.name, c.widthMm, c.heightMm, c.widthPx, c.heightPx, c.dpi,
                c.headMinPct, c.headMaxPct, c.eyeMinPct, c.eyeMaxPct, c.minKb, c.maxKb,
                bg?.key ?: "white",
                SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(c.createdAt)),
            )
        }
    }
}
