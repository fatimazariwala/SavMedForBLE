package mu.location.savmed.compatibility

import android.app.Notification
import android.app.Service
import android.net.Uri
import android.provider.MediaStore
import org.linphone.core.tools.Log

class Api28Compatibility {
    companion object {
        private const val TAG = "[API 28 Compatibility]"

        fun startServiceForeground(service: Service, id: Int, notification: Notification) {
            try {
                service.startForeground(
                    id,
                    notification
                )
            } catch (e: Exception) {
                Log.e("$TAG Can't start service as foreground! $e")
            }
        }


        fun getMediaCollectionUri(isImage: Boolean, isVideo: Boolean, isAudio: Boolean): Uri {
            return when {
                isImage -> {
                    MediaStore.Images.Media.getContentUri("external")
                }
                isVideo -> {
                    MediaStore.Video.Media.getContentUri("external")
                }
                isAudio -> {
                    MediaStore.Audio.Media.getContentUri("external")
                }
                else -> Uri.EMPTY
            }
        }
    }
}