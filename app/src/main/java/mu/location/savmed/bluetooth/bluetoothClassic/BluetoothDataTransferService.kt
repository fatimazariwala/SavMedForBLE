package mu.location.savmed.bluetooth.bluetoothClassic

import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import mu.location.savmed.bluetooth.bluetoothClassic.models.BluetoothMessage
import mu.location.savmed.bluetooth.bluetoothClassic.models.toBluetoothMessage
import java.io.IOException

class BluetoothDataTransferService(
    private val socket: BluetoothSocket                     // Passing Bluetooth Client socket
) {
    fun listenForIncomingMessages(): Flow<BluetoothMessage> {
        return flow {
            if(!socket.isConnected) {
                return@flow
            }
            val buffer = ByteArray(1024)
            while(true) {
                val byteCount = try {
                    socket.inputStream.read(buffer)
                } catch (e : IOException) {
                    throw IOException("Reading incoming data failed")
                }

                emit(
                    buffer.decodeToString(
                        endIndex = byteCount
                    ).toBluetoothMessage(
                        isFromLocalUser = false
                    )
                )
            }
        }.flowOn(Dispatchers.IO)
    }

    suspend fun sendMessage(bytes: ByteArray): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                socket.outputStream.write(bytes)
            } catch (e: IOException) {
                e.printStackTrace()
                return@withContext false
            }
            true
        }
    }


}