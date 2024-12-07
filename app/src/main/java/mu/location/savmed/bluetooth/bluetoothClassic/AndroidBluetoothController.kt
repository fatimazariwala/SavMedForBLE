package mu.location.savmed.bluetooth.bluetoothClassic

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mu.location.savmed.bluetooth.bluetoothClassic.BroadCastReceivers.BluetoothStateReceiver
import mu.location.savmed.bluetooth.bluetoothClassic.BroadCastReceivers.FoundDeviceReceiver
import mu.location.savmed.bluetooth.bluetoothClassic.models.BluetoothDeviceLocal
import mu.location.savmed.bluetooth.bluetoothClassic.models.BluetoothMessage
import mu.location.savmed.bluetooth.bluetoothLE.models.ConnectionResult
import mu.location.savmed.bluetooth.bluetoothClassic.models.toBluetoothDeviceLocal
import mu.location.savmed.bluetooth.bluetoothClassic.models.toByteArray
import java.io.IOException
import java.lang.reflect.Method
import java.util.UUID

@SuppressLint("MissingPermission")
class AndroidBluetoothController(
    private val context: Context
): BluetoothController {

    companion object {
        const val TAG = "[Android Bluetooth Controller]"
        const val SHARE_UUID = "27b7d1da-08c7-4505-a6d1-2459987e5e2d"
    }

    private val bluetoothManager by lazy {
        context.getSystemService(BluetoothManager::class.java)
    }

    private val bluetoothAdapter by lazy {
        bluetoothManager?.adapter
    }

    private var dataTransferService: BluetoothDataTransferService? = null

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean>
        get() = _isConnected.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<BluetoothDeviceLocal>>(emptyList())
    override val scannedDevices: StateFlow<List<BluetoothDeviceLocal>>
        get() = _scannedDevices.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDeviceLocal>>(emptyList())
    override val pairedDevices: StateFlow<List<BluetoothDeviceLocal>>
        get() = _pairedDevices.asStateFlow()

    private val _savMedDevices = MutableStateFlow<List<BluetoothDeviceLocal>>(emptyList())
    override val savMedDevices: StateFlow<List<BluetoothDeviceLocal>>
        get() = _savMedDevices.asStateFlow()

    private val _error = MutableSharedFlow<String>()
    override val errors: SharedFlow<String>
        get() = _error.asSharedFlow()

    private val foundDeviceReceiver = FoundDeviceReceiver { device,rssi,isUuidSelf ->
        val newDevice = device.toBluetoothDeviceLocal(rssi = rssi.toString(),isUuidSelf)
        _scannedDevices.update { devices ->
            //Log.i("[FSubscriber]",device.address)
            if (newDevice in devices) devices else devices + newDevice
        }

        if (device.name != null) {
            if (device.name.contains("_SavMed")) {
                Log.i("[savmed-- S]","[savmed device found ${device.name}]")
                _savMedDevices.update { devices ->
                    if(newDevice in devices) devices else devices + newDevice
                }
            } else {
                Log.i("[savmed-- S]","[savmed device not found ${device.name}]")
            }
        }
    }

    private val bluetoothStateReceiver = BluetoothStateReceiver { isConnected, bluetoothDevice ->
        if (bluetoothAdapter?.bondedDevices?.contains(bluetoothDevice) == true) {
            _isConnected.update { isConnected }
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                _error.emit("Can't connect to a non-paired Device")
            }
        }
    }

    private var currentBluetoothServerSocket: BluetoothServerSocket? = null
    private var currentBluetoothClientSocket: BluetoothSocket? = null

    init{
        updatePairedDevices()
//        Log.i(TAG,bluetoothAdapter?.address.toString())
        context.registerReceiver(
            foundDeviceReceiver,
            IntentFilter(BluetoothDevice.ACTION_FOUND)
        )

        Log.i(TAG,"bluetooth class contact: $context")
        context.registerReceiver(
            bluetoothStateReceiver,
            IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
        )

        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            Log.i(TAG,"Setting Adapter name to unknown_SavMed")
            setAdapterName("unknown_SavMed")
        }
    }

    override fun startDiscovery() {
        if(!hasPermission(android.Manifest.permission.BLUETOOTH_SCAN)) return
        bluetoothAdapter?.startDiscovery()
    }

    override fun stopDiscovery() {
        if(!hasPermission(android.Manifest.permission.BLUETOOTH_SCAN)) return
        bluetoothAdapter?.cancelDiscovery()
    }

    override fun updatePairedDevices() {
        if(!hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT)) return

        Log.i(TAG,"In paired")

        bluetoothAdapter
            ?.bondedDevices
            ?.map { it.toBluetoothDeviceLocal(
                "N/A",false) }
            ?.also { devices ->
                _pairedDevices.update { devices }
                for (device in devices) {
                    if (device.name?.contains("_SavMed") == true) {
                        unpairBluetoothDevice(device.address)
                    } else {
                        Log.i("[savmed--]","[savmed not device found ${device.name ?: "unknown"}]")
                    }
                }
            }

        Log.i(TAG,"In paired : ${pairedDevices.value}")
    }

    override fun startBluetoothServer(): Flow<ConnectionResult> {
        return flow {
            if(!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                throw SecurityException("No BLUETOOTH_CONNECT Permission!")
            }

            currentBluetoothServerSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(
                "nearby_service",
                UUID.fromString(SHARE_UUID)
            )

            var shouldLoop = true
            while(shouldLoop) {
                currentBluetoothClientSocket = try {
                    currentBluetoothServerSocket?.accept()
                } catch (e: IOException) {
                    shouldLoop = false
                    null
                }
                emit(ConnectionResult.ConnectionEstablished)
                currentBluetoothClientSocket.let {
                    currentBluetoothServerSocket?.close()
                    val service = it?.let { it1 -> BluetoothDataTransferService(it1) }
                    if (service != null) {
                        dataTransferService = service

                        emitAll(
                            service
                                .listenForIncomingMessages()
                                .map {
                                    ConnectionResult.TransferSucceeded(it)
                                }
                        )
                    }
                }
            }
        }.onCompletion {
            closeConnection()
        }.flowOn(Dispatchers.IO)
    }

    override fun connectToDevice(device: BluetoothDeviceLocal): Flow<ConnectionResult> {
        return flow {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                throw SecurityException("Bluetooth Connect permission Not Granted!")
            }

            currentBluetoothClientSocket = bluetoothAdapter
                ?.getRemoteDevice(device.address)
                ?.createRfcommSocketToServiceRecord(
                    UUID.fromString(SHARE_UUID)
                )

            stopDiscovery()

            currentBluetoothClientSocket?.let { socket ->
                try {
                    socket.connect()
                    emit(ConnectionResult.ConnectionEstablished)

                    Log.i("[Connected]","Connection Successful")

                    BluetoothDataTransferService(socket).also {
                        dataTransferService = it
                        emitAll(
                            it.listenForIncomingMessages()
                                .map { ConnectionResult.TransferSucceeded(it) }
                        )
                    }
                } catch (e: IOException) {
                    socket.close()
                    currentBluetoothClientSocket = null
                    emit(ConnectionResult.Error("Connection aborted!"))
                }
            }
        }.onCompletion {
            closeConnection()
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun SendMessage(message: String): BluetoothMessage? {
        if(!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            return null
        }
        if(dataTransferService == null) {
            return null
        }
        val bluetoothMessage = BluetoothMessage(
            message = message,
            senderName = bluetoothAdapter?.name ?: "Unknow Name",
            isFromLocalUser = true
        )

        dataTransferService?.sendMessage(bluetoothMessage.toByteArray())

        return bluetoothMessage
    }
    override fun closeConnection() {
        currentBluetoothClientSocket?.close()
        currentBluetoothServerSocket?.close()
        currentBluetoothClientSocket = null
        currentBluetoothServerSocket = null
    }

    override fun release() {
        try {
            context.unregisterReceiver(foundDeviceReceiver)
            context.unregisterReceiver(bluetoothStateReceiver)
        } catch (e: Exception) {
            Log.i("Error UnRegistering","Receiver Not Found")
        }
        closeConnection()
    }

    override fun hasPermission(permission: String): Boolean {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun setAdapterName(userName: String) {
        bluetoothAdapter?.name = userName
    }

    fun unpairBluetoothDevice(targetDeviceAddress: String) {
        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            Log.e("Bluetooth", "Bluetooth not supported")
            return
        }

        val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter.bondedDevices

        for (device in pairedDevices) {
            if (device.address == targetDeviceAddress) { // Check the target device
                try {
                    // Get the removeBond method
                    val removeBondMethod: Method = device.javaClass.getMethod("removeBond")
                    // Invoke the method and cast the result to Boolean
                    val result: Boolean = removeBondMethod.invoke(device) as Boolean
                    if (result) {
                        Log.d("Bluetooth", "Device [$device]  successfully")
                    } else {
                        Log.e("Bluetooth", "Failed [$device] to unpair device")
                    }
                } catch (e: Exception) {
                    Log.e("Bluetooth", "Error unpairing device: ${e.message}")
                }
                break
            }
        }
    }
}