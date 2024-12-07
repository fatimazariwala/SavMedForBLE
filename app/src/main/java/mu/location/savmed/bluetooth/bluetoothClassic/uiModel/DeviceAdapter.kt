package mu.location.savmed.bluetooth.bluetoothClassic.uiModel

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import mu.location.savmed.bluetooth.bluetoothClassic.models.BluetoothDeviceLocal

class DeviceAdapter(
    context: Context,
    private val devices: List<BluetoothDeviceLocal>,
    private val onClickListener: (BluetoothDeviceLocal) -> Unit
) : ArrayAdapter<BluetoothDeviceLocal>(context, 0, devices) {

    init {
        Log.i("[Adapter]", "Devices list size: ${devices.size}")
        if (devices.isEmpty()) {
            Log.w("[Adapter]", "Devices list is empty")
        }
    }

    override fun getView(
        position: Int, convertView: View?, parent: ViewGroup
    ): View {
        Log.i("[Adapter]","adap")
        val view = convertView ?: LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, parent, false)
        val device = devices[position]

        Log.i("[Adapter]","${device.name}  ${device.address}  (${device.rssi})")
        view.findViewById<TextView>(android.R.id.text1).text = "${device.name}  ${device.address}  (${device.rssi})"
        view.setOnClickListener {
            onClickListener.invoke(device)
        }
        return view
    }
}