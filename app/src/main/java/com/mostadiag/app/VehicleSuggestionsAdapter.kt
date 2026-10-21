package com.mostadiag.app

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView

/** Filtering runs on Android's filter worker; accents, spaces and punctuation are ignored. */
class VehicleSuggestionsAdapter(
    private val context: Context,
    options: List<String>,
    aliases: Map<String, String> = emptyMap()
) : BaseAdapter(), Filterable {
    private val all = options.distinct()
    private val keys = all.map { option ->
        listOf(VehicleCatalog.normalize(option)) + aliases.filterValues { it == option }.keys
    }
    private var visible = all
    override fun getCount() = visible.size
    override fun getItem(position: Int): String = visible[position]
    override fun getItemId(position: Int) = position.toLong()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
        ((convertView as? TextView) ?: TextView(context).apply {
            val density = resources.displayMetrics.density
            setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
            minHeight = (48 * density).toInt()
            textSize = 16f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.rgb(13, 24, 39))
        }).apply { text = getItem(position) }

    private val suggestionsFilter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val query = VehicleCatalog.normalize(constraint?.toString() ?: "")
            val matches = if (query.isEmpty()) all else all.filterIndexed { i, _ -> keys[i].any { it.contains(query) } }
            return FilterResults().apply { values = matches; count = matches.size }
        }
        override fun publishResults(constraint: CharSequence?, results: FilterResults) {
            @Suppress("UNCHECKED_CAST")
            visible = results.values as List<String>
            notifyDataSetChanged()
        }
        override fun convertResultToString(resultValue: Any?): CharSequence = resultValue?.toString() ?: ""
    }
    override fun getFilter(): Filter = suggestionsFilter
}
