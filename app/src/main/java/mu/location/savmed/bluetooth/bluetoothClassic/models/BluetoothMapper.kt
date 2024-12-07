package mu.location.savmed.bluetooth.bluetoothClassic.models

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.util.Log

// Device Mapper
@SuppressLint("MissingPermission")
fun BluetoothDevice.toBluetoothDeviceLocal(rssi: String,isUuidSelf: Boolean): BluetoothDeviceLocal {
    return BluetoothDeviceLocal(
        name = name,
        address = address,
        rssi = rssi,
        isUuidSelf = isUuidSelf // is the uuid SavMed UUid
    )
}

// Class to represent state of the UI
data class BluetoothUiState(
    val scannedDevices: List<BluetoothDeviceLocal> = emptyList(),
    val pairedDevices: List<BluetoothDeviceLocal> = emptyList(),
    val savMedDevices: List<BluetoothDeviceLocal> = emptyList(),
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val errorMessage: String? = null,
    val messages: List<BluetoothMessage> = emptyList(),
    val message: BluetoothMessage?= null // Send Individual Message
)

data class BluetoothMessage(
    val message: String,
    val senderName: String,
    val isFromLocalUser: Boolean
)

// Message Mapper to extract data From incoming message
fun String.toBluetoothMessage(isFromLocalUser: Boolean): BluetoothMessage {
    val name = substringBeforeLast("#")
    val message = substringAfter("#")
    Log.i("[Incoming Message]","$name,$message")
    return BluetoothMessage(
        message = message,
        senderName = name,
        isFromLocalUser = isFromLocalUser
    )
}

// Converting the Outgoing Message to Byte Array
fun BluetoothMessage.toByteArray(): ByteArray {
    Log.i("[Byte Array]","$senderName,$message")
    return "$senderName#$message".encodeToByteArray()
}
