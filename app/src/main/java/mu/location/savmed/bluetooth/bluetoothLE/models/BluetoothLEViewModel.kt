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
import mu.location.savmed.SavMed.Companion.bleClient
import mu.location.savmed.SavMed.Companion.bleServer
import mu.location.savmed.bluetooth.bluetoothLE.BluetoothLEController
import mu.location.savmed.bluetooth.bluetoothLE.NearByFragment
import mu.location.savmed.bluetooth.bluetoothLE.NearByFragment.Companion

class BluetoothLEViewModel constructor(): ViewModel() {

    companion object {
        const val TAG = "[BLE ViewModel]"
    }

    init {
        bleServer.bleServerEvent.onEach { result ->

            when(result) {
                is ConnectionResult.BLETransferSucceeded -> {
                    Log.i(TAG,"yoooooooooooo ${result.message}")
                }
                else -> { }
            }

        }
            .catch { throwable ->
                Log.e(TAG, "Error: $throwable")
            }
            .launchIn(viewModelScope)
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
        bleClient.startBLEScan()
    }

    // AllowIncomignDevices
    fun stopScan() {
        bleClient.stopBleScan()
    }

    fun SendMessage(device: BluetoothLEScannedDevices) {
        Log.i(TAG,"Tryynnaa sneddd....")
        bleClient.writeCharacteristic(device,"")
    }

    private fun Flow<ConnectionResult>.listen(): Job {
        return onEach { result ->
            when(result) {
                ConnectionResult.ConnectionEstablished -> {
                    _state.update { it.copy(
                        toastMessage = "Connection Established"
                    ) }
                }
                is ConnectionResult.BLETransferSucceeded -> {
                    Log.i(TAG,"wriet message -> ${result.message}")
                    _state.update { it.copy(
                        message = result.message
                    ) }
                }
                is ConnectionResult.Error -> {
                    _state.update { it.copy(
                        toastMessage = result.message
                    ) }
                }
                else -> { }
            }
        }
            .catch { throwable ->
                _state.update { it.copy(
                    toastMessage = "Some Error Occurred!"
                ) }
                Log.i(TAG,"ConnectionResult Error: ${throwable.message}")
            }
            .launchIn(viewModelScope)
    }


    override fun onCleared() {
        super.onCleared()
      //  bluetoothController.release()
    }
}