package mu.location.savmed.bluetooth.bluetoothLE.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import mu.location.savmed.bluetooth.bluetoothLE.BluetoothLEController

class BluetoothLEViewModelFactory(private val bluetoothLEController: BluetoothLEController) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return BluetoothLEViewModel(bluetoothLEController) as T
    }
}