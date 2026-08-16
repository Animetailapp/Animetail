package eu.kanade.core.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import mihon.app.di.appGraph

@Composable
fun ifAnimeSourcesLoaded(): Boolean {
    val context = LocalContext.current
    return remember { context.appGraph.animeSourceManager.isInitialized }.collectAsState().value
}
