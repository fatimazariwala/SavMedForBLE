package mu.location.savmed.bluetooth.bluetoothLE.controls

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
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
import mu.location.savmed.bluetooth.bluetoothLE.controls.BLEServer.Companion.CHARACTERISTIC_OIDENTITY_UUID
import mu.location.savmed.bluetooth.bluetoothLE.controls.BLEServer.Companion.CHARACTERISTIC_OLOC_UUID
import mu.location.savmed.bluetooth.bluetoothLE.controls.BLEServer.Companion.CHARACTERISTIC_USER_LOC_UUID
import mu.location.savmed.bluetooth.bluetoothLE.controls.BLEServer.Companion.CHARACTERISTIC_USER_NAME_UUID
import mu.location.savmed.bluetooth.bluetoothLE.controls.BLEServer.Companion.SERVICE_UUID
import mu.location.savmed.bluetooth.bluetoothLE.models.BluetoothLESavMedDevices
import mu.location.savmed.bluetooth.bluetoothLE.models.BluetoothLEScannedDevices
import mu.location.savmed.bluetooth.bluetoothLE.models.ConnectionResult
import mu.location.savmed.utils.SettingsManager.hasPermission
import mu.location.savmed.utils.SharedPreference
import java.io.IOException
import kotlin.math.pow

