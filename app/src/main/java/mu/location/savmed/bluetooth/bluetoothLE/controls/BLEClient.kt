package mu.location.savmed.bluetooth.bluetoothLE.controls

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mu.location.savmed.SavMed.Companion.bleServer
import mu.location.savmed.SavMed.Companion.bluetoothAdapter
import mu.location.savmed.SavMed.Companion.bluetoothManager
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.SavMed.Companion.webSocket
import mu.location.savmed.bluetooth.bluetoothLE.controls.BLEServer.Companion.CHARACTERISTIC_JOIN_KEY_UUID
import mu.location.savmed.bluetooth.bluetoothLE.controls.BLEServer.Companion.CHARACTERISTIC_OIDENTITY_UUID
import mu.location.savmed.bluetooth.bluetoothLE.controls.BLEServer.Companion.CHARACTERISTIC_USER_NAME_UUID
import mu.location.savmed.bluetooth.bluetoothLE.controls.BLEServer.Companion.SERVICE_UUID
import mu.location.savmed.bluetooth.bluetoothLE.models.BluetoothLESavMedDevices
import mu.location.savmed.bluetooth.bluetoothLE.models.BluetoothLEScannedDevices
import mu.location.savmed.bluetooth.bluetoothLE.models.GlobalEventTriggers
import mu.location.savmed.bluetooth.bluetoothLE.models.LocationChar
import mu.location.savmed.utils.SettingsManager.hasPermission
import mu.location.savmed.utils.SharedPreference
import java.io.IOException
import java.util.UUID
import kotlin.math.pow


