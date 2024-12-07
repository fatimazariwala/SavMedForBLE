package mu.location.savmed.bluetooth.bluetoothLE.models

import mu.location.savmed.bluetooth.bluetoothClassic.models.BluetoothMessage

sealed interface ConnectionResult {

    object ConnectionEstablished: ConnectionResult

    data class TransferSucceeded(val message: BluetoothMessage): ConnectionResult
    data class BLETransferSucceeded(val message: String): ConnectionResult

    data class Error(val message: String): ConnectionResult

    data class Success(val message: String): ConnectionResult

}