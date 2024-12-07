package mu.location.savmed.ui.chat.model

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import mu.location.savmed.databinding.ChatIncomingBinding
import mu.location.savmed.databinding.ChatOutgoingBinding
import mu.location.savmed.ui.chat.ChatTestActivity
import java.io.File

class MessageListAdapter(
    private val context: Context,
    private val messageList: List<ChatMapper>,
    private val clickListener: ChatTestActivity
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(){

    companion object {
        private const val VIEW_TYPE_MESSAGE_SENT = 1
        private const val VIEW_TYPE_MESSAGE_RECEIVED = 2
    }

    override fun getItemCount(): Int {
        return messageList.size
    }

    override fun getItemViewType(position: Int): Int {
        val message = messageList[position]
        return if (message.isOutgoing) {
            VIEW_TYPE_MESSAGE_SENT
        } else {
            VIEW_TYPE_MESSAGE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_MESSAGE_SENT -> {
                val binding = ChatOutgoingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                SentMessageHolder(binding)
            }
            VIEW_TYPE_MESSAGE_RECEIVED -> {
                val binding = ChatIncomingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                ReceivedMessageHolder(binding,)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messageList[position]
        when (holder) {
            is SentMessageHolder -> holder.bind(message)
            is ReceivedMessageHolder -> holder.bind(message, context)
        }
    }

    inner class SentMessageHolder(private val binding: ChatOutgoingBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMapper) {
            // Bind your data to the views here using binding
            if(message.content?.isFile == true) {
                binding.textGchatMessageMe.visibility = View.GONE
                binding.imageGchatMessageMe.visibility = View.VISIBLE
                binding.imageGchatMessageMe.setImageBitmap(BitmapFactory.decodeFile(message.content.filePath))
                Log.i("Message ADap","${message.content.filePath}")

                binding.imageGchatMessageMe.setOnClickListener() {
                    val file = message.content.filePath?.let { path -> File(path) }

                    // Get the Uri using FileProvider
                    val imageUri: Uri = file?.let {
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
                    } ?: return@setOnClickListener

                    clickListener.openImageViewer(context, imageUri)
                }
            }
            if(message.content?.isText == true) {
                binding.imageGchatMessageMe.visibility = View.GONE
                binding.textGchatMessageMe.text = message.content?.utf8Text
            }// Assuming message has a 'message' property

            binding.status.text = message.state
            binding.textGchatTimestampMe.text = message.time.toString() // Adjust accordingly
        }
    }

    inner class ReceivedMessageHolder(private val binding: ChatIncomingBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMapper, context: Context) {
            // Bind your data to the views here using binding

            if(message.button == true) {
                binding.imageGchatDownloadOther.visibility = View.VISIBLE
                binding.imageGchatMessageOther.visibility = View.GONE
                binding.textGchatMessageOther.visibility = View.GONE

                binding.imageGchatDownloadOther.setOnClickListener() {
                    Log.i("Message ADap","Button state: ${message.button}")
                    clickListener.downloadImage(message)
                }
            }
            if (message.button != true) {
                if (message.content?.isFile == true) {
                    binding.imageGchatMessageOther.visibility = View.VISIBLE
                    binding.textGchatMessageOther.visibility = View.GONE
                    binding.imageGchatDownloadOther.visibility = View.GONE

                    binding.imageGchatMessageOther.setImageBitmap(BitmapFactory.decodeFile(message.content.filePath))

                    binding.imageGchatMessageOther.setOnClickListener() {
                        val file = message.content.filePath?.let { path -> File(path) }

                        // Get the Uri using FileProvider
                        val imageUri: Uri = file?.let {
                            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
                        } ?: return@setOnClickListener

                        clickListener.openImageViewer(context, imageUri)
                    }
                }
            }
            if(message.content?.isText == true) {
                binding.imageGchatMessageOther.visibility = View.GONE
                binding.textGchatMessageOther.visibility = View.VISIBLE
                binding.imageGchatDownloadOther.visibility = View.GONE

                binding.textGchatMessageOther.text = message.content?.utf8Text
            }
            binding.textGchatTimestampOther.text = message.time.toString()
            binding.textGchatUserOther.text = message.userName // Assuming a sender property exists
        }
    }

}
