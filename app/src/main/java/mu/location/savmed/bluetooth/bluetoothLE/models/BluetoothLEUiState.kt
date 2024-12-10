package mu.location.savmed.bluetooth.bluetoothLE.models

import android.bluetooth.BluetoothGattCharacteristic

data class BluetoothLEScannedDevices(
    var name: String ?= null,
    var deviceName: String ?= null,
    var isSavMed: Boolean = false,
    val address: String ?= null,
    val rssi: String ?= null,
    val dist: Double ?= null,
    var characteristics: List<BluetoothGattCharacteristic>? = null
)

data class BluetoothLESavMedDevices (
    var deviceName: String ?= null,
    val address: String ?= null,
    var name: String ?= null,
    val characteristics: List<BluetoothGattCharacteristic> ?= null
)

data class BluetoothLEUiState(
    val scannedDevices: List<BluetoothLEScannedDevices> = emptyList(),
    val savMedDevices: List<BluetoothLESavMedDevices> = emptyList(),
    var toastMessage: String ?= null,
    val listOfMessages: List<writeMessage> ?= null,
    val message: String ?= null
)

data class writeMessage(
    val From: String,
    val dist :Double,
    val lat : Double,
    val lon : Double
)