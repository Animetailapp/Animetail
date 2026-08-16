package eu.kanade.domain.source.anime.model

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import mihon.app.di.appGraph
import tachiyomi.domain.source.anime.model.AnimeSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

val AnimeSource.icon: ImageBitmap?
    get() {
        return Injekt.get<Context>().appGraph.animeExtensionManager.getAppIconForSource(id)
            ?.toBitmap()
            ?.asImageBitmap()
    }

// AM (BROWSE) -->
// Add an extra property to Source for it to get access to ExtensionManager
val AnimeSource.installedExtension: AnimeExtension.Installed?
    get() {
        return Injekt.get<Context>().appGraph.animeExtensionManager
            .installedExtensionsFlow
            .value
            .find { ext -> ext.sources.any { it.id == id } }
    }
// <-- AM (BROWSE)
