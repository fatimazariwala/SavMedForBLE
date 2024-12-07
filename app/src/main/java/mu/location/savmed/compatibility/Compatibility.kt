package mu.location.savmed.compatibility

import android.app.Notification
import android.app.Service
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.annotation.RequiresApi
import org.linphone.mediastream.Version

class Compatibility {

    companion object {
        private const val TAG = "[Compatibility]"

        const val FOREGROUND_SERVICE_TYPE_DATA_SYNC =
            1 // ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        const val FOREGROUND_SERVICE_TYPE_PHONE_CALL =
            4 // ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        const val FOREGROUND_SERVICE_TYPE_CAMERA = 64 // ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        const val FOREGROUND_SERVICE_TYPE_MICROPHONE =
            128 // ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        const val FOREGROUND_SERVICE_TYPE_SPECIAL_USE =
            1073741824 // ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE

        @RequiresApi(Build.VERSION_CODES.Q)
        fun startServiceForeground(
            service: Service,
            id: Int,
            notification: Notification,
            foregroundServiceType: Int
        ) {
            if (Version.sdkAboveOrEqual(Version.API34_ANDROID_14_UPSIDE_DOWN_CAKE)) {
                Api34Compatibility.startServiceForeground(
                    service,
                    id,
                    notification,
                    foregroundServiceType
                )
            } else {
                Api28Compatibility.startServiceForeground(service, id, notification)
            }
        }

        fun hasFullScreenIntentPermission(context: Context): Boolean {
            if (Version.sdkAboveOrEqual(Version.API34_ANDROID_14_UPSIDE_DOWN_CAKE)) {
                return Api34Compatibility.hasFullScreenIntentPermission(context)
            }
            return true
        }

        fun requestFullScreenIntentPermission(context: Context): Boolean {
            if (Version.sdkAboveOrEqual(Version.API34_ANDROID_14_UPSIDE_DOWN_CAKE)) {
                Api34Compatibility.requestFullScreenIntentPermission(context)
                return true
            }
            return false
        }


        fun getRecordingsDirectory(): String {
            @RequiresApi(Build.VERSION_CODES.S)
            if (Version.sdkAboveOrEqual(Version.API31_ANDROID_12)) {
                return Api31Compatibility.getRecordingsDirectory()
            }
            return Environment.DIRECTORY_PODCASTS
        }

        fun getMediaCollectionUri(
            isImage: Boolean = false,
            isVideo: Boolean = false,
            isAudio: Boolean = false
        ): Uri {
            return if (Version.sdkAboveOrEqual(Version.API29_ANDROID_10)) {
                Api29Compatibility.getMediaCollectionUri(isImage, isVideo, isAudio)
            } else {
                Api28Compatibility.getMediaCollectionUri(isImage, isVideo, isAudio)
            }
        }
    }
}