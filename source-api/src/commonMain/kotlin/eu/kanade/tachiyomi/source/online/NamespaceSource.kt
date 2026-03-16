package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.MangaSource

/**
 * Marker interface for sources that support namespace-based tag filtering
 * (e.g. `artist:`, `character:`, `parody:`, etc.).
 *
 * Identical to Komikku's NamespaceSource interface.
 */
interface NamespaceSource : MangaSource
