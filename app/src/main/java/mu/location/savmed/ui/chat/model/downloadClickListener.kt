package mu.location.savmed.ui.chat.model

import android.content.Context
import android.net.Uri

interface downloadClickListener {
    fun downloadImage(message: ChatMapper)
    fun openImageViewer(context: Context, imageUri: Uri)
}