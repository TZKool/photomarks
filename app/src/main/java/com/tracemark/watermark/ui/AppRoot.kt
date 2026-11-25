package com.tracemark.watermark.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tracemark.watermark.AppContainer
import com.tracemark.watermark.data.AppSettings
import com.tracemark.watermark.model.WatermarkTemplate
import com.tracemark.watermark.ui.editor.EditorScreen
import com.tracemark.watermark.ui.home.HomeScreen
import com.tracemark.watermark.ui.settings.SettingsScreen
import com.tracemark.watermark.ui.studio.TemplateStudioScreen
import com.tracemark.watermark.ui.theme.TraceTheme

sealed interface Route {
    data object Home : Route
    data class Editor(val uri: Uri) : Route
    data class Studio(val template: WatermarkTemplate?) : Route
    data object Settings : Route
}

class NavViewModel : ViewModel() {
    val stack = mutableStateListOf<Route>()
    private var initialized = false

    fun init(initial: Uri?) {
        if (initialized) return
        initialized = true
        stack.add(Route.Home)
        initial?.let { stack.add(Route.Editor(it)) }
    }

    fun push(route: Route) {
        stack.add(route)
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
}

@Composable
fun TraceMarkRoot(container: AppContainer, initialUri: Uri? = null) {
    val nav: NavViewModel = viewModel()
    val settings by container.settings.settings.collectAsStateWithLifecycle(
        initialValue = AppSettings()
    )
    val templates by container.templates.templates.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { nav.init(initialUri) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) nav.push(Route.Editor(uri))
    }

    BackHandler(enabled = nav.stack.size > 1) { nav.pop() }

    TraceTheme(dark = settings.darkTheme) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (val route = nav.stack.lastOrNull() ?: Route.Home) {
                Route.Home -> HomeScreen(
                    onPickPhoto = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onOpenStudio = { nav.push(Route.Studio(it)) },
                    onOpenSettings = { nav.push(Route.Settings) },
                    templates = templates
                )
                is Route.Editor -> EditorScreen(
                    uri = route.uri,
                    container = container,
                    onBack = { nav.pop() },
                    onOpenSettings = { nav.push(Route.Settings) },
                    onSaveAsTemplate = { nav.push(Route.Studio(it)) }
                )
                is Route.Studio -> TemplateStudioScreen(
                    container = container,
                    initial = route.template,
                    onBack = { nav.pop() }
                )
                Route.Settings -> SettingsScreen(
                    container = container,
                    onBack = { nav.pop() }
                )
            }
        }
    }
}
