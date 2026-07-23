package xyz.nulldev.ts.api.http.serializer

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import logcat.LogPriority
import logcat.asLog
import tachiyomi.core.common.util.system.logcat
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.isSubclassOf
import eu.kanade.tachiyomi.animesource.model.AnimeFilter as Filter

class FilterSerializer {
    private val serializers = listOf<Serializer<*>>(
        // SY -->
        AutoCompleteSerializer(this),
        // SY <--
        HeaderSerializer(this),
        SeparatorSerializer(this),
        SelectSerializer(this),
        TextSerializer(this),
        CheckboxSerializer(this),
        TriStateSerializer(this),
        GroupSerializer(this),
        SortSerializer(this),
    )

    fun serialize(filters: AnimeFilterList) = buildJsonArray {
        filters.filterIsInstance<Filter<Any?>>().forEach {
            add(serialize(it))
        }
    }

    fun serialize(filter: Filter<Any?>): JsonObject {
        return serializers
            .filterIsInstance<Serializer<Filter<Any?>>>()
            .firstOrNull {
                filter::class.isSubclassOf(it.clazz)
            }?.let { serializer ->
                buildJsonObject {
                    with(serializer) { serialize(filter) }

                    val classMappings = mutableListOf<Pair<String, Any>>()

                    serializer.mappings().forEach {
                        val res = it.second.get(filter)
                        put(it.first, res.toString())
                        classMappings += it.first to (res?.javaClass?.name ?: "null")
                    }

                    putJsonObject(CLASS_MAPPINGS) {
                        classMappings.forEach { (t, u) ->
                            put(t, u.toString())
                        }
                    }

                    put(TYPE, serializer.type)
                }
            } ?: throw IllegalArgumentException("Cannot serialize this Filter object!")
    }

    fun deserialize(filters: AnimeFilterList, json: JsonArray) {
        filters.filterIsInstance<Filter<Any?>>().zip(json).forEach { (filter, obj) ->
            // KMK -->
            try {
                // KMK <--
                deserialize(filter, obj.jsonObject)
                // KMK -->
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { e.asLog() }
            }
            // KMK <--
        }
    }

    fun deserialize(filter: Filter<Any?>, json: JsonObject) {
        val serializer = serializers
            .filterIsInstance<Serializer<Filter<Any?>>>()
            .firstOrNull {
                it.type == json[TYPE]!!.jsonPrimitive.content
            } ?: throw IllegalArgumentException("Cannot deserialize this type!")

        serializer.deserialize(json, filter)

        serializer.mappings().forEach {
            if (it.second is KMutableProperty1) {
                val obj = json[it.first]!!.jsonPrimitive
                val res: Any? = when (json[CLASS_MAPPINGS]!!.jsonObject[it.first]!!.jsonPrimitive.content) {
                    Int::class.java.name -> obj.int
                    Long::class.java.name -> obj.long
                    Float::class.java.name -> obj.float
                    Double::class.java.name -> obj.double
                    String::class.java.name -> obj.content
                    Boolean::class.java.name -> obj.boolean
                    Byte::class.java.name -> obj.content.toByte()
                    Short::class.java.name -> obj.content.toShort()
                    Char::class.java.name -> obj.content[0]
                    "null" -> null
                    else -> throw IllegalArgumentException("Cannot deserialize this type!")
                }
                @Suppress("UNCHECKED_CAST")
                (it.second as KMutableProperty1<in Filter<Any?>, in Any?>).set(filter, res)
            }
        }
    }

    companion object {
        const val TYPE = "_type"
        const val CLASS_MAPPINGS = "_cmaps"
    }
}
