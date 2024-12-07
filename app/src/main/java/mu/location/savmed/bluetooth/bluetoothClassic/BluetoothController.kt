package mu.location.savmed.bluetooth.bluetoothClassic

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import mu.location.savmed.bluetooth.bluetoothClassic.models.BluetoothDeviceLocal
import mu.location.savmed.bluetooth.bluetoothClassic.models.BluetoothMessage
import mu.location.savmed.bluetooth.bluetoothLE.models.ConnectionResult

interface BluetoothController {
    val scannedDevices: StateFlow<List<BluetoothDeviceLocal>>
    val pairedDevices: StateFlow<List<BluetoothDeviceLocal>>
    val savMedDevices: StateFlow<List<BluetoothDeviceLocal>>
    val errors: SharedFlow<String>
    val isConnected: StateFlow<Boolean>

    // Set Name for BluetoothAdapter
    fun setAdapterName(userName: String)

    // Start Bluetooth Near-by Scan
    fun startDiscovery()

    // Stop Bluetooth Near-by Scan
    fun stopDiscovery()

    // Update the List of Paired Devices
    fun updatePairedDevices()

    // Launches the server for Connection
    fun startBluetoothServer(): Flow<ConnectionResult>

    // Connecting to the launched server
    fun connectToDevice(device: BluetoothDeviceLocal): Flow<ConnectionResult>

    // Send outgoing Message
    suspend fun SendMessage(message: String): BluetoothMessage?

    // Close Connection with the Connected Device
    fun closeConnection()

    // Releases the Scan Device Receiver
    fun release()

    // Check Permissions
    fun hasPermission(permission: String) : Boolean
}