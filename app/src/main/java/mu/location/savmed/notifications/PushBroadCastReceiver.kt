package mu.location.savmed.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import mu.location.savmed.SavMed.Companion.corePreferences

class PushBroadCastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("PushBroadCast--","Push Received")
        try {
            corePreferences.keepServiceAlive = true
        } catch (e: Exception) {
            Log.e("Error start",e.toString())
        }
    }
}