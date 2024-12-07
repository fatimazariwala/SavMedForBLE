package mu.location.savmed.compatibility

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.View
import android.view.contentcapture.ContentCaptureContext
import android.view.contentcapture.ContentCaptureSession
import androidx.annotation.RequiresApi
import mu.location.savmed.utils.SavMedUtils

class Api29Compatibility {
    companion object {
        private const val TAG = "[API 29 Compatibility]"

        fun getMediaCollectionUri(isImage: Boolean, isVideo: Boolean, isAudio: Boolean): Uri {
            return when {
                isImage -> {
                    MediaStore.Images.Media.getContentUri(
                        MediaStore.VOLUME_EXTERNAL_PRIMARY
                    )
                }

                isVideo -> {
                    MediaStore.Video.Media.getContentUri(
                        MediaStore.VOLUME_EXTERNAL_PRIMARY
                    )
                }

                isAudio -> {
                    MediaStore.Audio.Media.getContentUri(
                        MediaStore.VOLUME_EXTERNAL_PRIMARY
                    )
                }

                else -> Uri.EMPTY
            }
        }

        fun extractLocusIdFromIntent(intent: Intent): String? {
            return intent.getStringExtra(Intent.EXTRA_LOCUS_ID)
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        fun setLocusIdInContentCaptureSession(root: View, localSipUri: String, remoteSipUri: String) {
            val session: ContentCaptureSession? = root.contentCaptureSession
            if (session != null) {
                val id = SavMedUtils.getChatRoomId(localSipUri, remoteSipUri)
                session.contentCaptureContext = ContentCaptureContext.forLocusId(id)
            }
        }
    }
}