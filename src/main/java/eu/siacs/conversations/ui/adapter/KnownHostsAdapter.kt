package eu.siacs.conversations.ui.adapter

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter
import com.google.common.collect.ImmutableList
import com.google.common.collect.Ordering
import eu.siacs.conversations.Config
import java.util.ArrayList
import java.util.Locale
import java.util.regex.Pattern

class KnownHostsAdapter : ArrayAdapter<String> {

    private var domains: List<String>

    private val domainFilter = object : Filter() {

        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val builder = ImmutableList.Builder<String>()
            val split = if (constraint == null) emptyArray() else constraint.toString().split("@").toTypedArray()
            if (split.size == 1) {
                val local = split[0].lowercase(Locale.ENGLISH)
                if (Config.QUICKSY_DOMAIN != null && E164_PATTERN.matcher(local).matches()) {
                    builder.add("$local@${Config.QUICKSY_DOMAIN}")
                } else {
                    for (domain in domains) {
                        builder.add("$local@$domain")
                    }
                }
            } else if (split.size == 2) {
                val localPart = split[0].lowercase(Locale.ENGLISH)
                val domainPart = split[1].lowercase(Locale.ENGLISH)
                if (domains.contains(domainPart)) {
                    return FilterResults()
                }
                for (domain in domains) {
                    if (domain.contains(domainPart)) {
                        builder.add("$localPart@$domain")
                    }
                }
            } else {
                return FilterResults()
            }
            val suggestions = builder.build()
            val filterResults = FilterResults()
            filterResults.values = suggestions
            filterResults.count = suggestions.size
            return filterResults
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults) {
            val suggestions = ImmutableList.Builder<String>()
            if (results.values is Collection<*>) {
                for (item in results.values as Collection<*>) {
                    if (item is String) {
                        suggestions.add(item)
                    }
                }
            }
            clear()
            addAll(suggestions.build())
            notifyDataSetChanged()
        }
    }

    constructor(context: Context, viewResourceId: Int, knownHosts: Collection<String>) :
            super(context, viewResourceId, ArrayList()) {
        domains = Ordering.natural<String>().sortedCopy(knownHosts)
    }

    constructor(context: Context, viewResourceId: Int) :
            super(context, viewResourceId, ArrayList()) {
        domains = ImmutableList.of()
    }

    fun refresh(knownHosts: Collection<String>) {
        this.domains = Ordering.natural<String>().sortedCopy(knownHosts)
        notifyDataSetChanged()
    }

    override fun getFilter(): Filter = domainFilter

    companion object {
        private val E164_PATTERN: Pattern = Pattern.compile("^\\+[1-9]\\d{1,14}$")
    }
}
