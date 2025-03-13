package mu.location.savmed.ui.call.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import mu.location.savmed.SavMed.Companion.bleServer
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.models.ScheduleBackgroundTask
import mu.location.savmed.notifications.OnDestroyBroadCastReceiver
import mu.location.savmed.ui.locationing.DefaultLocationClient
import mu.location.savmed.utils.SharedPreference
import org.linphone.core.tools.Log
import java.util.concurrent.TimeUnit

class CoreForeground : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val latLonHashMap: HashMap<String,Double> = HashMap()

    companion object {
        private const val TAG = "[Core Keep Alive Third Party Accounts Service]"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i("$TAG Created")

        val periodicStart = PeriodicWorkRequestBuilder<ScheduleBackgroundTask>(
            15,TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "StartService",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicStart
        )

        val locationClient = DefaultLocationClient(
            applicationContext,
            LocationServices.getFusedLocationProviderClient(applicationContext)
        )

        SharedPreference.init(this)

        locationClient
            .getLocationUpdates(10000L)
            .catch { e -> e.printStackTrace() }
            .onEach { location ->
                if (coreContext.onLocationEvent.value?.get("latitude") != location.latitude || coreContext.onLocationEvent.value?.get("longitude") != location.longitude) {
                    latLonHashMap["latitude"] = location.latitude
                    latLonHashMap["longitude"] = location.longitude

                    coreContext.onLocationEvent.postValue(latLonHashMap)

                    Log.i(TAG,"Updating Location Characteristics")
                   // bleServer.updateLocCharacteristics(location.latitude,location.longitude)
                }
            }
            .launchIn(serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("$TAG onStartCommand")
        coreContext.notificationManager.onKeepAliveServiceStarted(this)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i("$TAG Task removed, doing nothing")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.i("$TAG onDestroy")
        coreContext.notificationManager.onKeepAliveServiceDestroyed()
        super.onDestroy()
        val i = Intent(this, OnDestroyBroadCastReceiver::class.java)
        sendBroadcast(i)
    }

    override fun onBind(intent: Intent): IBinder ?= null
}