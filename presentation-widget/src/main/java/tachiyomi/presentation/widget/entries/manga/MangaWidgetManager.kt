package tachiyomi.presentation.widget.entries.manga

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.LifecycleCoroutineScope
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.updates.manga.interactor.GetMangaUpdates

@Inject
class MangaWidgetManager(
    private val getUpdates: GetMangaUpdates,
    private val securityPreferences: SecurityPreferences,
) {

    context(context: Context)
    fun init(scope: LifecycleCoroutineScope) {
        combine(
            getUpdates.subscribe(
                read = false,
                after = BaseMangaUpdatesGridGlanceWidget.DateLimit.toEpochMilliseconds(),
            ),
            securityPreferences.useAuthenticator.changes(),
            transform = { a, b -> a to b },
        )
            .distinctUntilChanged { old, new ->
                old.second == new.second &&
                    old.first.map { it.chapterId }.toSet() == new.first.map { it.chapterId }.toSet()
            }
            .onEach {
                try {
                    MangaUpdatesGridGlanceWidget().updateAll(context)
                    MangaUpdatesGridCoverScreenGlanceWidget().updateAll(context)
                } catch (e: Exception) {
                    context.logcat(LogPriority.ERROR, e) { "Failed to update widget" }
                }
            }
            .flowOn(Dispatchers.Main)
            .launchIn(scope)
    }
}
