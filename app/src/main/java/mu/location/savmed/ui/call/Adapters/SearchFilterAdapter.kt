package mu.location.savmed.ui.call.Adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import mu.location.savmed.databinding.SearchresultItemLayoutBinding
import org.linphone.core.SearchResult

class SearchResultAdapter(
    private val onCallClick: (String) -> Unit,
    private val onChatClick: (String) -> Unit
) : ListAdapter<SearchResult, SearchResultAdapter.SearchViewHolder>(ContactDiffCallback()) {

    companion object {
        const val TAG = "[Search Adapter]"
    }

    inner class SearchViewHolder(
        private val binding: SearchresultItemLayoutBinding
    ) : RecyclerView.ViewHolder(binding.root) {

//        init {
//            binding.root.setOnClickListener {
//                val position = bindingAdapterPosition
//                if (position != RecyclerView.NO_POSITION) {
//                    onContactClicked(getItem(position))
//                }
//            }
//        }

        fun bind(contact: SearchResult) {
            binding.apply {
                contactNameText.text =
                    "${contact.friend?.vcard?.givenName ?: contact.address?.username} ${contact.friend?.vcard?.familyName ?: ""}"
            }
            binding.callButton.setOnClickListener() {
                contact.address?.username?.let { it1 -> onCallClick(it1) }
            }
            binding.chatButton.setOnClickListener() {
                contact.address?.username?.let { it1 -> onChatClick(it1) }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val binding = SearchresultItemLayoutBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SearchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ContactDiffCallback : DiffUtil.ItemCallback<SearchResult>() {
        override fun areItemsTheSame(oldItem: SearchResult, newItem: SearchResult): Boolean {
            return  false //oldItem.address?.username == newItem.address?.username
        }

        override fun areContentsTheSame(oldItem: SearchResult, newItem: SearchResult): Boolean {
            return false
        }
    }
}
