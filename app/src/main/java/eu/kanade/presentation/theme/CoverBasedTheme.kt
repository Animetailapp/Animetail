package eu.kanade.presentation.theme

import android.graphics.Bitmap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.theme.colorscheme.CustomCompatColorScheme
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun CoverBasedTheme(
    anime: Anime,
    content: @Composable () -> Unit,
) {
    CoverBasedThemeImpl(coverData = anime, thumbnailUrl = anime.thumbnailUrl, content = content)
}

@Composable
fun CoverBasedTheme(
    manga: Manga,
    content: @Composable () -> Unit,
) {
    CoverBasedThemeImpl(coverData = manga, thumbnailUrl = manga.thumbnailUrl, content = content)
}

@Composable
private fun CoverBasedThemeImpl(
    coverData: Any,
    thumbnailUrl: String?,
    content: @Composable () -> Unit,
) {
    val uiPreferences = Injekt.get<UiPreferences>()
    val enabled = uiPreferences.themeCoverBased.get()

    if (!enabled) {
        content()
        return
    }

    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val isAmoled = uiPreferences.themeDarkAmoled.get()
    val style = uiPreferences.themeCoverBasedStyle.get()

    var coverColorScheme by remember { mutableStateOf<ColorScheme?>(null) }

    LaunchedEffect(coverData, thumbnailUrl, style, isDark, isAmoled) {
        thumbnailUrl ?: return@LaunchedEffect
        try {
            val request = ImageRequest.Builder(context)
                .data(coverData)
                .build()
            val result = context.imageLoader.execute(request)
            if (result is SuccessResult) {
                val bitmap = result.image.toBitmap().copy(Bitmap.Config.ARGB_8888, false)
                val palette = Palette.from(bitmap).generate()
                val dominantColor = palette.getDominantColor(
                    palette.getVibrantColor(
                        palette.getMutedColor(0xFF6200EE.toInt()),
                    ),
                )
                coverColorScheme = CustomCompatColorScheme.generateColorSchemeFromSeed(
                    seed = Color(dominantColor),
                    dark = isDark,
                    style = style,
                ).let { scheme ->
                    if (isAmoled && isDark) {
                        scheme.copy(
                            background = Color.Black,
                            surface = Color.Black,
                            surfaceVariant = Color.Black,
                        )
                    } else {
                        scheme
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback: use current theme
        }
    }

    val colorScheme = coverColorScheme
    if (colorScheme != null) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            content = content,
        )
    } else {
        content()
    }
}
