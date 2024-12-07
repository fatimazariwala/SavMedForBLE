package mu.location.savmed.ui.chat.chatNew.Adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.core.view.doOnPreDraw
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import mu.location.savmed.R
import mu.location.savmed.databinding.ChatListCellBinding
import mu.location.savmed.ui.chat.chatNew.model.ConversationModel
import mu.location.savmed.utils.Event
import mu.location.savmed.utils.startAnimatedDrawable

class ConversationsListAdapter : ListAdapter<ConversationModel, RecyclerView.ViewHolder?>(
    ChatRoomDiffCallback()
) {
    var selectedAdapterPosition = -1

    val conversationClickedEvent: MutableLiveData<Event<ConversationModel>> by lazy {
        MutableLiveData<Event<ConversationModel>>()
    }

    val conversationLongClickedEvent: MutableLiveData<Event<ConversationModel>> by lazy {
        MutableLiveData<Event<ConversationModel>>()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding: ChatListCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.chat_list_cell,
            parent,
            false
        )
        val viewHolder = ViewHolder(binding)
        binding.apply {
            Log.i("bInff","in view Treww")
            lifecycleOwner = parent.findViewTreeLifecycleOwner()
            Log.i("bInff","in view Treww-------")
            setOnClickListener {
                conversationClickedEvent.value = Event(model!!)
            }

            setOnLongClickListener {
                selectedAdapterPosition = viewHolder.adapterPosition
                root.isSelected = true
                conversationLongClickedEvent.value = Event(model!!)
                true
            }
        }
        return viewHolder
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as ViewHolder).bind(getItem(position))
    }

    fun resetSelection() {
        notifyItemChanged(selectedAdapterPosition)
        selectedAdapterPosition = -1
    }

    inner class ViewHolder(
        val binding: ChatListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(conversationModel: ConversationModel) {
            with(binding) {
                model = conversationModel

                binding.root.isSelected = adapterPosition == selectedAdapterPosition

                executePendingBindings()

                binding.root.doOnPreDraw {
                    binding.lastSentMessageStatus.startAnimatedDrawable()
                }
            }
        }
    }

    private class ChatRoomDiffCallback : DiffUtil.ItemCallback<ConversationModel>() {
        override fun areItemsTheSame(oldItem: ConversationModel, newItem: ConversationModel): Boolean {
            return oldItem.id == newItem.id && oldItem.lastUpdateTime == newItem.lastUpdateTime
        }

        override fun areContentsTheSame(oldItem: ConversationModel, newItem: ConversationModel): Boolean {
            return oldItem.chatRoom.peerAddress.username == newItem.chatRoom.peerAddress.username
        }
    }
}
