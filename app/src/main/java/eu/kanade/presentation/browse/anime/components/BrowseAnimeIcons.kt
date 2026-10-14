package eu.kanade.presentation.browse.anime.components

import android.util.DisplayMetrics
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.presentation.util.rememberResourceBitmapPainter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.coil.InstalledExtensionIcon
import eu.kanade.tachiyomi.data.coil.InstalledSourceIcon
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Dangerous
import mihon.icons.materialsymbols.rounded.Warning
import tachiyomi.domain.source.anime.model.AnimeSource
import tachiyomi.source.local.entries.anime.LocalAnimeSource

private val defaultModifier = Modifier
    .height(40.dp)
    .aspectRatio(1f)

@Composable
fun AnimeSourceIcon(
    source: AnimeSource,
    modifier: Modifier = Modifier,
) {
    when {
        source.isStub -> {
            Image(
                imageVector = MaterialSymbols.Rounded.Warning,
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.error),
                modifier = modifier.then(defaultModifier),
            )
        }

        source.id == LocalAnimeSource.ID -> {
            Image(
                painter = painterResource(R.mipmap.ic_local_source),
                contentDescription = null,
                modifier = modifier.then(defaultModifier),
            )
        }

        else -> {
            val defaultIcon = painterResource(R.mipmap.ic_default_source)
            AsyncImage(
                model = InstalledSourceIcon(source.id),
                contentDescription = null,
                placeholder = defaultIcon,
                error = defaultIcon,
                modifier = modifier.then(defaultModifier),
            )
        }
    }
}

@Composable
fun AnimeExtensionIcon(
    extension: AnimeExtension,
    modifier: Modifier = Modifier,
    density: Int = DisplayMetrics.DENSITY_DEFAULT,
) {
    when (extension) {
        is AnimeExtension.Available -> {
            AsyncImage(
                model = extension.iconUrl,
                contentDescription = null,
                placeholder = ColorPainter(Color(0x1F888888)),
                error = rememberResourceBitmapPainter(id = R.drawable.cover_error),
                modifier = modifier
                    .clip(MaterialTheme.shapes.extraSmall),
            )
        }

        is AnimeExtension.Loaded -> {
            val defaultIcon = painterResource(R.mipmap.ic_default_source)
            AsyncImage(
                model = InstalledExtensionIcon(extension.pkgName, extension.versionCode, density),
                contentDescription = null,
                placeholder = defaultIcon,
                error = defaultIcon,
                modifier = modifier,
            )
        }

        is AnimeExtension.NotLoaded -> Image(
            imageVector = MaterialSymbols.Rounded.Dangerous,
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.error),
            modifier = modifier.then(defaultModifier),
        )
    }
}

