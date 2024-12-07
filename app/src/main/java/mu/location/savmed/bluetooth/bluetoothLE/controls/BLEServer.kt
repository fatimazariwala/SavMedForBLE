package mu.location.savmed.bluetooth.bluetoothLE.controls

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import mu.location.savmed.bluetooth.bluetoothLE.models.ConnectionResult
import mu.location.savmed.bluetooth.bluetoothLE.models.writeMessage
import mu.location.savmed.utils.SharedPreference
import java.util.UUID

@SuppressLint("MissingPermission")
class BLEServer(
   val context: Context
): AndroidBluetoothLEController(context) {

    private var advertiseData: AdvertiseData ?= null

    @SuppressLint("MissingPermission")
    private val gattServerCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            Log.i(TAG,"Device: ${device?.address}, Status: ${status}, newState: ${newState}")

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

            processWriteRequest(device,value)

            flow{ emit(ConnectionResult.Success("BLE Write Message Received!")) }

            bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }
    }

    private fun processWriteRequest(device: BluetoothDevice, value: ByteArray) {

        val received = String(value).split('#')
        val message = writeMessage(
            From = received[0],
            message = received[1]
        )

        _listOfMessages.update { messages ->
            val existingIndex = messages.indexOfFirst { messageZ ->
                messageZ.From == message.From
            }

            if (existingIndex == -1) {
                messages + message
            } else {
                messages.toMutableList().apply {
                    this[existingIndex] = message
                }
            }

            messages
        }

        flow {
            emit (ConnectionResult.BLETransferSucceeded(
                "Help Request Received From ${message.From} -> ${message.message} 3m way"
            ))
        }
    }

    @SuppressLint("MissingPermission")
    fun setUpBle() {
        if (bluetoothAdapter?.bluetoothLeAdvertiser != null) {
            bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser!!
            try {
                Handler(Looper.getMainLooper()).post {
                    bluetoothGattServer =
                        bluetoothManager?.openGattServer(context, gattServerCallback)!!
                    createBLEServerService()
                }
            } catch (e: Exception) {
                Log.i(TAG, "Error:===== ${e.message}")
            }
        } else {
            Log.e(TAG,"BLE NOt Supported!")
        }
    }

    fun createBLEServerService() {

        Log.i(TAG,"Creating BLE Service...")
        val serviceUuid = UUID.fromString(SERVICE_UUID)
        val characteristicUuid = UUID.fromString(CHARACTERISTIC_USERNAME_UUID)

        val service = BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val characteristics_username = BluetoothGattCharacteristic(
            characteristicUuid,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        if (SharedPreference.username.isNotEmpty()) {
            Log.i(TAG,"Found ${SharedPreference.username} setting it as characteristic params")
            characteristics_username.setValue(SharedPreference.username)
        } else {
            Log.i(TAG,"Could not set Username in characteristic FOUND EMPTY")
            characteristics_username.setValue("unknown_savMed_user")
        }

        val characteristic_message = BluetoothGattCharacteristic(
            characteristicUuid,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        characteristic_message.setValue("")

        service.addCharacteristic(characteristics_username)
        service.addCharacteristic(characteristic_message)

        bluetoothGattServer.addService(service)
    }

    fun startAdvertise() {
        val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser

        Log.i(TAG,"Creating BLE Advertising Data...")
        advertiseData = AdvertiseData.Builder()
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
                                + status + advertisingSet.setPeriodicAdvertisingData(advertiseData)
                    )

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
}