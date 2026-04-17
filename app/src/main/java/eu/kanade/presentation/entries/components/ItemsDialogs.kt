package eu.kanade.presentation.entries.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.util.system.isReleaseBuildType
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.domain.entries.anime.interactor.AnimeFetchInterval
import tachiyomi.domain.entries.manga.interactor.MangaFetchInterval
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.components.WheelTextPicker
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.absoluteValue

@Composable
fun DeleteItemsDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    isManga: Boolean,
) {
    val subtitle = if (isManga) MR.strings.confirm_delete_chapters else AYMR.strings.confirm_delete_episodes
    AlertDialog(
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    onConfirm()
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.are_you_sure))
        },
        text = {
            Text(text = stringResource(subtitle))
        },
    )
}

@Composable
fun SetIntervalDialog(
    interval: Int,
    nextUpdate: Instant?,
    onDismissRequest: () -> Unit,
    isManga: Boolean,
    onValueChanged: ((Int) -> Unit)? = null,
) {
    var selectedInterval by rememberSaveable { mutableIntStateOf(if (interval < 0) -interval else 0) }

    val nextUpdateDays = remember(nextUpdate) {
        return@remember if (nextUpdate != null) {
            val now = Instant.now()
            now.until(nextUpdate, ChronoUnit.DAYS).toInt().coerceAtLeast(0)
        } else {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(MR.strings.pref_library_update_smart_update)) },
        text = {
            Column {
                if (nextUpdateDays != null && nextUpdateDays >= 0 && interval >= 0) {
                    Text(
                        stringResource(
                            if (isManga) {
                                MR.strings.manga_interval_expected_update
                            } else {
                                AYMR.strings.anime_interval_expected_update
                            },
                            pluralStringResource(
                                MR.plurals.day,
                                count = nextUpdateDays,
                                nextUpdateDays,
                            ),
                            pluralStringResource(
                                MR.plurals.day,
                                count = interval.absoluteValue,
                                interval.absoluteValue,
                            ),
                        ),
                    )
                } else {
                    Text(
                        stringResource(
                            if (isManga) {
                                MR.strings.manga_interval_expected_update_null
                            } else {
                                AYMR.strings.anime_interval_expected_update_null
                            },
                        ),
                    )
                }
                Spacer(Modifier.height(MaterialTheme.padding.small))

                if (onValueChanged != null && (!isReleaseBuildType)) {
                    Text(stringResource(MR.strings.manga_interval_custom_amount))

                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        val size = DpSize(width = maxWidth / 2, height = 128.dp)
                        val maxInterval = if (isManga) {
                            MangaFetchInterval.MAX_INTERVAL
                        } else {
                            AnimeFetchInterval.MAX_INTERVAL
                        }
                        val items = (0..maxInterval)
                            .map {
                                if (it == 0) {
                                    stringResource(MR.strings.label_default)
                                } else {
                                    it.toString()
                                }
                            }
                            .toImmutableList()
                        WheelTextPicker(
                            items = items,
                            size = size,
                            startIndex = selectedInterval,
                            onSelectionChanged = { selectedInterval = it },
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onValueChanged?.invoke(selectedInterval)
                onDismissRequest()
            }) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
    )
}

@Composable
fun SetDateDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (Long) -> Unit,
    onRemove: () -> Unit,
    initialDateMillis: Long = 0,
) {
    val initialDate = remember {
        if (initialDateMillis > 0) {
            Instant.ofEpochMilli(initialDateMillis).atZone(ZoneOffset.UTC).toLocalDate()
        } else {
            LocalDate.now()
        }
    }
    val years = remember { (1900..LocalDate.now().year + 1).toList() }
    val months = remember { (1..12).toList() }

    var selectedYear by rememberSaveable { mutableIntStateOf(initialDate.year) }
    var selectedMonth by rememberSaveable { mutableIntStateOf(initialDate.monthValue) }
    var selectedDay by rememberSaveable { mutableIntStateOf(initialDate.dayOfMonth) }

    val daysInMonth = remember(selectedYear, selectedMonth) {
        YearMonth.of(selectedYear, selectedMonth).lengthOfMonth()
    }
    if (selectedDay > daysInMonth) selectedDay = daysInMonth

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(TLMR.strings.action_set_date_title)) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val locale = Locale.getDefault()
                val monthNames = remember(locale) {
                    months.map { java.time.Month.of(it).getDisplayName(TextStyle.SHORT, locale) }
                        .toImmutableList()
                }

                WheelTextPicker(
                    modifier = Modifier.weight(1f),
                    items = remember { years.map { it.toString() }.toImmutableList() },
                    startIndex = years.indexOf(selectedYear),
                    size = DpSize(64.dp, 128.dp),
                    onSelectionChanged = { selectedYear = years[it] },
                )
                WheelTextPicker(
                    modifier = Modifier.weight(1f),
                    items = monthNames,
                    startIndex = selectedMonth - 1,
                    size = DpSize(64.dp, 128.dp),
                    onSelectionChanged = { selectedMonth = it + 1 },
                )
                WheelTextPicker(
                    modifier = Modifier.weight(1f),
                    items = remember(daysInMonth) { (1..daysInMonth).map { it.toString() }.toImmutableList() },
                    startIndex = (selectedDay - 1).coerceIn(0, daysInMonth - 1),
                    size = DpSize(64.dp, 128.dp),
                    onSelectionChanged = { selectedDay = it + 1 },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onRemove()
                onDismissRequest()
            }) {
                Text(text = stringResource(MR.strings.action_remove))
            }
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val millis = LocalDate.of(selectedYear, selectedMonth, selectedDay)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
                onConfirm(millis)
                onDismissRequest()
            }) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
    )
}
