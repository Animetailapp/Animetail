package eu.kanade.presentation.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Representa los diferentes formatos de contenido soportados en la plataforma.
 */
enum class MediaType(val icon: ImageVector, val color: Color) {
    ALL(Icons.Default.VideoLibrary, Color(0xFF6366F1)),
    MOVIES(Icons.Default.Movie, Color(0xFFEAB308)),
    SERIES(Icons.Default.Tv, Color(0xFFA855F7)),
    ANIME(Icons.Default.PlayArrow, Color(0xFFEF4444)),
    MANGA(Icons.Default.Book, Color(0xFFF97316)),
}

/**
 * Obtener la etiqueta internacionalizada para cada tipo de formato.
 */
@Composable
fun MediaType.getLabel(): String {
    return when (this) {
        MediaType.ALL -> stringResource(MR.strings.label_all_media)
        MediaType.MOVIES -> stringResource(MR.strings.label_movies)
        MediaType.SERIES -> stringResource(MR.strings.label_series)
        MediaType.ANIME -> stringResource(MR.strings.label_anime)
        MediaType.MANGA -> stringResource(MR.strings.label_manga)
    }
}
