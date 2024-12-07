package mu.location.savmed.bluetooth.bluetoothLE.models

import android.content.Context
import android.location.Geocoder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import mu.location.savmed.R
import mu.location.savmed.SavMed.Companion.coreContext

class NearByAdapter(
    context: Context,
    private val bluetoothViewModel: BluetoothLEViewModel,
    private val devices: List<BluetoothLEDevice>
) : ArrayAdapter<BluetoothLEDevice>(context,0,devices) {

    init {
        Log.i("[Nearby Adapter]", "Devices list size: ${devices.size}")
        if (devices.isEmpty()) {
            Log.w("[Nearby Adapter]", "Devices list is empty")
        }
    }

    lateinit var address : String

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {

        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_nearbyuser,parent,false)
        val device = devices[position]

        view.findViewById<TextView>(R.id.tvDeviceAdd).setText(device.address)
        view.findViewById<TextView>(R.id.bluetoothtvFullName).setText(device.name)


//        // Button to Connect To a Device
//        view.setOnClickListener() {
//            bluetoothViewModel.connectToDevice(device)
//        }

        var lat = coreContext.onLocationEvent["latitude"] ?: 0.0
        var lon = coreContext.onLocationEvent["longitude"] ?: 0.0

        val geocoder = Geocoder(context)
        try {
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            address = addresses!![0].getAddressLine(0)
        } catch (e: Exception) {
            address = "Unable to fetch address"
        }
        // Button to Send Data (Should eb Paired)
        view.findViewById<ImageView>(R.id.transferbtn).setOnClickListener() {
//            if(bluetoothViewModel.state.value.isConnected) {
//                bluetoothViewModel.wr("Help Needed at [${address}] by ${coreContext.core.defaultAccount?.params?.identityAddress?.username ?: "unknown"}")
//            } else {
//                bluetoothViewModel.connectToDevice(device)
//            }
        }

        // Button to Make A Call (No Need to Be Paired)
        view.findViewById<ImageView>(R.id.call_btn).setOnClickListener() {
            val userName = device.name
            coreContext.startCall(userName)
        }
        return view
    }
}