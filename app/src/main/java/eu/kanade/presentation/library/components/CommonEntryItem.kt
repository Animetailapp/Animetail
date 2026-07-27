package eu.kanade.presentation.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.entries.components.ItemCover
import kotlinx.coroutines.delay
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.selectedBackground
import tachiyomi.domain.entries.EntryCover as EntryCoverModel

object CommonEntryItemDefaults {
    val GridHorizontalSpacer = 4.dp
    val GridVerticalSpacer = 4.dp

    @Suppress("ConstPropertyName")
    const val BrowseFavoriteCoverAlpha = 0.34f
}

private val ContinueViewingButtonSizeSmall = 28.dp
private val ContinueViewingButtonSizeLarge = 32.dp

private val ContinueViewingButtonIconSizeSmall = 16.dp
private val ContinueViewingButtonIconSizeLarge = 20.dp

private val ContinueViewingButtonGridPadding = 6.dp
private val ContinueViewingButtonListSpacing = 8.dp

private const val GRID_SELECTED_COVER_ALPHA = 0.95f

/**
 * Layout of grid list item with title overlaying the cover.
 * Accepts null [title] for a cover-only view.
 */
@Composable
fun EntryCompactGridItem(
    coverData: EntryCoverModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isSelected: Boolean = false,
    title: String? = null,
    onClickContinueViewing: (() -> Unit)? = null,
    coverAlpha: Float = 1f,
    coverBadgeStart: @Composable (RowScope.() -> Unit)? = null,
    coverBadgeEnd: @Composable (RowScope.() -> Unit)? = null,
    isFirstItem: Boolean = false,
) {
    val context = LocalContext.current
    val isAndroidTV = remember {
        context.packageManager.hasSystemFeature("android.software.leanback")
    }

    // Si es el primer elemento en Android TV, crear un FocusRequester para él
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    // Intentar enfocar automáticamente en este elemento si es el primero
    if (isFirstItem && isAndroidTV) {
        LaunchedEffect(Unit) {
            delay(500) // Esperar que la UI se renderice
            focusRequester.requestFocus()
        }
    }

    // Color para el borde en Android TV
    val primaryColor = MaterialTheme.colorScheme.primary
    // Extraer el valor del radio y añadir 1 para un ajuste perfecto
    val cornerRadius =
        (
            MaterialTheme.shapes.small.toString().replace(
                "RoundedCornerShape\\((.*)\\)".toRegex(),
                "$1",
            ).toFloatOrNull()
                ?: 4f
            ) +
            1f

    Box(
        modifier = Modifier
            .then(
                if (isFirstItem && isAndroidTV) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                },
            )
            .then(
                if (isAndroidTV) {
                    Modifier.onFocusChanged {
                        isFocused = it.isFocused
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        // El contenido principal (la casilla)
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        ) {
            EntryGridCover(
                cover = {
                    ItemCover.Book(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (isSelected) GRID_SELECTED_COVER_ALPHA else coverAlpha),
                        data = coverData,
                    )
                },
                badgesStart = coverBadgeStart,
                badgesEnd = coverBadgeEnd,
                content = {
                    if (title != null) {
                        CoverTextOverlay(
                            title = title,
                            onClickContinueViewing = onClickContinueViewing,
                        )
                    } else if (onClickContinueViewing != null) {
                        ContinueViewingButton(
                            size = ContinueViewingButtonSizeLarge,
                            iconSize = ContinueViewingButtonIconSizeLarge,
                            onClick = onClickContinueViewing,
                            modifier = Modifier
                                .padding(ContinueViewingButtonGridPadding)
                                .align(Alignment.BottomEnd),
                        )
                    }
                },
            )
        }

        // El borde exterior (solo visible cuando está enfocado)
        if (isAndroidTV && isFocused) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        // Dibujar un borde delgado con esquinas redondeadas
                        val strokeWidth = 2.dp.toPx()
                        val cornerSize = cornerRadius.dp.toPx()

                        // Ajustar el tamaño para que el borde se dibuje dentro del área visible
                        inset(0f, 0f, 0f, 0f) {
                            drawRoundRect(
                                color = primaryColor,
                                style = Stroke(width = strokeWidth),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerSize),
                            )
                        }
                    },
            )
        }
    }
}

/**
 * Title overlay for [EntryCompactGridItem]
 */
