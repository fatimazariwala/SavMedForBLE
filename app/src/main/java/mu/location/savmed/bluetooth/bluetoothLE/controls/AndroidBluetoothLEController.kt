package mu.location.savmed.bluetooth.bluetoothLE.controls

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.bluetooth.bluetoothLE.BluetoothLEController
import mu.location.savmed.bluetooth.bluetoothLE.models.BluetoothLESavMedDevices
import mu.location.savmed.bluetooth.bluetoothLE.models.BluetoothLEScannedDevices
import mu.location.savmed.bluetooth.bluetoothLE.models.ConnectionResult
import mu.location.savmed.bluetooth.bluetoothLE.models.writeMessage
import mu.location.savmed.ui.contacts.models.ContactEvent
import mu.location.savmed.utils.SharedPreference
import java.io.IOException
import java.util.UUID

@SuppressLint("MissingPermission")
open class AndroidBluetoothLEController(
    private var context: Context
): BluetoothLEController {

    companion object {
        const val TAG = "[BLE Controller]"
//        const val SERVICE_UUID = "27b7d1da-08c7-4505-a6d1-2459987e5e2d"
//        const val CHARACTERISTIC_USERNAME_UUID = "87654321-4321-6789-4321-fedcba987654"
//        const val CHARACTERISTIC_MESSAGE_UUID = "fedcba987654-4321-6789-4321-87654321"


//        const val ACTION_GATT_CONNECTED =
//            "ACTION_GATT_CONNECTED"
//        const val ACTION_GATT_DISCONNECTED =
//            "ACTION_GATT_DISCONNECTED"
//        const val ACTION_GAT_SERVICES_DISCOVERED =
//            "ACTION_GAT_SERVICES_DISCOVERED"
    }

    val bluetoothManager by lazy {
        context.getSystemService(BluetoothManager::class.java)
    }

    val bluetoothAdapter by lazy {
        bluetoothManager?.adapter
    }

    lateinit var  bleClient: BLEClient
    lateinit var  bleServer: BLEServer

    lateinit var bluetoothLeAdvertiser: BluetoothLeAdvertiser
    lateinit var bluetoothGattServer: BluetoothGattServer
    var bluetoothGatt: BluetoothGatt? = null

    val bluetoothLeScanner by lazy { bluetoothAdapter?.bluetoothLeScanner }

//    protected val _scannedDevices = MutableStateFlow<List<BluetoothLEScannedDevices>>(emptyList())
//    override val scannedDevices: StateFlow<List<BluetoothLEScannedDevices>>
//        get() = _scannedDevices.asStateFlow()
//
//
//    protected val _savMedDevices = MutableStateFlow<List<BluetoothLESavMedDevices>>(emptyList())
//    override val savMedDevices: StateFlow<List<BluetoothLESavMedDevices>>
//        get() = _savMedDevices.asStateFlow()

//    protected val _listOfMessages = MutableStateFlow<List<writeMessage>>(emptyList())
//    override val listOfMessages: StateFlow<List<writeMessage>>
//        get() = _listOfMessages.asStateFlow()

    protected val _bleEvent = MutableSharedFlow<ConnectionResult>()
    override val bleEvent: SharedFlow<ConnectionResult> get() = _bleEvent

    init{
        Log.i(TAG,"IN andorid cont...")
        Log.i("AndroidBluetoothLEController", "Context: $context")

        GlobalScope.launch(Dispatchers.Main) {
            scannedDevices.collect { devices ->
                // Log or perform actions before stopping discovery
                Log.i(TAG, "Scanned Devices before stopping discovery: $devices")
            }
        }
    }

//    override fun initialize() {
//        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT) && hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
//            //Log.i(TAG,"Setting Adapter name to unknown_SavMed")
//            setAdapterName()
//            bleClient = BLEClient(context)
//            bleServer = BLEServer(context)
//
//            if(hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE)) {
//                bleServer.setUpBle()
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                    bleServer.startAdvertise()
//                } else {
//                    Log.i(TAG,"ble advertise not spported")
//                }
//            } else {
//                Log.i(TAG,"Ble adv not granted...")
//            }
//
//        }
//    }
//
//    fun addDeviceToSavMedDevicesList() {
//        Log.i(TAG,"uoooo")
//    }

//    fun addDeviceToScannedDeviceList(result: ScanResult, isSavMed: Boolean) {
//
//        // Add Calculation for dist
//        val newDevice = BluetoothLEScannedDevices(
//            deviceName = result.device.name ?: "N/A",
//            address = result.device.address,
//            rssi = result.rssi.toString(),
//            isSavMed = isSavMed
//        )
//
//        _scannedDevices.update { devices ->
//            val mutableDevices = devices.toMutableList()
//            val existingDeviceIndex = devices.indexOfFirst { it.address == newDevice.address }
//
//            if (existingDeviceIndex != -1) {
//                mutableDevices[existingDeviceIndex] = newDevice // Replace the existing device
//            } else {
//                mutableDevices.add(newDevice) // Add the new device
//            }
//            mutableDevices
//        }
//
//        for(devices in scannedDevices.value) {
//            Log.i(TAG,"devocezzz ${devices.address}")
//        }
//    }

//    final override fun setAdapterName() {
//        bluetoothAdapter?.name = "${Build.MODEL},${Build.MANUFACTURER}"
//    }
//
//    override fun startDiscovery() {
//        bleClient = BLEClient(context)
//        bleClient.startBLEScan()
//    }
//
//    override fun stopDiscovery() {
//        Log.i(TAG,"yooooo in stop disc")
//
//        for(devices in scannedDevices.value) {
//            Log.i(TAG,"devocezz------z ${devices.address}")
//        }
//
//    }

//    override fun sendMessage(device: BluetoothLEScannedDevices) {
//        bleClient.writeCharacteristic(device,"")
//    }

//    final override fun hasPermission(permission: String): Boolean {
//        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
//    }

//    private val leScanCallback: ScanCallback = object : ScanCallback() {
//
//        // println("in call back");
//        @SuppressLint("MissingPermission")
//        override fun onScanResult(callbackType: Int, result: ScanResult) {
//            super.onScanResult(callbackType, result)
//            Log.i(TAG,"Device Name: ${result.device.name},Address: ${result.device.address} RSSI: ${result.rssi} sevice data: ${result.scanRecord?.serviceUuids} ")
//            processScanResult(result)
//        }
//    }

    // Callback for Hosted Gatt Server.


    // CallBack For Connecting To Host gattServer
//    private val BLEgattCallBack = object: BluetoothGattCallback() {
//
//        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
//            super.onConnectionStateChange(gatt, status, newState)
//
//            if (newState == BluetoothProfile.STATE_CONNECTED) {
//
//                Log.i(TAG,"Successful Connection!")
//                bluetoothGatt?.discoverServices()
//                _isConnected.update { true }
//                flow {
//                    emit(ConnectionResult.Success("Connected Successfully!"))
//                }
//
//            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
//
//                Log.i(TAG,"Connection DisConnected!")
//                connectionState = STATE_DISCONNECTED
//                _isConnected.update { false }
//            }
//        }
//
//        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
//            super.onServicesDiscovered(gatt, status)
//            Log.i(TAG,"In disssssssssssssssssssssss")
//            if(status == BluetoothGatt.GATT_SUCCESS) {
//                Log.i(TAG,"SErvice Discovered")
//                val services = getSupportedGattService()
//                for (service in services ?: emptyList()) {
//                    Log.i(TAG, "Service UUID: ${service?.uuid}")
//                    // Loop through the characteristics
//                    for (characteristic in service?.characteristics ?: emptyList()) {
//
//                        Log.i(TAG, "Service Character: ${characteristic.uuid}")
//                        val char_uuid = characteristic.uuid.toString()
//
//                        if (char_uuid.equals(CHARACTERISTIC_UUID)) {
//                            // Read the value of the characteristic
//                            bluetoothGatt?.readCharacteristic(characteristic)
//                        }
//                    }
//                }
//
//            } else {
//                Log.w(TAG,"onServiceDiscovered received: $status")
//            }
//        }
//
//        @Deprecated("Deprecated in Java")
//        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
//            super.onCharacteristicRead(gatt, characteristic, status)
//            if (status == BluetoothGatt.GATT_SUCCESS) {
//                val value = characteristic.value
//                val valueString = String(value ?: ByteArray(0)) // Convert byte array to string
//                Log.i(TAG, "Characteristic Value-----: $valueString")
//
//                _savMedDevices.update { devices ->
//                    val existingDeviceIndex = devices.indexOfFirst { device ->
//                        device.address == gatt.device.address
//                    }
//                    if (existingDeviceIndex != -1) {
//                        devices[existingDeviceIndex].name = valueString
//                    }
//                    devices
//                }
//
//                if (SharedPreference.username.isNotEmpty()) {
//                    writeCharacteristic(characteristic,SharedPreference.username.toByteArray())
//                } else {
//                    writeCharacteristic(characteristic,"unknown_SavMed".toByteArray())
//                }
//
//            } else {
//                Log.w(TAG, "Error reading characteristic: $status")
//            }
//        }

//        @SuppressLint("MissingPermission")
//        fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, payload: ByteArray) {
//            Log.i(TAG,"In write...")
//            characteristic.value = payload
//            Log.i(TAG,"char data---: ${String(characteristic.value ?: ByteArray(0))}")
//            bluetoothGatt?.writeCharacteristic(characteristic)
//        }

//        override fun onCharacteristicWrite(
//            gatt: BluetoothGatt,
//            characteristic: BluetoothGattCharacteristic,
//            status: Int
//        ) {
//            if (status == BluetoothGatt.GATT_SUCCESS) {
//                Log.i(TAG, "Characteristic write successful: ${characteristic.value.toString(Charsets.UTF_8)}")
//            } else {
//                Log.e(TAG, "Characteristic write failed with status: $status")
//            }
//        }
//    }

//    @SuppressLint("MissingPermission")
//    private fun setUpBle() {
//        if (bluetoothAdapter?.bluetoothLeAdvertiser != null) {
//            bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser!!
//            try {
//                Handler(Looper.getMainLooper()).post {
//                    bluetoothGattServer =
//                        bluetoothManager?.openGattServer(context, gattServerCallback)!!
//                    createBLEServerService()
//                }
//            } catch (e: Exception) {
//                Log.i(TAG, "Error:===== ${e.message}")
//            }
//        } else {
//            Log.e(TAG,"BLE NOt Supported!")
//        }
//    }



//    fun createBLEServerService() {
//
//        Log.i(TAG,"Creating BLE Service...")
//        val serviceUuid = UUID.fromString(SERVICE_UUID)
//        val characteristicUuid = UUID.fromString(CHARACTERISTIC_UUID)
//
//        val service = BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
//
//        val characteristics = BluetoothGattCharacteristic(
//            characteristicUuid,
//            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_WRITE,
//            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
//        )
//
//        if (SharedPreference.username.isNotEmpty()) {
//            characteristics.setValue(SharedPreference.username.toByteArray())
//        } else {
//            characteristics.setValue("unknown_savMed_user".toByteArray())
//        }
//        service.addCharacteristic(characteristics)
//
//        bluetoothGattServer.addService(service)
//    }

    // Start Advertizing Data
//    fun startAdvertise() {
//        val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
//
//        Log.i(TAG,"Creating BLE Advertising Data...")
//        advertiseData = AdvertiseData.Builder()
//            .addServiceUuid(ParcelUuid(UUID.fromString(SERVICE_UUID)))
//            .build()
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//
//            Log.i(TAG,"BLE Support: PHY: ${bluetoothAdapter?.isLe2MPhySupported} CODED: ${bluetoothAdapter?.isLeCodedPhySupported} extended: ${bluetoothAdapter?.isLeExtendedAdvertisingSupported} periodic: ${bluetoothAdapter?.isLePeriodicAdvertisingSupported}")
//
//            val parameters =
//                AdvertisingSetParameters.Builder()
//                    .setScannable(true)
//                    .setLegacyMode(true)
//                    .setConnectable(true)// True by default, but set here as a reminder.
//                    .setInterval(AdvertisingSetParameters.INTERVAL_HIGH)
//                    .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MEDIUM)
//                    .build()
//
//            val AdvertiseCallback = object : AdvertisingSetCallback() {
//                override fun onAdvertisingSetStarted(
//                    advertisingSet: AdvertisingSet,
//                    txPower: Int,
//                    status: Int
//                ) {
//                    Log.i(
//                        TAG, "onAdvertisingSetStarted(): txPower:" + txPower + " , status: "
//                                + status + advertisingSet.setPeriodicAdvertisingData(advertiseData)
//                    )
//
//                }
//
//                override fun onAdvertisingDataSet(advertisingSet: AdvertisingSet, status: Int) {
//                    Log.i(
//                        TAG,
//                        "onAdvertisingDataSet() :status:$status"
//                    )
//                }
//
//                override fun onScanResponseDataSet(advertisingSet: AdvertisingSet, status: Int) {
//                    Log.i(
//                        TAG,
//                        "onScanResponseDataSet(): status:$status"
//                    )
//                }
//
//                override fun onAdvertisingSetStopped(advertisingSet: AdvertisingSet) {
//                    Log.i(TAG, "onAdvertisingSetStopped():")
//                }
//            }
//            advertiser?.startAdvertisingSet(parameters, advertiseData, null, null, null, AdvertiseCallback)
//        } else {
//
//            val settings = AdvertiseSettings.Builder()
//                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY) // Adjust as needed
//                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM) // Adjust as needed
//                .setConnectable(true) // True by default
//                .build()
//
//            val AdvertiseCallback = object : AdvertiseCallback() {
//                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
//                    Log.i(TAG, "Advertising started successfully")
//                }
//
//
//                override fun onStartFailure(errorCode: Int) {
//                    Log.e(TAG, "Advertising failed to start: $errorCode")
//                }
//            }
//
//            advertiser?.startAdvertising(settings, advertiseData,AdvertiseCallback)
//        }
//    }
//
//    override fun connectToDevice(device: BluetoothLEDevice): Flow<ConnectionResult> {
//        Log.i(TAG,"Connecting to BLE Device ${device.address}")
//        return flow {
//            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
//                throw SecurityException("Bluetooth Connect permission Not Granted!")
//            }
//            Log.i(TAG,"Security exception passed ${bluetoothGatt?.device}")
//            bluetoothAdapter.let { adapter ->
//                Log.i(TAG,"Going to tryyyyyyyyyyyyyyyy ${bluetoothGatt?.device}")
//                try {
//                    Log.i(TAG,"In tryyyyyyyyyyyy ${bluetoothGatt?.device}")
//                    val deviceFound = adapter?.getRemoteDevice(device.address)
//                    bluetoothGatt = deviceFound?.connectGatt(context,false,BLEgattCallBack)
//                    Log.i(TAG,"AFter ConnectGatt ${bluetoothGatt?.device}")
//                    emit(ConnectionResult.Success("Successfully Connected To ${device.address}"))
//
//                } catch (e: IOException) {
//                    emit(ConnectionResult.Error("Error Connecting To ${device.address}"))
//                }
//            }
//        }.onCompletion {
//
//        }.flowOn(Dispatchers.IO)
//    }
//
//    private fun processScanResult(result: ScanResult) {
//
//        Log.i(TAG,"processss.....")
//        val serviceUuids = result.scanRecord?.serviceUuids
//
//        val newDevice = BluetoothLEDevice(
//            name = result.device.name ?: "none",
//            address = result.device.address,
//            rssi = result.rssi.toString()
//        )
//
//        _scannedDevices.update { devices ->
//            //Log.i("[FSubscriber]",device.address)
//            val existingDeviceIndex = devices.indexOfFirst { device ->
//                device.address == newDevice.address
//            }
//            val updatedDevices = if (existingDeviceIndex != -1) {
//                devices.toMutableList().apply { removeAt(existingDeviceIndex) }
//            } else {
//                devices.toMutableList()
//            }
//            updatedDevices + newDevice
//        }
//
//        if (serviceUuids != null) {
//            for(uuid in serviceUuids) {
//                val uuids = uuid.toString()
//                if(uuids.equals(SERVICE_UUID)) {
//
//                    Log.i(TAG,"SavMed uuid found $uuid")
//
//                    _savMedDevices.update { devices ->
//
//                        val existingDeviceIndex = devices.indexOfFirst { device ->
//                            device.address == newDevice.address
//                        }
//                        val updatedDevices = if (existingDeviceIndex != -1) {
//                            Log.i(TAG,"Similar Device Found ${newDevice.address} Removing it")
//                            devices.toMutableList().apply { removeAt(existingDeviceIndex) }
//                        } else {
//                            Log.i(TAG,"Not match found for ${newDevice.address}")
//                            devices.toMutableList()
//                        }
//                        updatedDevices + newDevice
//                    }
//                    GlobalScope.launch { connectToDevice(newDevice).collect { result ->
//                        when (result) {
//                            is ConnectionResult.Success -> {
//                                Log.i(TAG, result.message)
//                            }
//                            is ConnectionResult.Error -> {
//                                Log.e(TAG, result.message)
//                            }
//                            else -> { }
//                        }
//                    }
//
//                    }
//                    stopDiscovery()
//
//                } else {
//                    Log.i(TAG,"SavMed uuid not found $uuid")
//                }
//            }
//        } else {
//            Log.i(TAG,"No service uuid found")
//        }
//    }


}