package eu.kanade.domain.source.manga.model

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.source.manga.model.Source

suspend fun Source.icon(extensionManager: MangaExtensionManager): ImageBitmap? = withIOContext {
    extensionManager.getAppIconForSource(id)
        ?.toBitmap()
        ?.asImageBitmap()
}
