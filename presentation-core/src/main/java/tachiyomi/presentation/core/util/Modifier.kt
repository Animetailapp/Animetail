package tachiyomi.presentation.core.util

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA

fun Modifier.selectedBackground(isSelected: Boolean): Modifier = if (isSelected) {
    composed {
        val context = LocalContext.current
        val isAndroidTV = remember {
            context.packageManager.hasSystemFeature("android.software.leanback")
        }

        if (isAndroidTV) {
            // Borde cian para selección en Android TV para mayor visibilidad con D-pad
            val borderColor = Color(0xFF00E5FF) // Cyan más saturado
            Modifier.drawWithCache {
                val outline = RoundedCornerShape(4.dp).createOutline(size, layoutDirection, this)
                onDrawWithContent {
                    drawContent()
                    drawOutline(
                        outline = outline,
                        color = borderColor,
                        style = Stroke(width = 5f), // Aumentar el ancho del borde para mejor visibilidad
                    )
                }
            }
        } else {
            // Comportamiento normal para dispositivos móviles
            val alpha = if (isSystemInDarkTheme()) 0.16f else 0.22f
            val color = MaterialTheme.colorScheme.secondary.copy(alpha = alpha)
            Modifier.drawBehind {
                drawRect(color)
            }
        }
    }
} else {
    this
}

/**
 * Modifier que añade un borde de 1px cuando un elemento está enfocado en Android TV.
 * En dispositivos Android TV, esto mejora la visibilidad de la navegación con D-pad.
 */
fun Modifier.hoverBorder(
    borderColor: Color = Color.White.copy(alpha = 0.9f),
    cornerRadius: Int = 4,
): Modifier = composed {
    val context = LocalContext.current
    val isAndroidTV = remember {
        context.packageManager.hasSystemFeature("android.software.leanback")
    }

    // Aplicar SOLO para Android TV (los dispositivos móviles no necesitan esto)
    if (isAndroidTV) {
        var isFocused by remember { mutableStateOf(false) }

        this
            .onFocusChanged { isFocused = it.isFocused }
            .drawWithCache {
                val outline = RoundedCornerShape(cornerRadius.dp)
                    .createOutline(size, layoutDirection, this)
                onDrawWithContent {
                    drawContent()
                    if (isFocused) {
                        // Dibujar un borde alrededor cuando está enfocado
                        drawOutline(
                            outline = outline,
                            color = borderColor,
                            style = Stroke(width = 3f), // Aumentar el ancho para mejor visibilidad
                        )
                    }
                }
            }
    } else {
        this
    }
}

fun Modifier.secondaryItemAlpha(): Modifier = this.alpha(SECONDARY_ALPHA)

fun Modifier.clickableNoIndication(
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) = this.combinedClickable(
    interactionSource = null,
    indication = null,
    onLongClick = onLongClick,
    onClick = onClick,
)

/**
 * For TextField, the provided [action] will be invoked when
 * physical enter key is pressed.
 *
 * Naturally, the TextField should be set to single line only.
 */
fun Modifier.runOnEnterKeyPressed(action: () -> Unit): Modifier = this.onPreviewKeyEvent {
    when (it.key) {
        Key.Enter, Key.NumPadEnter -> {
            action()
            true
        }

        else -> false
    }
}

/**
 * For TextField on AppBar, this modifier will request focus
 * to the element the first time it's composed.
 */
fun Modifier.showSoftKeyboard(show: Boolean): Modifier = if (show) {
    composed {
        val focusRequester = remember { FocusRequester() }
        var openKeyboard by rememberSaveable { mutableStateOf(show) }
        LaunchedEffect(focusRequester) {
            if (openKeyboard) {
                focusRequester.requestFocus()
                openKeyboard = false
            }
        }

        Modifier.focusRequester(focusRequester)
    }
} else {
    this
}

/**
 * For TextField, this modifier will clear focus when soft
 * keyboard is hidden.
 */
fun Modifier.clearFocusOnSoftKeyboardHide(
    onFocusCleared: (() -> Unit)? = null,
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    var keyboardShowedSinceFocused by remember { mutableStateOf(false) }
    if (isFocused) {
        val imeVisible = WindowInsets.isImeVisible
        val focusManager = LocalFocusManager.current
        LaunchedEffect(imeVisible) {
            if (imeVisible) {
                keyboardShowedSinceFocused = true
            } else if (keyboardShowedSinceFocused) {
                focusManager.clearFocus()
                onFocusCleared?.invoke()
            }
        }
    }

    Modifier.onFocusChanged {
        if (isFocused != it.isFocused) {
            if (isFocused) {
                keyboardShowedSinceFocused = false
            }
            isFocused = it.isFocused
        }
    }
}
