package mu.location.savmed.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import mu.location.savmed.SavMed.Companion.corePreferences

//import mu.location.savmed.sip.services.SipService

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if(intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            println("Hello world, I'm booted up!")
            try {
                corePreferences.keepServiceAlive = true
                //context?.startService(Intent(context, SipService::class.java))
            } catch (e: Exception) {
                Log.e("Error start",e.toString())
            }
        }
    }
}