@Suppress("MissingPermission")
class BLEClient(
    val context: Context
) {

    companion object {
        const val TAG = "[BLE CLient]"
    }

    val activeGattConnections = HashMap<String, BluetoothGatt>()
    val locationReadComplete = MutableLiveData<Boolean>()

    val scanStatus = MutableLiveData<String>()
    var enableJoinKeyWrite = false

    val bluetoothLeScanner by lazy { bluetoothAdapter?.bluetoothLeScanner }

    private val _scannedDevices = MutableStateFlow<List<BluetoothLEScannedDevices>>(emptyList())
    val scannedDevices: StateFlow<List<BluetoothLEScannedDevices>>
        get() = _scannedDevices.asStateFlow()

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var scanning = false
    val handler = android.os.Handler()

    val SCAN_PERIOD: Long = 10000

    private val bleScanCallBack: ScanCallback = object: ScanCallback() {

        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            Log.i(TAG,"Device Name: ${result?.device?.name},Address: ${result?.device?.address} RSSI: ${result?.rssi} sevice data: ${result?.scanRecord?.serviceUuids} ")

            if (result != null) {
                processScanResult(result)
            } else {
                Log.i(TAG,"Scan Result is NUlll!")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            scanStatus.postValue("Scan Failed!")
            coroutineScope.launch {
                coreContext._globalEvents.emit(GlobalEventTriggers.Error("Scan Error Code: $errorCode"))
            }
        }
    }

    init {
        locationReadComplete.postValue(false)
    }

    private val BLEgattCallBack = object: BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            if (newState == BluetoothProfile.STATE_CONNECTED) {

                Log.i(TAG, "Successful Connection! Requesting Service Discovery ${gatt?.device}")
                gatt?.discoverServices()

                coroutineScope.launch {
                    coreContext._globalEvents.emit(GlobalEventTriggers.ConnectionEstablished)
                }
                locationReadComplete.postValue(false)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {

                gatt?.device?.address?.let { address ->
                    removeConnection(address)
                }

                Log.i(TAG, "Connection DisConnected!")
                coroutineScope.launch {
                    coreContext._globalEvents.emit(GlobalEventTriggers.Error("Connection Disconnected!"))
                }

            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)

            Log.i(TAG, "In disssssssssssssssssssssss")
            if (status == BluetoothGatt.GATT_SUCCESS) {

                val discoveredCharacteristics = mutableListOf<BluetoothGattCharacteristic>()
                Log.i(TAG, "SErvice Discovered")

                val services = getSupportedGattService(gatt!!)

                for (service in services ?: emptyList()) {
                    Log.i(TAG, "Service UUID: ${service?.uuid}")
                    // Loop through the characteristics
                    for (characteristic in service?.characteristics ?: emptyList()) {

                        Log.i(TAG, "Service Character: ${characteristic.uuid}")
                        val char_uuid = characteristic.uuid.toString()

                        if (char_uuid.equals(CHARACTERISTIC_USER_NAME_UUID)) {

                            Log.i(TAG, "Characteristic with UserName Found!")
                            Log.i(TAG,"${gatt.readCharacteristic(characteristic)} - Reading CHar......")
                            discoveredCharacteristics.add(characteristic)

                        } else if (char_uuid.equals(CHARACTERISTIC_OIDENTITY_UUID)) {

                            Log.i(TAG, "Characteristics with Others Identity Found!!")
                            discoveredCharacteristics.add(characteristic)

                        } else if (char_uuid.equals(CHARACTERISTIC_JOIN_KEY_UUID)) {

                            Log.i(TAG, "Characteristics with Join Found!!")
                            discoveredCharacteristics.add(characteristic)
                        }
                    }
                }

                addDeviceToSavMedDevicesList(gatt, discoveredCharacteristics)

            } else {
                Log.w(TAG, "onServiceDiscovered received: $status")
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {

                val char = characteristic.uuid.toString()
                val value = characteristic.value
                val valueString = String(value ?: ByteArray(0)) // Convert byte array to string
                Log.i(TAG, "Characteristic Value-----: $valueString ${characteristic.uuid}")

                if (char.equals(CHARACTERISTIC_USER_NAME_UUID)) {
                    Log.i(TAG,"In char found!!!")
                    addDeviceToSavMedDevicesList(gatt, null, valueString)
                }

            } else {
                Log.w(TAG, "Error reading characteristic: $status")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            Log.i(TAG,"SOmething..... $status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(
                    TAG,
                    "Characteristic write successful: ${characteristic.value.toString(Charsets.UTF_8)} ${characteristic.uuid}"
                )
                if (characteristic.uuid.toString().equals(CHARACTERISTIC_OIDENTITY_UUID)) {
                    Log.i(TAG,"Performing Second Write...")
                    
                    coroutineScope.launch {
                        coreContext._globalEvents.emit(GlobalEventTriggers.DataTransferSucceeded)
                    }
                    
                    if (enableJoinKeyWrite) {
                        if (webSocket.join_key.value != null) {
                            sendJoinKey(webSocket.join_key.value!!)
                        }
                    }
                }
            } else {
                Log.e(TAG, "Characteristic write failed with status: $status")
            }
        }
    }

    suspend fun startBLEScan() {
        if(!hasPermission(android.Manifest.permission.BLUETOOTH_SCAN,context)) {
            flow {
               emit(GlobalEventTriggers.Error("BLE Scan Security Exception"))
            }
        }

        if (!scanning) {
            handler.postDelayed({
                scanning = false
                stopBleScan()
            }, SCAN_PERIOD)
            scanning = true

            if (!activeGattConnections.isEmpty()) {
                for (keys in activeGattConnections) {
                    keys.value.disconnect()
                    keys.value.close()
                }
            }

            delay(200)

            scanStatus.postValue("Scanning...")
            Log.i(TAG, "Scan Start...")
            bleServer.stopAdvertise()
            bluetoothLeScanner?.startScan(bleScanCallBack)
        } else {
            scanStatus.postValue("Scan Completed!")
            stopBleScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun writeCharacteristic(device: BluetoothLEScannedDevices, payload: String) {
        Log.i(TAG,"In write...")
        val distz = device.dist?.toBigDecimal()?.setScale(2, java.math.RoundingMode.HALF_UP)?.toDouble()

        for (characteristic in device.characteristics ?: emptyList()) {

            val char = characteristic.uuid.toString()

            Log.i(TAG,"CAHrxxxx $char $CHARACTERISTIC_JOIN_KEY_UUID")
            if (char.equals(CHARACTERISTIC_OIDENTITY_UUID)) {

                Log.i(TAG,"Writting UserName N DIstance ${device.address}")

                val userName = if (SharedPreference.username.contains('.')) {
                    SharedPreference.username.split('.')[0]
                } else {
                    SharedPreference.username
                }

                characteristic.setValue("${userName}#${distz}")
                Log.i(TAG,"${getActiveGattConnection(device.address!!)?.writeCharacteristic(characteristic)} - Result of characteristic....")
            }
            Log.i(TAG,"char data---ini: ${characteristic.value}")
        }
    }

    private fun processScanResult(result: ScanResult) {

        Log.i(TAG,"processss.....")
        val serviceUuids = result.scanRecord?.serviceUuids

        if (serviceUuids != null) {

            for(uuid in serviceUuids) {
                val uuidFetched = uuid.toString()

                Log.i(TAG,"Checking Service UUIDS in process Scan [Fetched UUID:$uuidFetched] [SavMed UUID:$SERVICE_UUID] ")
                if(uuidFetched.equals(SERVICE_UUID)) {

                    Log.i(TAG,"----------------Found SavMed Device----------------------")
                    addDeviceToScannedDeviceList(result, true)

                } else {
                    Log.i(TAG,"SavMed uuid not found $uuid")
                    addDeviceToScannedDeviceList(result,false)
                }
            }
        } else {
            addDeviceToScannedDeviceList(result,false)
            Log.i(TAG,"No service uuid found")
        }
    }

    fun connectToDevice(device: BluetoothLEScannedDevices ): Flow<GlobalEventTriggers> {
        Log.i(TAG,"Connecting to BLE Device ${device.address}")
        return flow {

            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT,context)) {
                throw SecurityException("Bluetooth Connect permission Not Granted!")
            }
            //Log.i(TAG,"Security exception passed ${bluetoothGatt?.device}")
            bluetoothAdapter.let { adapter ->
                try {
                   val deviceFound = adapter?.getRemoteDevice(device.address)

                    Log.i(TAG,"FOund device ${deviceFound?.address}")

                    val newBluetoothGatt = deviceFound?.connectGatt(context, false, BLEgattCallBack)

                    if (newBluetoothGatt != null && device.address != null) {
                        Log.i(TAG,"Adding Device To Active Gatt Connections!")
                        addConnection(device.address,newBluetoothGatt)
                    }

                    emit(GlobalEventTriggers.Success("Successful Connection!"))

                    Log.i(TAG,"AFter ConnectGatt ${device.address}")

                } catch (e: IOException) {

                    emit(GlobalEventTriggers.Error("Error Connecting To ${device.address}"))
                }
            }
        }.onCompletion {

        }.flowOn(Dispatchers.IO)
    }

    fun addDeviceToSavMedDevicesList(gatt: BluetoothGatt?, discoveredCharacteristics: List<BluetoothGattCharacteristic>?, name: String? = null) {

        Log.i(TAG,"Got it.......")
        val savMedDevice = BluetoothLESavMedDevices(
            deviceName = gatt?.device?.name ?: "",
            address = gatt?.device?.address,
            characteristics = discoveredCharacteristics,
        )

        _scannedDevices.update { devices ->
            var existingName = -1
            val existingDeviceIndex = devices.indexOfFirst { device ->
                device.address == gatt?.device?.address
            }

            if (existingDeviceIndex != -1) {

                val updatedDevices = devices.toMutableList()

                if (discoveredCharacteristics != null) {
                    val updatedDevice = updatedDevices[existingDeviceIndex].copy(
                        characteristics = discoveredCharacteristics
                    )
                    updatedDevices[existingDeviceIndex] = updatedDevice
                    Log.i(TAG, "Updating Characteristics: ${updatedDevice.characteristics?.size}")

                    // Log characteristics values
                    updatedDevice.characteristics?.forEach { char ->
                        Log.i(TAG, "Device characteristic value: ${char.value?.toString()}")
                    }
                }

                if (name != null) {

                    val split = name.split('#')
                    if (split[1] != "000"){
                        coreContext.contactsManager.getUserNameFromPrimaryKey(split[1])
                    }
                    val updatedDevice = updatedDevices[existingDeviceIndex].copy(
                        name = split[0],
                        deviceName = split[1]   // this is primary Key
                    )

                    updatedDevices[existingDeviceIndex] = updatedDevice
                    Log.i(TAG, "Updated Name: $name")
                }

                // Moving the updated device to the first position
                val updatedDevice = updatedDevices[existingDeviceIndex]
                updatedDevices.removeAt(existingDeviceIndex)
                updatedDevices.add(0, updatedDevice)

                updatedDevices.forEach {
                    Log.i(TAG, "Updated Device: ${it.name} - ${it.address}")
                }

                updatedDevices
            } else {
                // If the device is not found, return the original list unchanged
                Log.i(TAG, "SavMed Device ${gatt?.device?.address} not found in Scanned Device List")
                devices
            }
        }
    }

    fun addDeviceToScannedDeviceList(result: ScanResult, isSavMed: Boolean) {

        val newDevice = BluetoothLEScannedDevices(
            deviceName = result.device.name ?: "",
            address = result.device.address,
            rssi = result.rssi.toString(),
            dist = calculateDistance(result.rssi),
            isSavMed = isSavMed
        )

        _scannedDevices.update { devices ->
            val mutableDevices = devices.toMutableList()
            val existingDeviceIndex = devices.indexOfFirst { it.address == newDevice.address }

            if (existingDeviceIndex != -1) {
                mutableDevices[existingDeviceIndex] = newDevice // Replace the existing device
            } else {
                mutableDevices.add(newDevice) // Add the new device
            }
            mutableDevices
        }

    }

    fun stopBleScan() {

        if(!hasPermission(Manifest.permission.BLUETOOTH_SCAN,context)) return
        scanning = false
        bluetoothLeScanner?.stopScan(bleScanCallBack)

        if (bleServer.advertisingState != true) {
            Log.i(TAG,"Advertising Starting")
            bleServer.startAdvertise()
        } else {
            Log.i(TAG,"Advertise ALready Started...")
        }
        
        val scannedDevices = _scannedDevices.value
        for (device in scannedDevices) {

            Log.i(TAG,"In Connection Looop")
            if (device.isSavMed == true) {

                val deviceFound = bluetoothAdapter.getRemoteDevice(device.address)
                val connectionState = bluetoothManager.getConnectionState(deviceFound,BluetoothProfile.GATT)

                if (connectionState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG,"in COnnetced!!! already")
                    getActiveGattConnection(device.address!!)?.discoverServices()
                } else if (connectionState == BluetoothProfile.STATE_DISCONNECTED) {
                    coroutineScope.launch {
                        Log.i(TAG, "In COnnection Coroutine..")
                        connectToDevice(device).collect { triggerOut ->
                            when (triggerOut) {
                                is GlobalEventTriggers.Success -> {
                                    coreContext._globalEvents.emit(GlobalEventTriggers.ConnectionEstablished)
                                    Log.i(TAG, triggerOut.message)
                                }

                                is GlobalEventTriggers.Error -> {
                                    coreContext._globalEvents.emit(
                                        GlobalEventTriggers.Error("Connection Error -> ${triggerOut.message}")
                                    )
                                    Log.e(TAG, triggerOut.message)
                                }

                                else -> { }
                            }
                        }
                    }
                }
            }
        }
    }

    fun getSupportedGattService(gatt: BluetoothGatt): List<BluetoothGattService?>? {
        return gatt.services
    }

    fun calculateDistance(rssi: Int, rssiAt1Meter: Int = -50, pathLossExponent: Double = 2.0): Double {
        val rawDistance = 10.0.pow((rssiAt1Meter - rssi) / (10 * pathLossExponent))
        return rawDistance.toBigDecimal().setScale(2, java.math.RoundingMode.HALF_UP).toDouble()
    }

    fun resetDeviceList() {
        _scannedDevices.update { emptyList() }
    }

    fun sendJoinKey(joinKey: String) {
        Log.i(TAG,"Sending JOin key ${joinKey}")
        val scannedDevices = _scannedDevices.value
        for (conn in activeGattConnections) {
            val found = scannedDevices.find { bluetoothLEScannedDevices ->
                bluetoothLEScannedDevices.address == conn.key
            }

            for (char in found?.characteristics ?: emptyList()) {
                if (char.uuid.toString() == CHARACTERISTIC_JOIN_KEY_UUID) {
                    char.setValue(joinKey)

                    Log.i(TAG,"Performing Write: ${conn.value.writeCharacteristic(char)} ")

                }
            }
        }
    }

    fun BluetoothManager.disconnect(address: String) {
        val device = bluetoothAdapter.getRemoteDevice(address)

        if (device != null) {
            val connectionState = getConnectionState(device,BluetoothProfile.GATT)
            if (connectionState == BluetoothProfile.STATE_CONNECTED) {
                val gatt = getActiveGattConnection(device.address)
                if (gatt != null) {
                    gatt.disconnect()
                    gatt.close()
                }
            }
        }
    }

    fun addConnection(device: String,gatt: BluetoothGatt) {
        activeGattConnections[device] = gatt
    }

    fun removeConnection(device: String) {
        activeGattConnections.remove(device)
    }

    fun getActiveGattConnection(device: String): BluetoothGatt? {
        return activeGattConnections.get(device)
    }
}
