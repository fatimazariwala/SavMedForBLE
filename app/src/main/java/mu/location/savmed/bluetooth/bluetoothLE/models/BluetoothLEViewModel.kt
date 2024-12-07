package mu.location.savmed.bluetooth.bluetoothLE.models

import android.util.Log
import androidx.lifecycle.ViewModel
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
import mu.location.savmed.bluetooth.bluetoothLE.BluetoothLEController

class BluetoothLEViewModel constructor(
    private val bluetoothLEController: BluetoothLEController
): ViewModel() {

    companion object {
        const val TAG = "[BLE ViewModel]"
    }

    private val _state = MutableStateFlow(BluetoothLEUiState())
    val state = combine(
        bluetoothLEController.scannedDevices,
        bluetoothLEController.listOfMessages,
        _state
    ) { scannedDevices,listOfMessages,state ->

        state.copy(
            scannedDevices = scannedDevices,
            listOfMessages =  listOfMessages
        )

    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),_state.value)


    fun startScan() {
        bluetoothLEController.startDiscovery()
    }

    fun stopScan() {
        bluetoothLEController.stopDiscovery()
    }

//    fun SendMessage(message: String) {
//        bluetoothLEController.writeCharacteristic()
//    }

    private fun Flow<ConnectionResult>.listen(): Job {
        return onEach { result ->
            when(result) {
                ConnectionResult.ConnectionEstablished -> {
                    _state.update { it.copy(
                        toastMessage = "Connection Established"
                    ) }
                }
                is ConnectionResult.BLETransferSucceeded -> {
                    _state.update { it.copy(
                        toastMessage = result.message
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
                    toastMessage = "Some Error Occured!"
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