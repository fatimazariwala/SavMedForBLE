package mu.location.savmed.bluetooth.bluetoothLE.BroadCast

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BluetoothBroadcastReceiver (
    private val onStateChanged: (isBluetoothON: Boolean) -> Unit
): BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.i("[Bluetooth BR]","Received Action ${intent?.action}")
        val action = intent?.action
        when(action) {
            BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED -> {
                val extra = intent.extras
                Log.i("[Bluetooth BR]","Got Extra Data From Connection Changed ${extra}")
            }
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val extra = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,-1)
                Log.i("[Bluetooth BR]","Got Extra Data Fir State Changes $extra")

                if (extra == 10) {
                    onStateChanged(false)
                }
            }
        }
    }

}