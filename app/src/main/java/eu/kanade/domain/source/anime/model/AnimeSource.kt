package eu.kanade.domain.source.anime.model

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.source.anime.model.AnimeSource

suspend fun AnimeSource.icon(extensionManager: AnimeExtensionManager): ImageBitmap? = withIOContext {
    extensionManager.getAppIconForSource(id)
        ?.toBitmap()
        ?.asImageBitmap()
}

// AM (BROWSE) -->
// Add an extra property to Source for it to get access to ExtensionManager
fun AnimeSource.installedExtension(extensionManager: AnimeExtensionManager): AnimeExtension.Installed? {
    return extensionManager
        .installedExtensions
        .filterIsInstance<AnimeExtension.Loaded>()
        .find { ext -> ext.sources.any { it.id == id } }
}
// <-- AM (BROWSE)
