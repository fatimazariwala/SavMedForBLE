package mu.location.savmed.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import mu.location.savmed.SavMed.Companion.corePreferences

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if(intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            println("Hello world, I'm booted up!")
            try {
                corePreferences.keepServiceAlive = true
            } catch (e: Exception) {
                Log.e("Error start",e.toString())
            }
        }
    }
}