package mu.location.savmed.bluetooth.bluetoothLE.controls

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mu.location.savmed.MainActivity
import mu.location.savmed.SavMed.Companion.bluetoothAdapter
import mu.location.savmed.SavMed.Companion.bluetoothManager
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.SavMed.Companion.webSocket
import mu.location.savmed.bluetooth.bluetoothLE.BroadCast.BluetoothBroadcastReceiver
import mu.location.savmed.bluetooth.bluetoothLE.models.GlobalEventTriggers
import mu.location.savmed.bluetooth.bluetoothLE.models.LocationChar
import mu.location.savmed.bluetooth.bluetoothLE.models.writeMessage
import mu.location.savmed.models.CoreContext
import mu.location.savmed.models.CoreContext.Companion
import mu.location.savmed.ui.call.CallActivity
import mu.location.savmed.utils.SettingsManager.hasPermission
import mu.location.savmed.utils.SharedPreference
import java.util.UUID

@SuppressLint("MissingPermission")
class BLEServer(
   val context: Context
) {

    companion object {
        const val TAG = "[BLE Server]"
        const val SERVICE_UUID = "27b7d1da-08c7-4505-a6d1-2459987e5e2d"
        const val CHARACTERISTIC_USER_NAME_UUID = "87654321-4321-6789-4321-fedcba987654"
        const val CHARACTERISTIC_OIDENTITY_UUID = "46778467-4321-6789-4321-087654321000"
        const val CHARACTERISTIC_JOIN_KEY_UUID = "98765432-4321-6789-4321-087654321000"
    }

    val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser

    val messageReceivedFromBLE = MutableLiveData<writeMessage>()

    var advertisingState = false

    private var coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var advertiseData: AdvertiseData ?= null
    private var extraAdvertiseData: AdvertiseData ?= null
    var isPrevMessage = true

    val bluetoothBroadcastReceiver = BluetoothBroadcastReceiver { isBluetoothON ->
        Log.i(TAG,"B;uetooth State: ${isBluetoothON}, ADv state: ${advertisingState}")
        if (advertisingState == false) {
            Log.i(TAG,"Starting Advertise again...")

            try {
                setUpBle()
                startAdvertise()
            } catch (e: Exception) {
                Log.i(TAG,"Advertising ALreday there")
            }

        }
    }

    lateinit var bluetoothLeAdvertiser: BluetoothLeAdvertiser
    lateinit var bluetoothGattServer: BluetoothGattServer

    private lateinit var service: BluetoothGattService
    var charFrom: String ?= ""

   // val mesgReciwd = MutableLiveData<String>()

    private val _listOfMessages = MutableStateFlow<List<writeMessage>>(emptyList())
    val listOfMessages: StateFlow<List<writeMessage>>
        get() = _listOfMessages.asStateFlow()


    val AdvertiseSetCallback = @RequiresApi(Build.VERSION_CODES.O)
    object : AdvertisingSetCallback() {

        override fun onAdvertisingSetStarted(
            advertisingSet: AdvertisingSet,
            txPower: Int,
            status: Int
        ) {
            Log.i(
                TAG, "onAdvertisingSetStarted(): txPower:" + txPower + " , status: "
                        + status + advertisingSet.setPeriodicAdvertisingData(extraAdvertiseData)
            )

            advertisingState = true
            //advertisingSet.setAdvertisingData(extraAdvertiseData)
        }

        override fun onAdvertisingDataSet(advertisingSet: AdvertisingSet, status: Int) {
            Log.i(
                TAG,
                "onAdvertisingDataSet() :status:$status"
            )
        }

        override fun onScanResponseDataSet(advertisingSet: AdvertisingSet, status: Int) {
            Log.i(
                TAG,
                "onScanResponseDataSet(): status:$status"
            )
        }

        override fun onAdvertisingSetStopped(advertisingSet: AdvertisingSet) {
            Log.i(TAG, "onAdvertisingSetStopped():")
            advertisingState = false
        }
    }

    val AdvertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.i(TAG, "Advertising started successfully")
        }


        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "Advertising failed to start: $errorCode")
        }
    }


    @SuppressLint("MissingPermission")
    private val gattServerCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            Log.i(TAG,"Device: ${device?.address}, Status: ${status}, newState: ${newState}")

            if (newState == BluetoothProfile.STATE_CONNECTING) {
                Log.i(TAG,"In connecting...")
            }
            if(newState == BluetoothProfile.STATE_CONNECTED) {

                Log.i(TAG,"Device Connected ${device?.address}")
                stopAdvertise()

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {

                Log.i(TAG,"Device Disconnected ${device?.address} Bluetooth LE Advertisinser: ${advertiser}")
                startAdvertise()
            }
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            Log.i(TAG, "Characteristic read request: ${characteristic.uuid}")
            bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.value)
        }

        override fun onCharacteristicWriteRequest(device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
            characteristic.setValue(value)
            Log.i(TAG, "Characteristic write request: ${characteristic.uuid} ${String(characteristic.value ?: ByteArray(0))}")
            Log.i(TAG,"Sending Response to Client ${device.address} [${bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)}]")

            coreContext.postOnMainThread {
                showMessageActivity()
            }
            isPrevMessage = false
            processWriteRequest(characteristic,value)
        }
    }

    init {
        Log.i(TAG,"in int setting up ble")
        if(hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE,context)) {
            if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT,context)) {
                setUpBle()
            } else {
                Log.e(TAG,"Could not SETup BLE Server Permission Issues!!")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startAdvertise()
            } else {
                Log.i(TAG,"ble advertise not supported")
            }
        } else {
            Log.i(TAG,"Ble adv not granted...")
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }

        context.registerReceiver(bluetoothBroadcastReceiver,filter)
    }

    private fun processWriteRequest(characteristic: BluetoothGattCharacteristic, value: ByteArray) {

        val recv = String(value)
        val received = if (recv.contains('#')) {
            String(value).split('#')
        } else {
            emptyList()
        }

        val characteristicToString = characteristic.uuid.toString()

        Log.i(TAG,"in received ${received.size} ${received.lastOrNull()}")

        val defaultFrom = "Unknown"
        val defaultDist = 0.0

        Log.i(TAG,"Char received $characteristicToString identity = $CHARACTERISTIC_OIDENTITY_UUID loc = $CHARACTERISTIC_JOIN_KEY_UUID")
        if (characteristicToString.equals(CHARACTERISTIC_OIDENTITY_UUID)) {
            if (received.isNotEmpty()) {
                val msg = writeMessage(
                    From = received.getOrElse(0) { defaultFrom },
                    dist = received.getOrElse(1) { defaultDist.toString() }.toDouble(),
                    joinKey = ""
                )

                charFrom = msg.From

                messageReceivedFromBLE.postValue(
                    msg
                )

                _listOfMessages.update { messages ->
                    Log.i(TAG, "add messageing to list ${msg.From} ${msg.dist}")
                    for (message in messages) {
                        Log.i(TAG, "${message.From} : ----ioioi")
                    }
                    val updatedMessages = messages + msg
                    Log.i(
                        TAG,
                        "in am message ${updatedMessages.lastOrNull()?.From} ${updatedMessages.lastOrNull()?.From}"
                    )
                    updatedMessages
                }
            } else {
                Log.i(TAG,"Received Message Empty!")
            }

        } else if (characteristicToString.equals(CHARACTERISTIC_JOIN_KEY_UUID)) {

            Log.i(TAG,"FOund SOme uuid... OLOC $recv")

            coreContext.performLiveLocJOIN(recv)

            if (webSocket.join_key.value != recv && !webSocket.join_key.value.isNullOrEmpty()) {
                Log.i(TAG,"JoinKey Value ${webSocket.join_key.value}")

                coroutineScope.launch {
                    coreContext._globalEvents.emit(
                        GlobalEventTriggers.LiveLocationCheck
                    )
                }
                coreContext.fetchedJoinKey = recv
            } else {
                Log.i(TAG,"JoinKey Value in else block ${webSocket.join_key.value}")
            }

            _listOfMessages.update { messages ->

                val existingIndex = messages.indexOfFirst { message ->
                    message.From == charFrom
                }

                if (existingIndex != -1) {
                    Log.i(TAG,"Found Device with From [${messages[existingIndex].From}] Matching [$charFrom]")
                    val updatedMessages = messages.toMutableList()

                    val updatedMessage = updatedMessages[existingIndex].copy(
                        joinKey = recv
                    )

                    coreContext.notificationManager.createBleMessageNotification(updatedMessage)

                    updatedMessages[existingIndex] = updatedMessage

                    updatedMessages
                } else {
                    Log.i(TAG,"Could not find any device Matching [$charFrom]")
                    messages
                }
            }
        }
    }

    fun setJoinCharacteristic(joinKey: String) {
        if (::service.isInitialized) {
            Log.i(TAG,"in init... ${service.uuid}")
            val characteristic =
                service.getCharacteristic(UUID.fromString(CHARACTERISTIC_JOIN_KEY_UUID))
            characteristic.setValue(joinKey)
            Log.i(TAG, "Join Key updated! [${String(characteristic.value ?: ByteArray(0))}]")
        } else {
            Log.i(TAG,"Service Variable Not initialized yet")
        }
    }

    @SuppressLint("MissingPermission")
    fun setUpBle() {
        if (bluetoothAdapter?.bluetoothLeAdvertiser != null) {
            Log.i(TAG,"in setup BLE")
            bluetoothAdapter?.name = Build.MODEL
            bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser!!
            try {
                Handler(Looper.getMainLooper()).post {
                    bluetoothGattServer =
                        bluetoothManager.openGattServer(context, gattServerCallback)!!
                    createBLEServerService()
                }
            } catch (e: Exception) {
                Log.i(TAG, "Error:===== ${e.message}")
            }
        } else {
            Log.e(TAG,"BLE NOt Supported!")
        }
    }

    @SuppressLint("MissingPermission")
    fun createBLEServerService() {

        Log.i(TAG,"Creating BLE Service...")
        val serviceUuid = UUID.fromString(SERVICE_UUID)
        val characteristicUuid_USER = UUID.fromString(CHARACTERISTIC_USER_NAME_UUID)
        val characteristicUuid_OUSER = UUID.fromString(CHARACTERISTIC_OIDENTITY_UUID)
        val characteristicUuid_JOIN = UUID.fromString(CHARACTERISTIC_JOIN_KEY_UUID)

        service = BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val characteristics_username = BluetoothGattCharacteristic(
            characteristicUuid_USER,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        if (SharedPreference.username.isNotEmpty()) {
            Log.i(TAG,"Found ${SharedPreference.username} setting it as characteristic params")
            val userName = if (SharedPreference.username.contains('.')) {
                SharedPreference.username.split('.')[0]
            } else {
                SharedPreference.username
            }
            characteristics_username.setValue("${userName}#${SharedPreference.priKey}")  // Removed Build.MANUFACTURER.take(7)
            Log.i(TAG,"In am shared prefValue ${userName}#${SharedPreference.priKey}")

        } else {
            Log.i(TAG,"Could not set Username in characteristic FOUND EMPTY")
            characteristics_username.setValue("Saviour#000")
        }

        val characteristic_join = BluetoothGattCharacteristic(
            characteristicUuid_JOIN,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        if (coreContext.isCoreAvailable()) {
            characteristic_join.setValue("")
        } else {
            characteristic_join.setValue("")
            Log.i(TAG,"Core Not initialized!")
        }

        val characteristic_oidentity = BluetoothGattCharacteristic(
            characteristicUuid_OUSER,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        characteristic_oidentity.setValue("")

        service.addCharacteristic(characteristics_username)
        service.addCharacteristic(characteristic_join)
        service.addCharacteristic(characteristic_oidentity)

        bluetoothGattServer.addService(service)
    }

//    fun updateLocCharacteristics(lat: Double?,lon: Double?) {
//
//        val latRounded = lat?.toBigDecimal()?.setScale(4, java.math.RoundingMode.HALF_UP)?.toDouble()
//        val lonRounded = lon?.toBigDecimal()?.setScale(4,java.math.RoundingMode.HALF_UP)?.toDouble()
//
//        if (::service.isInitialized) {
//            Log.i(TAG,"in init... ${service.uuid}")
//            val characteristic =
//                service.getCharacteristic(UUID.fromString(CHARACTERISTIC_USER_LOC_UUID))
//            characteristic.setValue("${latRounded ?: 0.0}#${lonRounded ?: 0.0}")
//            Log.i(TAG, "Loc Characteristics Value updated! [${String(characteristic.value ?: ByteArray(0))}]")
//        } else {
//            Log.i(TAG,"Service Variable Not initialized yet")
//        }
//    }

    @SuppressLint("MissingPermission")
    fun startAdvertise() {
        Log.i(TAG,"Creating BLE Advertising Data...")
        advertiseData = AdvertiseData.Builder()
            //.setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(UUID.fromString(SERVICE_UUID)))
            .build()

        extraAdvertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(UUID.fromString(SERVICE_UUID)))
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            Log.i(TAG,"BLE Support: PHY: ${bluetoothAdapter?.isLe2MPhySupported} CODED: ${bluetoothAdapter?.isLeCodedPhySupported} extended: ${bluetoothAdapter?.isLeExtendedAdvertisingSupported} periodic: ${bluetoothAdapter?.isLePeriodicAdvertisingSupported}")

            val parameters =
                AdvertisingSetParameters.Builder()
                    .setScannable(true)
                    .setLegacyMode(true)
                    .setConnectable(true)// True by default, but set here as a reminder.
                    .setInterval(AdvertisingSetParameters.INTERVAL_LOW)
                    .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MEDIUM)
                    .build()

            advertiser?.startAdvertisingSet(parameters, advertiseData, null, null, null, AdvertiseSetCallback)
        } else {

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY) // Adjust as needed
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM) // Adjust as needed
                .setConnectable(true) // True by default
                .build()

            advertiser?.startAdvertising(settings, advertiseData,AdvertiseCallback)
        }
    }

    fun stopAdvertise() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            advertiser?.stopAdvertisingSet(AdvertiseSetCallback)
        } else {
            advertiser?.stopAdvertising(AdvertiseCallback)
        }
    }

    @UiThread
    fun showMessageActivity() {
        Log.i(TAG,"Starting Main activity For BLE Message")
        val intent = Intent(context, MainActivity::class.java)
        // This flag is required to start an Activity from a Service context
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )
        context.startActivity(intent)
    }

    fun release() {
        context.unregisterReceiver(bluetoothBroadcastReceiver)
    }

    fun refreshAdvertisingState() {
        stopAdvertise()
        startAdvertise()
    }
}