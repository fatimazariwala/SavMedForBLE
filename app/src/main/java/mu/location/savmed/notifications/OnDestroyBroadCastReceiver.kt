package mu.location.savmed.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.SavMed.Companion.corePreferences
import mu.location.savmed.ui.call.services.CoreForeground

//import mu.location.savmed.sip.services.SipService

class OnDestroyBroadCastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.i(
            "mmm",//SipService::class.java.getSimpleName(),
            "Service Stops! Oooooooooooooppppssssss!!!!"
        )
        try {
            corePreferences.keepServiceAlive = true
            //context?.startService(Intent(context, CoreForeground::class.java))
        } catch (e: Exception) {
            Log.e(" start",e.toString())
        }

    }

}