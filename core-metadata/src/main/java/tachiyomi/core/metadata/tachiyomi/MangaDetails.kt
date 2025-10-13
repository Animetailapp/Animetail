package tachiyomi.core.metadata.tachiyomi

import eu.kanade.tachiyomi.animesource.model.Credit
import kotlinx.serialization.Serializable

@Serializable
class MangaDetails(
    val title: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genre: List<String>? = null,
    val status: Int? = null,
    val cast: List<Credit>? = null,
)
