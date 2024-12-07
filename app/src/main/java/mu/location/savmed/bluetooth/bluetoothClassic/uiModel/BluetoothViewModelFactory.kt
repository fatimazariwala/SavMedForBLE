package mu.location.savmed.bluetooth.bluetoothClassic.uiModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import mu.location.savmed.bluetooth.bluetoothClassic.BluetoothController

class BluetoothViewModelFactory(private val bluetoothController: BluetoothController) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return BluetoothViewModel(bluetoothController) as T
    }
}