package mu.location.savmed.ui.chat.model

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import mu.location.savmed.R
import org.linphone.core.Content

class ReceivedMessageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val messageText: TextView = itemView.findViewById(R.id.text_gchat_user_other)
    private val timeText: TextView = itemView.findViewById(R.id.text_gchat_timestamp_other)
    private val nameText: TextView = itemView.findViewById(R.id.text_gchat_message_other)
    private val profileImage: ImageView = itemView.findViewById(R.id.image_gchat_profile_other)

    fun bind(chatMapper: ChatMapper, context: Context) {
        messageText.text = chatMapper.content?.utf8Text
        timeText.text = chatMapper.time.toString()
        nameText.text = chatMapper.userName
    }
}