@Composable
private fun BoxScope.CoverTextOverlay(
    title: String,
    onClickContinueViewing: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color(0xAA000000),
                ),
            )
            .fillMaxHeight(0.33f)
            .fillMaxWidth()
            .align(Alignment.BottomCenter),
    )
    Row(
        modifier = Modifier.align(Alignment.BottomStart),
        verticalAlignment = Alignment.Bottom,
    ) {
        GridItemTitle(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp),
            title = title,
            style = MaterialTheme.typography.titleSmall.copy(
                color = Color.White,
                shadow = Shadow(
                    color = Color.Black,
                    blurRadius = 4f,
                ),
            ),
            minLines = 1,
        )
        if (onClickContinueViewing != null) {
            ContinueViewingButton(
                size = ContinueViewingButtonSizeSmall,
                iconSize = ContinueViewingButtonIconSizeSmall,
                onClick = onClickContinueViewing,
                modifier = Modifier.padding(
                    end = ContinueViewingButtonGridPadding,
                    bottom = ContinueViewingButtonGridPadding,
                ),
            )
        }
    }
}

/**
 * Layout of grid list item with title below the cover.
 */
@Composable
fun EntryComfortableGridItem(
    isSelected: Boolean = false,
    title: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    titleMaxLines: Int = 2,
    coverData: EntryCoverModel,
    coverAlpha: Float = 1f,
    coverBadgeStart: (@Composable RowScope.() -> Unit)? = null,
    coverBadgeEnd: (@Composable RowScope.() -> Unit)? = null,
    onClickContinueViewing: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val isAndroidTV = remember {
        context.packageManager.hasSystemFeature("android.software.leanback")
    }
    var isFocused by remember { mutableStateOf(false) }
    val primaryColor = MaterialTheme.colorScheme.primary
    // Extraer el valor del radio y añadir 1 para un ajuste perfecto
    val cornerRadius =
        (
            MaterialTheme.shapes.small.toString().replace(
                "RoundedCornerShape\\((.*)\\)".toRegex(),
                "$1",
            ).toFloatOrNull()
                ?: 4f
            ) +
            1f

    Box(
        modifier = Modifier
            .then(
                if (isAndroidTV) {
                    Modifier.onFocusChanged {
                        isFocused = it.isFocused
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        // El contenido principal
        Column(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        ) {
            EntryGridCover(
                cover = {
                    ItemCover.Book(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (isSelected) GRID_SELECTED_COVER_ALPHA else coverAlpha),
                        data = coverData,
                    )
                },
                badgesStart = coverBadgeStart,
                badgesEnd = coverBadgeEnd,
                content = {
                    if (onClickContinueViewing != null) {
                        ContinueViewingButton(
                            size = ContinueViewingButtonSizeLarge,
                            iconSize = ContinueViewingButtonIconSizeLarge,
                            onClick = onClickContinueViewing,
                            modifier = Modifier
                                .padding(ContinueViewingButtonGridPadding)
                                .align(Alignment.BottomEnd),
                        )
                    }
                },
            )
            GridItemTitle(
                modifier = Modifier.padding(4.dp),
                title = title,
                style = MaterialTheme.typography.titleSmall,
                minLines = 2,
                maxLines = titleMaxLines,
            )
        }
        // El borde exterior (solo visible cuando está enfocado)Add commentMore actions
        if (isAndroidTV && isFocused) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        // Dibujar un borde delgado con esquinas redondeadas
                        val strokeWidth = 2.dp.toPx()
                        val cornerSize = cornerRadius.dp.toPx()

                        // Ajustar el tamaño para que el borde se dibuje dentro del área visible
                        inset(0f, 0f, 0f, 0f) {
                            drawRoundRect(
                                color = primaryColor,
                                style = Stroke(width = strokeWidth),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerSize),
                            )
                        }
                    },
            )
        }
    }
}

/**
 * Common cover layout to add contents to be drawn on top of the cover.
 */
@Composable
private fun EntryGridCover(
    modifier: Modifier = Modifier,
    cover: @Composable BoxScope.() -> Unit = {},
    badgesStart: (@Composable RowScope.() -> Unit)? = null,
    badgesEnd: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable (BoxScope.() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ItemCover.Book.ratio),
    ) {
        cover()
        content?.invoke(this)
        if (badgesStart != null) {
            BadgeGroup(
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.TopStart),
                content = badgesStart,
            )
        }

        if (badgesEnd != null) {
            BadgeGroup(
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.TopEnd),
                content = badgesEnd,
            )
        }
    }
}

@Composable
private fun GridItemTitle(
    title: String,
    style: TextStyle,
    minLines: Int,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
) {
    Text(
        modifier = modifier,
        text = title,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        minLines = minLines,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = style,
    )
}

/**
 * Wrapper for grid items to handle selection state, click and long click.
 */
