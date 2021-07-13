package com.timejet.bio.timejet.ui.main

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import java.util.*

class AutoCompleteAdapter(
    context: Context,
    @LayoutRes resource: Int,
    @IdRes textViewResourceId: Int = 0,
    internal var items: List<Any> = listOf()
)
    : ArrayAdapter<Any>(context, resource, textViewResourceId, items) {


    internal var tempItems: MutableList<Any> = mutableListOf()
    internal var suggestions: MutableList<Any> = mutableListOf()

    /**
     * Custom Filter implementation for custom suggestions we provide.
     */
    private var filter: Filter = object : Filter() {

        override fun performFiltering(constraint: CharSequence?): FilterResults {
            return if (constraint != null) {
                suggestions.clear()
                tempItems.forEach {
                    if (it.toString().toLowerCase(Locale.getDefault()).contains(constraint.toString().toLowerCase(Locale.getDefault()))) {
                        suggestions.add(it)
                    }
                }

                val filterResults = FilterResults()
                filterResults.values = suggestions
                filterResults.count = suggestions.size
                filterResults
            } else {
                FilterResults()
            }
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults) {
            val filterList = results.values as? List<Any>
            if (results.count > 0) {
                clear()
                filterList?.forEach {
                    add(it)
                }.also {
                    notifyDataSetChanged()
                }
            }
        }
    }

    init {
        tempItems = items.toMutableList()
        suggestions = ArrayList()
    }

    override fun getFilter(): Filter {
        return filter
    }
}