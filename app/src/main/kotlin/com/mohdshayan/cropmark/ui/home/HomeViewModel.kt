package com.mohdshayan.cropmark.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mohdshayan.cropmark.core.spec.DocSpec
import com.mohdshayan.cropmark.core.spec.SpecCatalog
import com.mohdshayan.cropmark.data.db.CaptureWithEdit
import com.mohdshayan.cropmark.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Locale

data class HomeState(
    val query: String = "",
    val suggestions: List<DocSpec> = emptyList(),
    val groups: List<Pair<String, List<DocSpec>>> = emptyList(),
    val results: List<DocSpec> = emptyList(),
    val recents: List<CaptureWithEdit> = emptyList(),
    val specNames: Map<String, String> = emptyMap(),
    val sharedPhoto: Uri? = null,
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val specs = ServiceLocator.specs
    private val query = MutableStateFlow("")
    private val region = Locale.getDefault().country

    val state: StateFlow<HomeState> = combine(
        specs.all,
        query,
        ServiceLocator.photos.observeHistory().map { it.take(8) },
        ServiceLocator.sharedPhoto,
    ) { all, q, recents, shared ->
        val suggestions = SpecCatalog.suggestionsFor(region, all)
        val ordered = orderByRegion(all)
        HomeState(
            query = q,
            suggestions = suggestions,
            groups = ordered.groupBy { it.country }.toList(),
            results = if (q.isBlank()) emptyList() else SpecCatalog.search(q, all),
            recents = recents,
            specNames = all.associate { it.id to it.name },
            sharedPhoto = shared,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun setQuery(q: String) {
        query.value = q
    }

    fun consumeShared() {
        ServiceLocator.sharedPhoto.value = null
    }

    /** The user's own country first, then the rest in catalogue order, custom sizes last. */
    private fun orderByRegion(all: List<DocSpec>): List<DocSpec> {
        val own = all.filter { !it.custom && it.countryCode.equals(region, ignoreCase = true) }
        val rest = all.filter { !it.custom && it !in own }
        return own + rest + all.filter { it.custom }
    }
}
