package mu.location.savmed.bluetooth.bluetoothLE.models

import android.bluetooth.BluetoothGattCharacteristic
import java.sql.Timestamp

data class BluetoothLEScannedDevices(
    var name: String ?= null,
    var deviceName: String ?= null,
    var isSavMed: Boolean = false,
    val address: String ?= null,
    val rssi: String ?= null,
    val dist: Double ?= null,
    val latLon: LocationChar ?= null,
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
    val joinKey: String
)

data class LocationChar (
    val lat: Double,
    val lon: Double
)

data class NearByForAPI (
    val em_key: String,
    val em_caller: String,
    val em_responder: String,
    val em_responder_location: LocationChar,
    val event_timestamp: String
)