@Composable
private fun GridItemSelectable(
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val isAndroidTV = remember {
        context.packageManager.hasSystemFeature("android.software.leanback")
    }

    // Recordar el estado de foco para mostrar un borde más visible
    var isFocused by remember { mutableStateOf(false) }

    // Colores para usar en los efectos visuales
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .then(
                if (isAndroidTV) {
                    Modifier
                        .onFocusChanged { isFocused = it.isFocused }
                        .drawBehind {
                            if (isFocused) {
                                // Usar el color del tema en lugar de cian fijo
                                val borderWidth = 6.dp.toPx()
                                drawRect(
                                    color = primaryColor,
                                    size = size,
                                    style = Stroke(width = borderWidth),
                                )
                                // Fondo con mayor opacidad para mejor visibilidad
                                drawRect(color = primaryColor.copy(alpha = 0.35f))
                            }
                        }
                } else {
                    Modifier
                },
            )
            .selectedOutline(
                isSelected = isSelected,
                color = secondaryColor,
                primaryColor = primaryColor,
                isAndroidTV = isAndroidTV,
            )
            .padding(4.dp),
    ) {
        val contentColor = if (isSelected) {
            if (isAndroidTV) {
                primaryColor
            } else {
                MaterialTheme.colorScheme.onSecondary
            }
        } else {
            LocalContentColor.current
        }
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

/**
 * @see GridItemSelectable
 */
private fun Modifier.selectedOutline(
    isSelected: Boolean,
    color: Color,
    primaryColor: Color,
    isAndroidTV: Boolean = false,
) = drawBehind {
    if (isSelected) {
        if (isAndroidTV) {
            // Borde más grueso con color intenso para Android TV
            val borderWidth = 6.dp.toPx()

            // Fondo naranja casi sólido con solo 2% de transparencia
            drawRect(color = primaryColor.copy(alpha = 0.98f))

            // Borde para delimitar claramente el elemento seleccionado
            drawRect(
                color = Color.White.copy(alpha = 0.7f), // Borde blanco semitransparente para contraste
                size = size,
                style = Stroke(width = borderWidth),
            )
        } else {
            // Para móviles también hacemos el fondo más visible
            val borderWidth = 4.dp.toPx()

            // Fondo naranja casi sólido con solo 15% de transparencia
            drawRect(color = primaryColor.copy(alpha = 0.85f))

            // Borde visible para delimitar el elemento
            drawRect(
                color = primaryColor,
                size = size,
                style = Stroke(width = borderWidth),
            )
        }
    }
}

/**
 * Layout of list item.
 */
@Composable
fun EntryListItem(
    isSelected: Boolean = false,
    title: String,
    coverData: EntryCoverModel,
    coverAlpha: Float = 1f,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    badge: @Composable (RowScope.() -> Unit),
    onClickContinueViewing: (() -> Unit)? = null,
    entries: Int = 0,
    containerHeight: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isAndroidTV = remember {
        context.packageManager.hasSystemFeature("android.software.leanback")
    }

    // Estado de foco para mejorar visibilidad en Android TV
    var isFocused by remember { mutableStateOf(false) }

    // Obtener colores del tema para usar en drawBehind
    val primaryColor = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .selectedBackground(isSelected)
            .drawBehind {
                if (isSelected) {
                    // Borde sutil de 1px para ambas plataformas
                    val borderWidth = 1.dp.toPx()
                    drawRect(
                        color = primaryColor,
                        size = size,
                        style = Stroke(width = borderWidth),
                    )
                }
                // Añadir un indicador de foco muy visible
                if (isAndroidTV && isFocused) {
                    val focusBorderWidth = 3.dp.toPx() // Reducido de 5dp a 3dp
                    drawRect(
                        color = primaryColor,
                        size = size,
                        style = Stroke(width = focusBorderWidth),
                    )
                    // Fondo con mayor opacidad para mayor contraste
                    drawRect(color = primaryColor.copy(alpha = 0.35f))
                }
            }
            .height(
                when (entries) {
                    0 -> 76.dp

                    else -> {
                        val density = LocalDensity.current
                        with(density) { (containerHeight / entries).toDp() } - (3 / entries).dp
                    }
                },
            )
            .then(
                if (isAndroidTV) {
                    Modifier
                        .onFocusChanged { isFocused = it.isFocused }
                } else {
                    Modifier
                },
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 16.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItemCover.Book(
            modifier = Modifier
                .fillMaxHeight()
                .alpha(coverAlpha),
            data = coverData,
        )
        Text(
            text = title,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .weight(1f),
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        BadgeGroup(content = badge)
        if (onClickContinueViewing != null) {
            ContinueViewingButton(
                size = ContinueViewingButtonSizeSmall,
                iconSize = ContinueViewingButtonIconSizeSmall,
                onClick = onClickContinueViewing,
                modifier = Modifier.padding(start = ContinueViewingButtonListSpacing),
            )
        }
    }
}

@Composable
private fun ContinueViewingButton(
    size: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        FilledIconButton(
            onClick = onClick,
            shape = MaterialTheme.shapes.small,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                contentColor = contentColorFor(MaterialTheme.colorScheme.primaryContainer),
            ),
            modifier = Modifier.size(size),
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(MR.strings.action_resume),
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
