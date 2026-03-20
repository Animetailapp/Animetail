package eu.kanade.presentation.entries.manga.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import eu.kanade.tachiyomi.ui.entries.manga.notes.MangaNotesScreen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

private const val MAX_LENGTH = 250
private const val MAX_LENGTH_WARN = MAX_LENGTH * 0.9

@Composable
fun MangaNotesTextArea(
    state: MangaNotesScreen.State,
    onUpdate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf(state.notes) }

    LaunchedEffect(state.notes) {
        if (state.notes != text) {
            text = state.notes
        }
    }

    Column(
        modifier = modifier
            .padding(horizontal = MaterialTheme.padding.small)
            .fillMaxSize(),
    ) {
        val remaining = MAX_LENGTH - text.length

        OutlinedTextField(
            value = text,
            onValueChange = { newValue ->
                if (newValue.length <= MAX_LENGTH) {
                    text = newValue
                    onUpdate(newValue)
                }
            },
            modifier = Modifier
                .fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = { Text(text = stringResource(MR.strings.notes_placeholder)) },
            minLines = 8,
            maxLines = 16,
        )
        Text(
            text = remaining.toString(),
            color = if (text.length > MAX_LENGTH_WARN) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(vertical = MaterialTheme.padding.small),
        )
    }
}