@Suppress("MissingPermission")
class BLEClient(
    val context: Context
) {

    companion object {
        const val TAG = "[BLE CLient]"
        const val ACTION_GATT_CONNECTED =
            "ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED =
            "ACTION_GATT_DISCONNECTED"
        const val ACTION_GAT_SERVICES_DISCOVERED =
            "ACTION_GAT_SERVICES_DISCOVERED"
    }

    var bluetoothGatt: BluetoothGatt? = null

    private val _bleEvent = MutableSharedFlow<ConnectionResult>()
    val bleEvent: SharedFlow<ConnectionResult>
        get() = _bleEvent

    val scanStatus = MutableLiveData<String>()

    val bluetoothLeScanner by lazy { bluetoothAdapter?.bluetoothLeScanner }

    private val _scannedDevices = MutableStateFlow<List<BluetoothLEScannedDevices>>(emptyList())
    val scannedDevices: StateFlow<List<BluetoothLEScannedDevices>>
        get() = _scannedDevices.asStateFlow()


    private val _savMedDevices = MutableStateFlow<List<BluetoothLESavMedDevices>>(emptyList())
    val savMedDevices: StateFlow<List<BluetoothLESavMedDevices>>
        get() = _savMedDevices.asStateFlow()

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
                _bleEvent.emit(ConnectionResult.Error("Scan Error Code: $errorCode"))
            }
        }
    }

    private val BLEgattCallBack = object: BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            if (newState == BluetoothProfile.STATE_CONNECTED) {

                Log.i(TAG, "Successful Connection! Requesting Service Discovery")
                bluetoothGatt?.discoverServices()

                coroutineScope.launch {
                    _bleEvent.emit(ConnectionResult.ConnectionEstablished)
                }


            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {

                Log.i(TAG, "Connection DisConnected!")
                coroutineScope.launch {
                    _bleEvent.emit(ConnectionResult.Error("Connection Disconnected!"))
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)

            Log.i(TAG, "In disssssssssssssssssssssss")
            if (status == BluetoothGatt.GATT_SUCCESS) {

                val discoveredCharacteristics = mutableListOf<BluetoothGattCharacteristic>()
                Log.i(TAG, "SErvice Discovered")
                val services = getSupportedGattService()

                for (service in services ?: emptyList()) {
                    Log.i(TAG, "Service UUID: ${service?.uuid}")
                    // Loop through the characteristics
                    for (characteristic in service?.characteristics ?: emptyList()) {

                        Log.i(TAG, "Service Character: ${characteristic.uuid}")
                        val char_uuid = characteristic.uuid.toString()

                        if (char_uuid.equals(CHARACTERISTIC_USER_NAME_UUID)) {

                            Log.i(TAG, "Characteristic with UserName Found!")
                            bluetoothGatt?.readCharacteristic(characteristic)
                            discoveredCharacteristics.add(characteristic)

                        } else if (char_uuid.equals(CHARACTERISTIC_USER_LOC_UUID)) {

                            Log.i(TAG, "Characteristics with Location Found!!")
                            bluetoothGatt?.readCharacteristic(characteristic)
                            discoveredCharacteristics.add(characteristic)

                        } else if (char_uuid.equals(CHARACTERISTIC_OIDENTITY_UUID)) {

                            Log.i(TAG, "Characteristics with Others Identity Found!!")
                            discoveredCharacteristics.add(characteristic)

                        } else if (char_uuid.equals(CHARACTERISTIC_OLOC_UUID)) {

                            Log.i(TAG, "Characteristics with Others Location Found!!")
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
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(
                    TAG,
                    "Characteristic write successful: ${characteristic.value.toString(Charsets.UTF_8)}"
                )
                if (characteristic.uuid.toString().equals(CHARACTERISTIC_OIDENTITY_UUID)) {
                    Log.i(TAG,"Performing Seconf Write...")
                    secondWriteRequest(gatt.device.address)
                }
            } else {
                Log.e(TAG, "Characteristic write failed with status: $status")
            }
        }
    }

    fun startBLEScan() {
        if(!hasPermission(android.Manifest.permission.BLUETOOTH_SCAN,context)) {
            flow {
               emit(ConnectionResult.Error("BLE Scan Security Exception"))
            }
        }

        if (!scanning) { // Stops scanning after a pre-defined scan period.
            handler.postDelayed({
                scanning = false
                stopBleScan()
            }, SCAN_PERIOD)
            scanning = true

            scanStatus.postValue("Scanning...")
            Log.i(TAG, "Scan Start...")
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

            Log.i(TAG,"CAHrxxxx $char $CHARACTERISTIC_OLOC_UUID")
            if (char.equals(CHARACTERISTIC_OIDENTITY_UUID)) {

                Log.i(TAG,"Writting UserName N DIstance")
                characteristic.setValue("${SharedPreference.username}#${distz}")
                bluetoothGatt?.writeCharacteristic(characteristic)

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
                    val isExisting = _scannedDevices.value.any { device ->
                        device.address == result.device.address
                    }

                    if (!isExisting) {
                        addDeviceToScannedDeviceList(result, true)
                    }

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

    fun connectToDevice(device: BluetoothLEScannedDevices ): Flow<ConnectionResult> {
        Log.i(TAG,"Connecting to BLE Device ${device.address}")
        return flow {

            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT,context)) {
                throw SecurityException("Bluetooth Connect permission Not Granted!")
            }
            //Log.i(TAG,"Security exception passed ${bluetoothGatt?.device}")
            bluetoothAdapter.let { adapter ->

                Log.i(TAG,"Going to tryyyyyyyyyyyyyyyy ${bluetoothGatt?.device}")
                try {
                    Log.i(TAG,"In tryyyyyyyyyyyy ${bluetoothGatt?.device}")

                    val deviceFound = adapter?.getRemoteDevice(device.address)

                    Log.i(TAG,"FOund device ${deviceFound?.address}")

                    bluetoothGatt = deviceFound?.connectGatt(context, false, BLEgattCallBack)

                    emit(ConnectionResult.Success("Successful Connection!"))

                    Log.i(TAG,"AFter ConnectGatt ${bluetoothGatt?.device}")

                } catch (e: IOException) {

                    emit(ConnectionResult.Error("Error Connecting To ${device.address}"))
                }
            }
        }.onCompletion {

        }.flowOn(Dispatchers.IO)
    }

    fun createBroadCastConnection(): Flow<ConnectionResult> {
        Log.i(TAG,"In Broad BLE...")
        return flow {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT,context)) {
                throw SecurityException("Bluetooth Connect permission Not Granted!")
            }
            //Log.i(TAG,"Security exception passed ${bluetoothGatt?.device}")
            bluetoothAdapter.let { adapter ->

                try {
                    Log.i(TAG,"Trying to Send Broadcast Connection request ${bluetoothGatt?.device}")

                    val deviceFound = adapter?.getRemoteDevice("ff:ff:ff:ff:ff:ff")

                    Log.i(TAG,"Found device ${deviceFound?.address}")

                    bluetoothGatt = deviceFound?.connectGatt(context, false, BLEgattCallBack)

                    emit(ConnectionResult.Success("Successful Connection!"))

                    Log.i(TAG,"AFter ConnectGatt ${bluetoothGatt?.device}")

                } catch (e: IOException) {

                    emit(ConnectionResult.Error("Error Connecting To [ff:ff:ff:ff:ff:ff]"))
                }
            }
        }.onCompletion {

        }.flowOn(Dispatchers.IO)
    }

    fun addDeviceToSavMedDevicesList(gatt: BluetoothGatt?, discoveredCharacteristics: List<BluetoothGattCharacteristic>?, name: String? = null) {

        val savMedDevice = BluetoothLESavMedDevices(
            deviceName = gatt?.device?.name ?: "",
            address = gatt?.device?.address,
            characteristics = discoveredCharacteristics,
        )

        _savMedDevices.update { devices ->
            val existingDeviceIndex = devices.indexOfFirst { device ->
                device.address == gatt?.device?.address
            }
            if (existingDeviceIndex == -1) {
                devices + savMedDevice
            } else {
                devices.toMutableList().apply {
                    this[existingDeviceIndex].name = name
                }
            }
            devices
        }

        _scannedDevices.update { devices ->
            val existingDeviceIndex = devices.indexOfFirst { device ->
                device.address == gatt?.device?.address
            }

            if (existingDeviceIndex != -1) {
                // Create a mutable copy of the devices list to modify
                val updatedDevices = devices.toMutableList()

                // Update characteristics if needed
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

                    val updatedDevice = updatedDevices[existingDeviceIndex].copy(
                        name = split[0],
                        deviceName = split[1]
                    )
                    updatedDevices[existingDeviceIndex] = updatedDevice
                    Log.i(TAG, "Updated Name: $name")
                }

                // Optionally, move the updated device to the first position
                val updatedDevice = updatedDevices[existingDeviceIndex]
                updatedDevices.removeAt(existingDeviceIndex)
                updatedDevices.add(0, updatedDevice)

                // Log the updated devices list
                updatedDevices.forEach {
                    Log.i(TAG, "Updated Device: ${it.name} - ${it.address}")
                }

                // Return the updated list to trigger the update
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

        val scannedDevices = _scannedDevices.value
        for (device in scannedDevices) {

            Log.i(TAG,"In Connection Looop")
            if (device.isSavMed == true) {

                val deviceFound = bluetoothAdapter.getRemoteDevice(device.address)
                val connectionState = bluetoothManager.getConnectionState(deviceFound,BluetoothProfile.GATT)

                if (connectionState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG,"in COnnetced!!! already")
                    bluetoothGatt?.discoverServices()
                } else if (connectionState == BluetoothProfile.STATE_DISCONNECTED) {
                    coroutineScope.launch {
                        Log.i(TAG, "In COnnection Coroutine..")
                        connectToDevice(device).collect { connectionResult ->
                            when (connectionResult) {
                                is ConnectionResult.Success -> {
                                    _bleEvent.emit(ConnectionResult.ConnectionEstablished)
                                    Log.i(TAG, connectionResult.message)
                                }

                                is ConnectionResult.Error -> {
                                    _bleEvent.emit(
                                        ConnectionResult.Error("Connection Error -> ${connectionResult.message}")
                                    )
                                    Log.e(TAG, connectionResult.message)
                                }

                                else -> {}
                            }
                        }
                    }
                }
            }
        }
    }

    fun getSupportedGattService(): List<BluetoothGattService?>? {
        return bluetoothGatt?.services
    }

    fun calculateDistance(rssi: Int, rssiAt1Meter: Int = -50, pathLossExponent: Double = 2.0): Double {
        val rawDistance = 10.0.pow((rssiAt1Meter - rssi) / (10 * pathLossExponent))
        return rawDistance.toBigDecimal().setScale(2, java.math.RoundingMode.HALF_UP).toDouble()
    }

    fun resetDeviceList() {
        _scannedDevices.update { emptyList() }
        _savMedDevices.update { emptyList() }
    }

    fun secondWriteRequest(deviceAddress: String) {
        Log.i(TAG,"in sec write....")
        val scannedDevices = _scannedDevices.value
        for (device in scannedDevices) {
            if (device.address == deviceAddress) {
                for (char in device.characteristics ?: emptyList()) {
                    if (char.uuid.toString().equals(CHARACTERISTIC_OLOC_UUID)) {
                        Log.i(TAG,"Writting Location at ${char.uuid.toString()} ${CHARACTERISTIC_OLOC_UUID}")
                        if (coreContext.isCoreAvailable()) {
                            char.setValue("${coreContext.onLocationEvent["latitude"]}#${coreContext.onLocationEvent["longitude"]}")
                            Log.i(TAG,"${bluetoothGatt?.device} in write loc...")
                            bluetoothGatt?.writeCharacteristic(char)
                        }
                    }
                }
            }
        }
    }

}
