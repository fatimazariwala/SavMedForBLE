package mu.location.savmed.bluetooth.bluetoothLE.controls

import android.Manifest
import android.annotation.SuppressLint
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
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
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
import mu.location.savmed.bluetooth.bluetoothLE.models.ConnectionResult
import mu.location.savmed.bluetooth.bluetoothLE.models.writeMessage
import mu.location.savmed.models.CoreContext
import mu.location.savmed.models.CoreContext.Companion
import mu.location.savmed.ui.call.CallActivity
import mu.location.savmed.utils.SettingsManager.hasPermission
import mu.location.savmed.utils.SharedPreference
import java.util.UUID

//@SuppressLint("MissingPermission")
class BLEServer(
   val context: Context
) {

    companion object {
        const val TAG = "[BLE Server]"
        const val SERVICE_UUID = "27b7d1da-08c7-4505-a6d1-2459987e5e2d"
        const val CHARACTERISTIC_USERNAME_UUID = "87654321-4321-6789-4321-fedcba987654"
        const val CHARACTERISTIC_MESSAGE_UUID = "ba987654-4321-6789-4321-000087654321"
    }

    val messageReceivedFromBLE = MutableLiveData<writeMessage>()

    private var coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var advertiseData: AdvertiseData ?= null
    var isPrevMessage = true

    lateinit var bluetoothLeAdvertiser: BluetoothLeAdvertiser
    lateinit var bluetoothGattServer: BluetoothGattServer

    private val _bleServerEvent = MutableSharedFlow<ConnectionResult>()
    val bleServerEvent: SharedFlow<ConnectionResult>
        get() = _bleServerEvent

   // val mesgReciwd = MutableLiveData<String>()

    private val _listOfMessages = MutableStateFlow<List<writeMessage>>(emptyList())
    val listOfMessages: StateFlow<List<writeMessage>>
        get() = _listOfMessages.asStateFlow()

    init {
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
                flow {
                    emit(ConnectionResult.ConnectionEstablished)
                }

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {

                Log.i(TAG,"Device Disconnected ${device?.address}")
                flow {
                    emit(ConnectionResult.Error("Connection DIsConnected: $status"))
                }
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

            coreContext.postOnMainThread {
                showMessageActivity()
            }
            isPrevMessage = false
            processWriteRequest(device,value)

            coroutineScope.launch {
                _bleServerEvent.emit(
                    ConnectionResult.Success("BLE Write Message Received!")
                )
            }

            bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }
    }

    private fun processWriteRequest(device: BluetoothDevice, value: ByteArray) {

        val received = String(value).split('#')

        Log.i(TAG,"in receuved ${received.size} ${received.lastOrNull()}")

        val defaultFrom = "Unknown"
        val defaultDist = 0.0
        val defaultLat = 0.0
        val defaultLon = 0.0

        val msg = writeMessage(
            From = received.getOrElse(0) { defaultFrom },
            dist = received.getOrElse(1) { defaultDist.toString() }.toDouble(),
            lat = received.getOrElse(2) { defaultLat.toString() }.toDouble(),
            lon = received.getOrElse(3) { defaultLon.toString() }.toDouble()
        )
        messageReceivedFromBLE.postValue(
            msg
        )

        coreContext.notificationManager.createBleMessageNotification(msg)

        _listOfMessages.update { messages ->
            Log.i(TAG,"add messageing to list ${msg.From} ${msg.dist}")
            for (message in messages) {
                Log.i(TAG,"${message.From} : ----ioioi")
            }
            val updatedMessages = messages + msg
            Log.i(TAG, "in am message ${updatedMessages.lastOrNull()?.From} ${updatedMessages.lastOrNull()?.From}")
            updatedMessages
        }

    }

    @SuppressLint("MissingPermission")
    fun setUpBle() {
        if (bluetoothAdapter?.bluetoothLeAdvertiser != null) {
            bluetoothAdapter?.name = "${Build.MODEL},${Build.MANUFACTURER}"
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
        val characteristicUuid_USER = UUID.fromString(CHARACTERISTIC_USERNAME_UUID)
        val characteristicUuid_MESSAGE = UUID.fromString(CHARACTERISTIC_MESSAGE_UUID)

        val service = BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val characteristics_username = BluetoothGattCharacteristic(
            characteristicUuid_USER,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        if (SharedPreference.username.isNotEmpty()) {
            Log.i(TAG,"Found ${SharedPreference.username} setting it as characteristic params")
            characteristics_username.setValue("${SharedPreference.username}#${Build.MANUFACTURER}")

            Log.i(TAG,"In am shared prefValue ${SharedPreference.username}")
        } else {
            Log.i(TAG,"Could not set Username in characteristic FOUND EMPTY")
            characteristics_username.setValue("unknown_savMed_user")
        }

        val characteristic_message = BluetoothGattCharacteristic(
            characteristicUuid_MESSAGE,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        characteristic_message.setValue("")

        service.addCharacteristic(characteristics_username)
        service.addCharacteristic(characteristic_message)

        bluetoothGattServer.addService(service)
    }

    @SuppressLint("MissingPermission")
    fun startAdvertise() {
        val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser

        Log.i(TAG,"Creating BLE Advertising Data...")
        advertiseData = AdvertiseData.Builder()
            //.setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(UUID.fromString(SERVICE_UUID)))
            .build()

        val extraAdvertiseData = AdvertiseData.Builder()
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
                    .setInterval(AdvertisingSetParameters.INTERVAL_HIGH)
                    .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MEDIUM)
                    .build()

            val AdvertiseCallback = object : AdvertisingSetCallback() {
                override fun onAdvertisingSetStarted(
                    advertisingSet: AdvertisingSet,
                    txPower: Int,
                    status: Int
                ) {
                    Log.i(
                        TAG, "onAdvertisingSetStarted(): txPower:" + txPower + " , status: "
                                + status + advertisingSet.setPeriodicAdvertisingData(extraAdvertiseData)
                    )
                   // advertisingSet.setAdvertisingData(extraAdvertiseData)

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
                }
            }
            advertiser?.startAdvertisingSet(parameters, advertiseData, null, null, null, AdvertiseCallback)
        } else {

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY) // Adjust as needed
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM) // Adjust as needed
                .setConnectable(true) // True by default
                .build()

            val AdvertiseCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                    Log.i(TAG, "Advertising started successfully")
                }


                override fun onStartFailure(errorCode: Int) {
                    Log.e(TAG, "Advertising failed to start: $errorCode")
                }
            }

            advertiser?.startAdvertising(settings, advertiseData,AdvertiseCallback)
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

}