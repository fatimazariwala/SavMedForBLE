package mu.location.savmed.ui.chat.model

import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import mu.location.savmed.R

class SentMessageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val messageText: TextView = itemView.findViewById(R.id.text_gchat_message_me)
    private val timeText: TextView = itemView.findViewById(R.id.text_gchat_timestamp_me)
   // private val image: ImageView = itemView.findViewById(R.id.image_gchat_message_me)

    fun bind(chatMapper: ChatMapper) {
        messageText.text = chatMapper.content?.utf8Text
        timeText.text = chatMapper.time.toString()
    }
}