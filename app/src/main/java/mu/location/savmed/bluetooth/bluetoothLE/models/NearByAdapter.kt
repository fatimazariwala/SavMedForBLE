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
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.flow
import mu.location.savmed.R
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.SavMed.Companion.webSocket
import mu.location.savmed.bluetooth.bluetoothLE.NearByFragment
import mu.location.savmed.databinding.ItemNearbyuserBinding
import mu.location.savmed.databinding.SearchresultItemLayoutBinding
import mu.location.savmed.ui.call.Adapters.SearchResultAdapter
import mu.location.savmed.ui.call.Adapters.SearchResultAdapter.ContactDiffCallback
import org.linphone.core.SearchResult

class NearByAdapter(
    private val recyclerView: RecyclerView,
    private val onMessageClick: (BluetoothLEScannedDevices) -> Unit,
    private val onCallCLick: (String) -> Unit,
) : ListAdapter<BluetoothLEScannedDevices, NearByAdapter.BlueToothBLEDeviceViewHolder>(BleDeviceDiffCallback()) {

    companion object {
        const val TAG = "[BLE Adapter]"
    }

    init {
        Log.i(TAG,"i am init.....")
    }

    inner class BlueToothBLEDeviceViewHolder(
        private val binding: ItemNearbyuserBinding
    ): RecyclerView.ViewHolder(binding.root) {

        fun bind (device: BluetoothLEScannedDevices,recyclerView: RecyclerView) {
            Log.i(TAG,"in bind......")
            binding.apply {

                if (device.deviceName.isNullOrEmpty() && device.name.isNullOrEmpty()) {
                    bluetoothtvFullName.text = "[${device.dist}m way] rssi:(${device.rssi})"
                    deviceName.text = ""
                    tvDeviceAdd.text = device.address
                    distanceRssi.text = ""
                } else {
                    bluetoothtvFullName.text = device.name ?: ""
                    deviceName.text = device.deviceName ?: ""
                    tvDeviceAdd.text = device.address
                    distanceRssi.text = "[${device.dist}m way] rssi:(${device.rssi})"
                }

                if (device.isSavMed) {

                    val currentIndex = adapterPosition
                    if (currentIndex > 0) {
                        val updatedList = currentList.toMutableList()
                        updatedList.removeAt(currentIndex)
                        updatedList.add(0, device)
                        submitList(updatedList)
                    }

                    recyclerView.scrollToPosition(0)

                    root.setBackgroundColor(ContextCompat.getColor(root.context, R.color.green_main_300))
                } else {

                    root.setBackgroundColor(ContextCompat.getColor(root.context, R.color.white))
                }
                callBtn.setOnClickListener() {
                    var userToCall = ""
                    if (device.name != null) {
                        if (!webSocket.onPeerLocationEvent.value.isNullOrEmpty()) {
                            for ((key, value) in webSocket.onPeerLocationEvent.value!!) {
                                if (key.contains(device.name!!)) {
                                    userToCall = key
                                }
                            }
                            if (userToCall.isNotEmpty()) {
                                onCallCLick(userToCall)
                            } else {
                                coreContext.showPopUP.postValue("USER_TO_CALL_NOT_FOUND")
                            }
                        }
                    } else {
                        flow {
                            emit(ConnectionResult.Error("Cannot Call, Empty UserName!"))
                        }
                    }
                }

                transferbtn.setOnClickListener() {
                    Log.i(TAG,"Device characteristice on tf clicked ${device.characteristics?.size}")
                    if (!device.characteristics.isNullOrEmpty()) {
                        onMessageClick(device)
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BlueToothBLEDeviceViewHolder {
        val binding = ItemNearbyuserBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BlueToothBLEDeviceViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: BlueToothBLEDeviceViewHolder,
        position: Int
    ) {
        Log.i(TAG,"in bind.. creating virew b=holder....")
        holder.bind(getItem(position), recyclerView = recyclerView)
    }

    class BleDeviceDiffCallback: DiffUtil.ItemCallback<BluetoothLEScannedDevices>() {

        override fun areContentsTheSame(
            oldItem: BluetoothLEScannedDevices,
            newItem: BluetoothLEScannedDevices
        ): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areItemsTheSame(
            oldItem: BluetoothLEScannedDevices,
            newItem: BluetoothLEScannedDevices
        ): Boolean {
           return oldItem.address == newItem.address
        }

    }
}





//init {
//    Log.i("[Nearby Adapter]", "Devices list size: ${devices.size}")
//    if (devices.isEmpty()) {
//        Log.w("[Nearby Adapter]", "Devices list is empty")
//    }
//}
//
//lateinit var address : String
//
//override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
//
//    val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_nearbyuser,parent,false)
//    val device = devices[position]
//
//    view.findViewById<TextView>(R.id.tvDeviceAdd).setText(device.address)
//    view.findViewById<TextView>(R.id.bluetoothtvFullName).setText(device.name)
//
//
////        // Button to Connect To a Device
////        view.setOnClickListener() {
////            bluetoothViewModel.connectToDevice(device)
////        }
//
//    var lat = coreContext.onLocationEvent["latitude"] ?: 0.0
//    var lon = coreContext.onLocationEvent["longitude"] ?: 0.0
//
//    val geocoder = Geocoder(context)
//    try {
//        val addresses = geocoder.getFromLocation(lat, lon, 1)
//        address = addresses!![0].getAddressLine(0)
//    } catch (e: Exception) {
//        address = "Unable to fetch address"
//    }
//    // Button to Send Data (Should eb Paired)
//    view.findViewById<ImageView>(R.id.transferbtn).setOnClickListener() {
////            if(bluetoothViewModel.state.value.isConnected) {
////                bluetoothViewModel.wr("Help Needed at [${address}] by ${coreContext.core.defaultAccount?.params?.identityAddress?.username ?: "unknown"}")
////            } else {
////                bluetoothViewModel.connectToDevice(device)
////            }
//    }
//
//    // Button to Make A Call (No Need to Be Paired)
//    view.findViewById<ImageView>(R.id.call_btn).setOnClickListener() {
//        val userName = device.name
//        if (userName != null) {
//            coreContext.startCall(userName)
//        }
//    }
//    return view
//}