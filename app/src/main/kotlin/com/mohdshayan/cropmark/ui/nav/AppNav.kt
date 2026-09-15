package com.mohdshayan.cropmark.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mohdshayan.cropmark.ui.capture.CaptureScreen
import com.mohdshayan.cropmark.ui.checks.ChecksScreen
import com.mohdshayan.cropmark.ui.custom.CustomSizeScreen
import com.mohdshayan.cropmark.ui.editor.EditorScreen
import com.mohdshayan.cropmark.ui.export.ExportScreen
import com.mohdshayan.cropmark.ui.history.HistoryDetailScreen
import com.mohdshayan.cropmark.ui.history.HistoryScreen
import com.mohdshayan.cropmark.ui.home.HomeScreen
import com.mohdshayan.cropmark.ui.settings.SettingsScreen
import com.mohdshayan.cropmark.ui.settings.TextPageScreen
import com.mohdshayan.cropmark.ui.spec.SpecScreen
import kotlinx.serialization.Serializable

@Serializable object Home
@Serializable data class SpecDetail(val specId: String)
@Serializable data class Capture(val specId: String)
/** captureId is -1 while [importUri] is still being imported. */
@Serializable data class Editor(val captureId: Long, val specId: String, val importUri: String? = null, val source: String = "gallery")
@Serializable data class Checks(val captureId: Long, val specId: String)
@Serializable data class Export(val captureId: Long, val specId: String)
@Serializable object History
@Serializable data class HistoryDetail(val captureId: Long)
@Serializable object CustomSize
@Serializable object SettingsRoute
@Serializable data class TextPage(val kind: String)

@Composable
fun AppNav(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Home) {
        composable<Home> {
            HomeScreen(
                onSpec = { navController.navigate(SpecDetail(it)) },
                onSharedPhoto = { uri, spec -> navController.navigate(Editor(-1, spec, uri, "share")) },
                onHistory = { navController.navigate(History) },
                onSettings = { navController.navigate(SettingsRoute) },
                onCustomSize = { navController.navigate(CustomSize) },
                onRecent = { navController.navigate(HistoryDetail(it)) },
            )
        }
        composable<SpecDetail> { entry ->
            val r = entry.toRoute<SpecDetail>()
            SpecScreen(
                specId = r.specId,
                onBack = { navController.popBackStack() },
                onTakePhoto = { navController.navigate(Capture(r.specId)) },
                onGalleryPicked = { uri -> navController.navigate(Editor(-1, r.specId, uri, "gallery")) },
            )
        }
        composable<Capture> { entry ->
            val r = entry.toRoute<Capture>()
            CaptureScreen(
                specId = r.specId,
                onBack = { navController.popBackStack() },
                onCaptured = { id ->
                    navController.navigate(Editor(id, r.specId)) { popUpTo<Capture> { inclusive = true } }
                },
                onGalleryPicked = { uri ->
                    navController.navigate(Editor(-1, r.specId, uri, "gallery")) { popUpTo<Capture> { inclusive = true } }
                },
            )
        }
        composable<Editor> { entry ->
            val r = entry.toRoute<Editor>()
            EditorScreen(
                route = r,
                onBack = { navController.popBackStack() },
                onChecks = { id, spec -> navController.navigate(Checks(id, spec)) },
                onExport = { id, spec -> navController.navigate(Export(id, spec)) },
                onSwitchSpec = { id, spec ->
                    navController.navigate(Editor(id, spec)) { popUpTo<Editor> { inclusive = true } }
                },
                onRetake = { spec -> navController.navigate(Capture(spec)) { popUpTo<Editor> { inclusive = true } } },
                onNewPhoto = { uri, spec -> navController.navigate(Editor(-1, spec, uri, "gallery")) { popUpTo<Editor> { inclusive = true } } },
            )
        }
        composable<Checks> { entry ->
            val r = entry.toRoute<Checks>()
            ChecksScreen(r.captureId, r.specId, onBack = { navController.popBackStack() })
        }
        composable<Export> { entry ->
            val r = entry.toRoute<Export>()
            ExportScreen(
                captureId = r.captureId,
                specId = r.specId,
                onBack = { navController.popBackStack() },
                onCounterMode = { spec ->
                    navController.navigate(Capture(spec)) { popUpTo<Home> { inclusive = false } }
                },
            )
        }
        composable<History> {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpen = { navController.navigate(HistoryDetail(it)) },
                onMakePhoto = { navController.popBackStack(Home, inclusive = false) },
            )
        }
        composable<HistoryDetail> { entry ->
            val r = entry.toRoute<HistoryDetail>()
            HistoryDetailScreen(
                captureId = r.captureId,
                onBack = { navController.popBackStack() },
                onEdit = { spec -> navController.navigate(Editor(r.captureId, spec)) },
            )
        }
        composable<CustomSize> {
            CustomSizeScreen(
                onBack = { navController.popBackStack() },
                onSaved = { specId -> navController.navigate(SpecDetail(specId)) { popUpTo<CustomSize> { inclusive = true } } },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onPage = { navController.navigate(TextPage(it)) },
            )
        }
        composable<TextPage> { entry ->
            TextPageScreen(entry.toRoute<TextPage>().kind, onBack = { navController.popBackStack() })
        }
    }
}
