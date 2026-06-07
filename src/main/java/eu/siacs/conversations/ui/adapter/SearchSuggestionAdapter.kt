package eu.siacs.conversations.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import eu.siacs.conversations.R
import eu.siacs.conversations.databinding.ItemSearchSuggestionBinding
import im.conversations.android.model.SearchSuggestion
import java.util.Objects
import java.util.function.Consumer

class SearchSuggestionAdapter :
    ListAdapter<SearchSuggestion, SearchSuggestionAdapter.SearchSuggestionViewHolder>(DIFF) {

    private var onSearchSuggestionClicked: Consumer<SearchSuggestion>? = null

    fun setOnSearchSuggestionClicked(consumer: Consumer<SearchSuggestion>) {
        this.onSearchSuggestionClicked = consumer
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchSuggestionViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding: ItemSearchSuggestionBinding =
            DataBindingUtil.inflate(layoutInflater, R.layout.item_search_suggestion, parent, false)
        return SearchSuggestionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SearchSuggestionViewHolder, position: Int) {
        val searchSuggestion = getItem(position)
        holder.binding.wrapper.setOnClickListener {
            onSearchSuggestionClicked?.accept(searchSuggestion)
        }
        if (searchSuggestion is SearchSuggestion.Text) {
            holder.binding.searchSuggestion.maxLines = 2
            holder.binding.searchSuggestion.setText(
                holder.binding.searchSuggestion.resources
                    .getString(R.string.search_for_x_in_chats, searchSuggestion.text())
            )
            holder.binding.address.visibility = ViewGroup.GONE
            holder.binding.icon.setImageResource(R.drawable.ic_manage_search_24dp)
        } else if (searchSuggestion is SearchSuggestion.Contact) {
            holder.binding.searchSuggestion.maxLines = 1
            holder.binding.searchSuggestion.setText(searchSuggestion.name())
            holder.binding.address.setText(searchSuggestion.address().toString())
            holder.binding.address.visibility = View.VISIBLE
            holder.binding.icon.setImageResource(R.drawable.ic_person_24dp)
        } else if (searchSuggestion is SearchSuggestion.Bookmark) {
            holder.binding.searchSuggestion.maxLines = 1
            holder.binding.searchSuggestion.setText(searchSuggestion.name())
            holder.binding.address.setText(searchSuggestion.address().toString())
            holder.binding.address.visibility = View.VISIBLE
            holder.binding.icon.setImageResource(R.drawable.ic_group_24dp)
        } else if (searchSuggestion is SearchSuggestion.Uri) {
            val uri = searchSuggestion.uri()
            if (uri is de.gultsch.common.MiniUri.Xmpp) {
                holder.binding.searchSuggestion.maxLines = 2
                holder.binding.searchSuggestion.setText(uri.asJid())
                holder.binding.address.visibility = ViewGroup.GONE
                holder.binding.icon.setImageResource(R.drawable.ic_link_24dp)
            }
        }
    }

    class SearchSuggestionViewHolder(val binding: ItemSearchSuggestionBinding) :
        RecyclerView.ViewHolder(binding.root)

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SearchSuggestion>() {
            override fun areItemsTheSame(oldItem: SearchSuggestion, newItem: SearchSuggestion): Boolean {
                return if (oldItem is SearchSuggestion.Text && newItem is SearchSuggestion.Text) {
                    true
                } else {
                    Objects.equals(oldItem, newItem)
                }
            }

            override fun areContentsTheSame(oldItem: SearchSuggestion, newItem: SearchSuggestion): Boolean =
                Objects.equals(oldItem, newItem)
        }
    }
}
