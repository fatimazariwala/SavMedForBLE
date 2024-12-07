package mu.location.savmed.bluetooth.bluetoothLE.BroadCast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import mu.location.savmed.bluetooth.bluetoothLE.controls.AndroidBluetoothLEController

class GattStateReceiver: BroadcastReceiver() {

    companion object {
        const val TAG = "[Gatt Receiver]"
    }

    override fun onReceive(context: Context?, intent: Intent?) {

        when (intent?.action) {
            AndroidBluetoothLEController.ACTION_GATT_CONNECTED -> {

                Toast.makeText(
                    context,
                    "BLE Device Connected!",
                    Toast.LENGTH_SHORT
                ).show()

            }
            AndroidBluetoothLEController.ACTION_GATT_DISCONNECTED -> {

                Toast.makeText(
                    context,
                    "BLE Device DisConnected!",
                    Toast.LENGTH_SHORT
                ).show()
                // updateConnectionState(R.string.disconnected)
            }
            AndroidBluetoothLEController.ACTION_GAT_SERVICES_DISCOVERED -> {
                Toast.makeText(
                    context,
                    "BLE Device DisConnected!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}