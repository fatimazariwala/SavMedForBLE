package mu.location.savmed.bluetooth.bluetoothLE.models

import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mu.location.savmed.SavMed.Companion.bleClient
import mu.location.savmed.SavMed.Companion.bleServer
import mu.location.savmed.SavMed.Companion.webSocket
import mu.location.savmed.bluetooth.bluetoothLE.NearByFragment
import mu.location.savmed.bluetooth.bluetoothLE.NearByFragment.Companion
import mu.location.savmed.bluetooth.bluetoothLE.controls.BLEClient

class BluetoothLEViewModel constructor(): ViewModel() {

    companion object {
        const val TAG = "[BLE ViewModel]"
    }

    init {

    }

    private val _state = MutableStateFlow(BluetoothLEUiState())
    val state = combine(
        bleClient.scannedDevices,
        bleServer.listOfMessages,
        _state
    ) { scannedDevices,listOfMessages,state ->

//                Log.i(TAG, "Scanned Devices: ---${scannedDevices.size}")
//        scannedDevices.forEach { device ->
//            Log.i(TAG, "Device: ${device.deviceName} - ${device.address}")
//        }

//        Log.i(TAG, "List of Messages: ---${listOfMessages.size}")
//        listOfMessages.forEach { message ->
//            Log.i(TAG, "Message: $message")
//        }

        state.copy(
            scannedDevices = scannedDevices,
            listOfMessages =  listOfMessages
        )

    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),_state.value)


    fun startScan() {
        viewModelScope.launch {
            bleClient.startBLEScan()
        }
    }

    fun stopScan() {
        bleClient.stopBleScan()
    }

    fun SendMessage(device: BluetoothLEScannedDevices) {
        Log.i(TAG,"Tryynnaa sneddd..${webSocket.isConnected.value}..")
        bleClient.writeCharacteristic(device,"")

        if (webSocket.isConnected.value == false) {
            webSocket.connect()
        } else {
            if (!webSocket.join_key.value.isNullOrEmpty()) {
                bleClient.enableJoinKeyWrite = true
            } else {
                webSocket.initiate()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
      //  bluetoothController.release()
    }
}