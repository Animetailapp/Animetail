package xyz.nulldev.ts.api.http.serializer

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilter.TriState.Companion.STATE_EXCLUDE
import eu.kanade.tachiyomi.animesource.model.AnimeFilter.TriState.Companion.STATE_IGNORE
import eu.kanade.tachiyomi.animesource.model.AnimeFilter.TriState.Companion.STATE_INCLUDE
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Saved searches and feed filters are stored as JSON and restored into a fresh filter list from the
 * source. Every filter state has to survive that round trip, otherwise browse silently searches
 * with the source's default filters.
 */
class FilterSerializerTest {

    private val serializer = FilterSerializer()

    private class Box(name: String, state: Boolean = false) : AnimeFilter.CheckBox(name, state)
    private class Group(boxes: List<Box>) : AnimeFilter.Group<Box>("Categories", boxes)
    private class Match(state: Int = 0) : AnimeFilter.Select<String>("Match", arrayOf("Any", "All", "None"), state)
    private class Tri(state: Int = STATE_IGNORE) : AnimeFilter.TriState("Genre", state)
    private class Query : AnimeFilter.Text("Year")
    private class Order : AnimeFilter.Sort("Order", arrayOf("Latest", "Popular"), Selection(0, false))
    private class Tags : AnimeFilter.AutoComplete("Tags", "", emptyList(), state = emptyList())
    private class Title : AnimeFilter.Header("Title")
    private class Line : AnimeFilter.Separator()

    private fun filters(defaultChecked: Boolean = false) = AnimeFilterList(
        Title(),
        Group(listOf(Box("English Subtitle", defaultChecked), Box("Reduce Mosaic", defaultChecked))),
        Line(),
        Match(),
        Tri(),
        Query(),
        Order(),
        Tags(),
    )

    private fun roundTrip(picked: AnimeFilterList, restored: AnimeFilterList): AnimeFilterList {
        val json = Json.encodeToString(serializer.serialize(picked))
        serializer.deserialize(restored, Json.decodeFromString<JsonArray>(json))
        return restored
    }

    @Test
    fun `restores every filter state through JSON`() {
        val picked = filters().apply {
            (this[1] as Group).state[1].state = true
            (this[3] as Match).state = 2
            (this[4] as Tri).state = STATE_EXCLUDE
            (this[5] as Query).state = "2023"
            (this[6] as Order).state = AnimeFilter.Sort.Selection(1, true)
            (this[7] as Tags).state = listOf("action", "drama")
        }

        val restored = roundTrip(picked, filters())

        assertEquals(listOf(false, true), (restored[1] as Group).state.map { it.state })
        assertEquals(2, (restored[3] as Match).state)
        assertEquals(STATE_EXCLUDE, (restored[4] as Tri).state)
        assertEquals("2023", (restored[5] as Query).state)
        assertEquals(AnimeFilter.Sort.Selection(1, true), (restored[6] as Order).state)
        assertEquals(listOf("action", "drama"), (restored[7] as Tags).state)
    }

    @Test
    fun `restores unchecked boxes over a checked default`() {
        val restored = roundTrip(filters(defaultChecked = false), filters(defaultChecked = true))

        assertEquals(listOf(false, false), (restored[1] as Group).state.map { it.state })
    }

    @Test
    fun `restores the first option over a different default`() {
        val restored = roundTrip(AnimeFilterList(Match(state = 0)), AnimeFilterList(Match(state = 2)))

        assertEquals(0, (restored[0] as Match).state)
    }

    @ParameterizedTest
    @ValueSource(ints = [STATE_IGNORE, STATE_INCLUDE, STATE_EXCLUDE])
    fun `restores each tri-state value`(state: Int) {
        val other = (state + 1) % 3
        val restored = roundTrip(AnimeFilterList(Tri(state)), AnimeFilterList(Tri(other)))

        assertEquals(state, (restored[0] as Tri).state)
    }

    @Test
    fun `restores an empty sort selection`() {
        val picked = AnimeFilterList(Order().apply { state = null })

        val restored = roundTrip(picked, AnimeFilterList(Order()))

        assertNull((restored[0] as Order).state)
    }

    @Test
    fun `accepts primitive class names`() {
        val box = serializer.serialize(AnimeFilterList(Box("Box", state = true)))[0].jsonObject
        val classMappings = JsonObject(
            mapOf(
                CheckboxSerializer.NAME to JsonPrimitive("java.lang.String"),
                CheckboxSerializer.STATE to JsonPrimitive("boolean"),
            ),
        )
        val withPrimitiveName = JsonArray(listOf(JsonObject(box + (FilterSerializer.CLASS_MAPPINGS to classMappings))))
        val restored = AnimeFilterList(Box("Box"))

        serializer.deserialize(restored, withPrimitiveName)

        assertEquals(true, (restored[0] as Box).state)
    }

    @Test
    fun `a filter that fails to restore does not stop the others`() {
        val json = serializer.serialize(AnimeFilterList(Box("Box", state = true), Tri(STATE_INCLUDE)))
        val unknownType = JsonObject(mapOf(FilterSerializer.TYPE to JsonPrimitive("UNKNOWN")))
        val broken = JsonArray(listOf(unknownType, json[1]))
        val restored = AnimeFilterList(Box("Box"), Tri())

        serializer.deserialize(restored, broken)

        assertEquals(false, (restored[0] as Box).state)
        assertEquals(STATE_INCLUDE, (restored[1] as Tri).state)
    }
}
