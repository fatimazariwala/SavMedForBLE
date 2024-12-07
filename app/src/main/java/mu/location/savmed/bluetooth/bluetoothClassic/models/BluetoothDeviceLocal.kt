package mu.location.savmed.bluetooth.bluetoothClassic.models

data class BluetoothDeviceLocal (
    val name: String?,
    val address: String,
    val rssi: String,
    val isUuidSelf: Boolean
)