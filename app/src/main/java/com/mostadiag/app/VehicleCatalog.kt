package com.mostadiag.app

import android.content.res.AssetManager
import org.json.JSONObject
import java.text.Normalizer
import java.util.Calendar
import java.util.Locale

/** Read-only name/year suggestions. Never used to select an OBD protocol or infer readings. */
class VehicleCatalog(private val assets: AssetManager) {
    data class Model(val name: String, val referencedYears: List<Int>)
    private data class Make(val name: String, val shard: Int)
    private val index = assets.open("vehicle-catalog/index.json").bufferedReader().use { JSONObject(it.readText()) }
    private val entries = linkedMapOf<String, Make>().apply {
        val rows = index.getJSONArray("makes")
        for (i in 0 until rows.length()) {
            val row = rows.getJSONArray(i)
            put(row.getString(0), Make(row.getString(1), row.getInt(2)))
        }
    }
    val makeCount: Int = index.getInt("make_count")
    val modelCount: Int = index.getInt("model_count")
    val makes: List<String> = entries.values.map { it.name } + "Autre marque"
    private val cachedModels = object : LinkedHashMap<String, List<Model>>(8, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Model>>?): Boolean = size > 8
    }

    fun canonicalMake(value: String): String? = entries[makeKey(value)]?.name

    fun models(make: String): List<Model> {
        val k = makeKey(make)
        cachedModels[k]?.let { return it }
        val entry = entries[k] ?: return emptyList()
        val filename = "vehicle-catalog/models-${entry.shard.toString().padStart(2, '0')}.json"
        val shard = assets.open(filename).bufferedReader().use { JSONObject(it.readText()) }
        val rows = shard.getJSONArray(k)
        val models = (0 until rows.length()).map { i ->
            val row = rows.getJSONArray(i)
            val years = row.getJSONArray(1)
            Model(row.getString(0), (0 until years.length()).map { years.getInt(it) })
        }
        cachedModels[k] = models
        return models
    }

    fun referencedYears(make: String, model: String): List<Int> =
        models(make).firstOrNull { normalize(it.name) == normalize(model) }?.referencedYears ?: emptyList()

    /** All calendar years remain available, including gaps in the source observations. */
    fun yearSuggestions(make: String, model: String, currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)): List<String> {
        val all = (currentYear + 1 downTo 1886).toList()
        val referenced = referencedYears(make, model).filter { it in 1886..currentYear + 1 }
        return (referenced + all).distinct().map { it.toString() }
    }

    companion object {
        fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
            .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")

        val aliases = mapOf(
            "vw" to "Volkswagen", "mercedes" to "Mercedes-Benz", "benz" to "Mercedes-Benz",
            "chevy" to "Chevrolet", "ds" to "DS Automobiles"
        )
        private fun makeKey(value: String): String {
            val normalized = normalize(value)
            return normalize(aliases[normalized] ?: value)
        }
    }
}
