package mu.location.savmed.bluetooth.bluetoothLE

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import mu.location.savmed.bluetooth.bluetoothLE.models.BluetoothLESavMedDevices
import mu.location.savmed.bluetooth.bluetoothLE.models.BluetoothLEScannedDevices
import mu.location.savmed.bluetooth.bluetoothLE.models.ConnectionResult
import mu.location.savmed.bluetooth.bluetoothLE.models.writeMessage

interface BluetoothLEController {
    val scannedDevices: StateFlow<List<BluetoothLEScannedDevices>>
    val savMedDevices: StateFlow<List<BluetoothLESavMedDevices>>
    val listOfMessages: SharedFlow<List<writeMessage>>
    val bleEvent: SharedFlow<ConnectionResult>
   // val isConnected: StateFlow<Boolean>

    // Set Name for BluetoothAdapter
    fun setAdapterName()

    fun initialize()

    // Start Bluetooth Near-by Scan
    fun startDiscovery()

    // Stop Bluetooth Near-by Scan
    fun stopDiscovery()

    // Creates a Service of BLE devices to communication with
   // fun createBLEServerService()

    // Connecting to the launched server
   // fun connectToDevice(device: BluetoothLEDevice): Flow<ConnectionResult>

    // Send A Help request
   fun sendMessage(device: BluetoothLEScannedDevices)

    // Close Connection with the Connected Device
    //fun closeConnection()

    // Releases the Scan Device Receiver
    //fun release()

    // Check Permissions
    fun hasPermission(permission: String) : Boolean
}