package mihon.presentation.widget.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import tachiyomi.presentation.widget.entries.anime.BaseAnimeUpdatesGridGlanceWidget
import tachiyomi.presentation.widget.entries.manga.BaseMangaUpdatesGridGlanceWidget

@ContributesTo(AppScope::class)
interface PresentationWidgetGraph {
    fun inject(widget: BaseMangaUpdatesGridGlanceWidget)
    fun inject(widget: BaseAnimeUpdatesGridGlanceWidget)
}
