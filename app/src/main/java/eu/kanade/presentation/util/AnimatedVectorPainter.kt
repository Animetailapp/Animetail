package eu.kanade.presentation.util

import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter

@Composable
fun rememberAnimatedVectorPainter(
    enter: AnimatedImageVector,
    exit: AnimatedImageVector,
    atEnd: Boolean,
): Painter {
    val composed = remember { mutableStateOf(false) }
    SideEffect { composed.value = true }
    return key(atEnd) {
        var playing by remember { mutableStateOf(!composed.value) }
        LaunchedEffect(Unit) { playing = true }
        rememberAnimatedVectorPainter(if (atEnd) enter else exit, playing)
    }
}
