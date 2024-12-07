package mu.location.savmed.compatibility

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import org.linphone.core.tools.Log

class Api34Compatibility {
    companion object {
        private const val TAG = "[API 34 Compatibility]"

        @RequiresApi(Build.VERSION_CODES.Q)
        fun startServiceForeground(
            service: Service,
            id: Int,
            notification: Notification,
            foregroundServiceType: Int
        ) {
            try {
                service.startForeground(
                    id,
                    notification,
                    foregroundServiceType
                )
            } catch (e: Exception) {
                Log.e("$TAG Can't start service as foreground! $e")
            }
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        fun hasFullScreenIntentPermission(context: Context): Boolean {
            val notificationManager = context.getSystemService(NotificationManager::class.java) as NotificationManager
            // See https://developer.android.com/reference/android/app/NotificationManager#canUseFullScreenIntent%28%29
            val granted = notificationManager.canUseFullScreenIntent()
            if (granted) {
                Log.i("$TAG Full screen intent permission is granted")
            } else {
                Log.w("$TAG Full screen intent permission isn't granted yet!")
            }
            return granted
        }

        fun requestFullScreenIntentPermission(context: Context) {
            val intent = Intent()
            // See https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT
            intent.action = Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT
            intent.data = Uri.parse("package:${context.packageName}")
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            Log.i("$TAG Starting ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT")
            ContextCompat.startActivity(context, intent, null)
        }

    }
}