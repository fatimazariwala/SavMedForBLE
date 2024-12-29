package mu.location.savmed.ui.chat.Adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import mu.location.savmed.R
import mu.location.savmed.databinding.ChatBubbleIncomingBinding
import mu.location.savmed.databinding.ChatBubbleOutgoingBinding
import mu.location.savmed.ui.chat.model.EventLogModel
import mu.location.savmed.ui.chat.model.MessageModel
import mu.location.savmed.utils.Event
import mu.location.savmed.utils.startAnimatedDrawable

class ConversationEventAdapter :
    ListAdapter<EventLogModel, RecyclerView.ViewHolder>(
        EventLogDiffCallback()
    ) {

    companion object {
        private const val TAG = "[Conversation Event Adapter]"

        const val INCOMING_CHAT_MESSAGE = 1
        const val OUTGOING_CHAT_MESSAGE = 2
    }

    val chatMessageLongPressEvent = MutableLiveData<Event<MessageModel>>()

    val showDeliveryForChatMessageModelEvent: MutableLiveData<Event<MessageModel>> by lazy {
        MutableLiveData<Event<MessageModel>>()
    }

    val showReactionForChatMessageModelEvent: MutableLiveData<Event<MessageModel>> by lazy {
        MutableLiveData<Event<MessageModel>>()
    }

    val scrollToRepliedMessageEvent: MutableLiveData<Event<MessageModel>> by lazy {
        MutableLiveData<Event<MessageModel>>()
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        Log.i(TAG,"I am binding...")
        val eventLog = getItem(position)
       if (holder is IncomingChatBubbleViewHolder) {
           holder.bind(eventLog.model as MessageModel)
       } else if(holder is OutgoingChatBubbleViewHolder){
           holder.bind(eventLog.model as MessageModel)
       }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        Log.i(TAG,"I am creating...")
        if (viewType == INCOMING_CHAT_MESSAGE) {
            return createIncomingChatBubble(parent)
        } else {
            return createOutgoingChatBubble(parent)
        }
    }

    inner class IncomingChatBubbleViewHolder(
        val binding: ChatBubbleIncomingBinding
    ): RecyclerView.ViewHolder(binding.root) {
        fun bind(message: MessageModel) {
            with(binding) {
                model = message
                executePendingBindings()
            }
        }
    }

    inner class OutgoingChatBubbleViewHolder(
        val binding: ChatBubbleOutgoingBinding
    ): RecyclerView.ViewHolder(binding.root) {
        fun bind(message: MessageModel) {
            with(binding) {
                model = message
                executePendingBindings()

                root.doOnPreDraw {
                    binding.deliveryStatus.startAnimatedDrawable()
                }
            }
        }
    }

    private fun createIncomingChatBubble(parent: ViewGroup): IncomingChatBubbleViewHolder {
        val binding: ChatBubbleIncomingBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.chat_bubble_incoming,
            parent,
            false
        )
        val viewHolder = IncomingChatBubbleViewHolder(binding)
        binding.apply {
            lifecycleOwner = parent.findViewTreeLifecycleOwner()

            setOnLongClickListener {
                chatMessageLongPressEvent.value = Event(model!!)
                true
            }
            setShowReactionInfoClickListener {
                showReactionForChatMessageModelEvent.value = Event(model!!)
            }
            setScrollToRepliedMessageClickListener {
                scrollToRepliedMessageEvent.value = Event(model!!)
            }
        }
        return viewHolder
    }

    private fun createOutgoingChatBubble(parent: ViewGroup): OutgoingChatBubbleViewHolder {
        val binding: ChatBubbleOutgoingBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.chat_bubble_outgoing,
            parent,
            false
        )
        val viewHolder = OutgoingChatBubbleViewHolder(binding)
        binding.apply {
            lifecycleOwner = parent.findViewTreeLifecycleOwner()

            setOnLongClickListener {
                chatMessageLongPressEvent.value = Event(model!!)
                true
            }

            setShowDeliveryInfoClickListener {
                showDeliveryForChatMessageModelEvent.value = Event(model!!)
            }
            setShowReactionInfoClickListener {
                showReactionForChatMessageModelEvent.value = Event(model!!)
            }
            setScrollToRepliedMessageClickListener {
                scrollToRepliedMessageEvent.value = Event(model!!)
            }
        }
        return viewHolder
    }

    fun getFirstUnreadMessagePosition(): Int {
        var index = 0
        for (eventLog in currentList) {
            if (eventLog.model is MessageModel) {
                if (!eventLog.model.isRead) {
                    Log.i(TAG,"First unread message is [${eventLog.model.id}] at index [$index]")
                    return index
                }
            }
            index += 1
        }
        Log.i(TAG,"No unread message found in list of [${currentList.size}] events")
        return -1
    }

    override fun getItemViewType(position: Int): Int {
        val data = getItem(position)

        if ((data.model as MessageModel).isOutgoing) {
            Log.i(TAG,"Outgoing message----------------------------")
            return OUTGOING_CHAT_MESSAGE
        } else {
            Log.i(TAG,"Incoming message----------------------------")
        }
        return INCOMING_CHAT_MESSAGE
    }

    private class EventLogDiffCallback : DiffUtil.ItemCallback<EventLogModel>() {
        override fun areItemsTheSame(oldItem: EventLogModel, newItem: EventLogModel): Boolean {

            return oldItem.notifyId == newItem.notifyId
        }

        override fun areContentsTheSame(oldItem: EventLogModel, newItem: EventLogModel): Boolean {
            val oldModel = (oldItem.model as MessageModel)
            val newModel = (newItem.model as MessageModel)
            return  newModel.isRead &&
                    oldModel.groupedWithNextMessage.value == newModel.groupedWithNextMessage.value &&
                    oldModel.groupedWithPreviousMessage.value == newModel.groupedWithPreviousMessage.value

        }
    }
}