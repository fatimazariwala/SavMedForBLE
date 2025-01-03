package mu.location.savmed.ui.call.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import mu.location.savmed.SavMed.Companion.bleServer
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.notifications.OnDestroyBroadCastReceiver
import mu.location.savmed.ui.locationing.DefaultLocationClient
import mu.location.savmed.utils.SharedPreference
import org.linphone.core.tools.Log

class CoreForeground : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "[Core Keep Alive Third Party Accounts Service]"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i("$TAG Created")

        val locationClient = DefaultLocationClient(
            applicationContext,
            LocationServices.getFusedLocationProviderClient(applicationContext)
        )

        SharedPreference.init(this)
//        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT,this) && hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE,this)) {
//
//            bluetoothLEController = AndroidBluetoothLEController(applicationContext)
//
//        } else {
//
//            Log.e(TAG, "Bluetooth permissions not granted.")
//
//        }

        locationClient
            .getLocationUpdates(10000L)
            .catch { e -> e.printStackTrace() }
            .onEach { location ->
                if (coreContext.onLocationEvent["latitude"] != location.latitude || coreContext.onLocationEvent["longitude"] != location.longitude) {
                    coreContext.onLocationEvent["latitude"] = location.latitude
                    coreContext.onLocationEvent["longitude"] = location.longitude
                    Log.i(TAG,"Updating Location Characteristics")
                    bleServer.updateLocCharacteristics(location.latitude,location.longitude)
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