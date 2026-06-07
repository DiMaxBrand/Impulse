package eu.siacs.conversations.ui.adapter

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter
import androidx.annotation.NonNull
import eu.siacs.conversations.entities.PresenceTemplate
import java.util.ArrayList
import java.util.Locale

class PresenceTemplateAdapter(
    context: Context,
    resource: Int,
    templates: List<PresenceTemplate>
) : ArrayAdapter<PresenceTemplate>(context, resource, ArrayList()) {

    private val templates: List<PresenceTemplate> = ArrayList(templates)

    private val filter = object : Filter() {

        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val results = FilterResults()
            if (constraint == null || constraint.isEmpty()) {
                results.values = ArrayList(this@PresenceTemplateAdapter.templates)
                results.count = this@PresenceTemplateAdapter.templates.size
            } else {
                val suggestions = ArrayList<PresenceTemplate>()
                val needle = constraint.toString().trim().lowercase(Locale.getDefault())
                for (template in this@PresenceTemplateAdapter.templates) {
                    val lc = template.getStatusMessage()?.lowercase(Locale.getDefault()) ?: ""
                    if (needle.isEmpty() || lc.contains(needle)) {
                        suggestions.add(template)
                    }
                }
                results.values = suggestions
                results.count = suggestions.size
            }
            return results
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults) {
            @Suppress("UNCHECKED_CAST")
            val filteredList = results.values as ArrayList<*>
            clear()
            for (c in filteredList) {
                add(c as PresenceTemplate)
            }
            notifyDataSetChanged()
        }
    }

    override fun getFilter(): Filter = this.filter
}
