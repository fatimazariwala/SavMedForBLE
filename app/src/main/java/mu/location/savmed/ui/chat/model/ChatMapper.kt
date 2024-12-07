package mu.location.savmed.ui.chat.model

import android.widget.Button
import android.widget.ImageView
import org.linphone.core.Content

data class ChatMapper(
    val id: String ?= null,
    val userName: String = "",
    val isOutgoing: Boolean = false,
    val button: Boolean ?= false,
    val imageView: ImageView ?= null,
    val content: Content?,
    val time: String,
    var state: String = ""
)