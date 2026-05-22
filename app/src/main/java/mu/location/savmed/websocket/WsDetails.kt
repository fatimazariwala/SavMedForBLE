package mu.location.savmed.websocket

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.location.savmed.SavMed.Companion.bleClient
import mu.location.savmed.SavMed.Companion.bleServer
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.bluetooth.bluetoothLE.models.GlobalEventTriggers
import mu.location.savmed.utils.SharedPreference
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

// Will be initializied using Application Context
class WsDetails (context: Context) {

    companion object {
        const val TAG = "WsDetails"
    }

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var webSocket: okhttp3.WebSocket

    val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS) // Enable automatic pings every 30 seconds
        .build()

    private val gson = Gson()
    val isConnected = MutableLiveData<Boolean>()
    var isDisconnectDueToNetworkChange = false

    var initEstablished = false
    val join_key = MutableLiveData<String>()
    var enableJoin = false
    val errorMessage = MutableLiveData<String>()

    var destroyCurrent = false
    var changeSession = false

    val onPeerLocationEvent = MutableLiveData<HashMap<String,peerLatLon>>()

    init {
        isConnected.postValue(false)
        join_key.postValue("")
    }

    fun connect() {

        if (isConnected.value == false) {
            val request = Request.Builder().url("ws://x.x.x.x").build()
            Log.i(TAG, "in COnnect......Ws")

            webSocket = client.newWebSocket(request, object : WebSocketListener() {

                override fun onOpen(webSocket: okhttp3.WebSocket, response: Response) {
                    super.onOpen(webSocket, response)
                    Log.i(TAG, "Connection opened: ${response.message}")
                    isConnected.postValue(true)

                    coroutineScope.launch {
                        delay(500)
                        Log.i(TAG,"In open showed valu eof isConnected ${isConnected.value}")
                        if (enableJoin) {
                            Log.i(TAG,"In enable Join")
                            join()
                        } else if (isDisconnectDueToNetworkChange) {
                            Log.i(TAG,"IN network plroerjfj")
                            join()
                            isDisconnectDueToNetworkChange = false
                        } else {
                            Log.i(TAG,"going to initiate!!")
                            initiate()
                        }
                    }

                }

                override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                    super.onMessage(webSocket, text)
                    Log.i(TAG, "Message received: $text")
                    processReceivedMessage(text)
                }

                override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                    super.onClosed(webSocket, code, reason)
                    Log.i(TAG, "Connection closed: $reason")
                    initEstablished = false
                    isConnected.postValue(false)
                }

                override fun onFailure(
                    webSocket: okhttp3.WebSocket,
                    t: Throwable,
                    response: Response?
                ) {
                    super.onFailure(webSocket, t, response)
                    Log.e(TAG, "WebSocket failure Cause: ${response?.message}, Message: ${t.message}Response Code: ${response?.code}")
                    isConnected.postValue(false)
                    if (t.message == "Software caused connection abort") {
                        isDisconnectDueToNetworkChange = true
                    }
                    initEstablished = false
                }
            })
        } else {
            Log.i(TAG,"WS Connection Already Initiated!")
        }
    }

    fun processReceivedMessage(text: String) {
        Log.i(TAG,"In process recened Message....ws")

        when {
            text.contains("init") -> {

                val join: joinData = Gson().fromJson(text, joinData::class.java)
                Log.i(TAG,"received Join_key: ${join.join}")
                join_key.postValue(join.join)
                if (bleClient.activeGattConnections.isNotEmpty()) {
                    Log.i(TAG,"in send to active get connections")
                    bleClient.sendJoinKey(join.join)
                }
                initEstablished = true
                sendLocationMessage(coreContext.onLocationEvent.value?.get("latitude") ?: 0.0,coreContext.onLocationEvent.value?.get("longitude") ?: 0.0)

            }
            text.contains("Location Data") -> {

                val peerDetails: peerDetails = Gson().fromJson(text,peerDetails::class.java)
                Log.i(TAG,"Peer Data: $peerDetails")
                checkNaddToHashMap(peerDetails)

            }
            text.contains("error") -> {

                val error: otherMessage = Gson().fromJson(text,otherMessage::class.java)
                if (error.message.contains("Join Key NOT Found:")) {
                    errorMessage.postValue("KEY_NOT_FOUND")
                }

            }
            text.contains("connection_message") -> {
                val connMessage: otherMessage = Gson().fromJson(text,otherMessage::class.java)
                coroutineScope.launch {
                    coreContext._globalEvents.emit (
                        GlobalEventTriggers.WsMessages(connMessage.message)
                    )
                }
                if (connMessage.message.contains("Destroyed")) {
                    Log.i(TAG,"Destroy Message Received")
                    if (!join_key.value.isNullOrEmpty()) {
                        if (connMessage.message.contains(join_key.value!!)) {
                            Log.i(TAG,"Performing DIsconnect!")
                            destroyCurrent = false
                            disConnect()
                        }
                    }
                }
            }
        }
    }

    fun initiate() {
        Log.i(TAG,"COnnect VAlue,,,: ${isConnected.value}")
        if (isConnected.value == true) {
            Log.i(TAG,"in initewhcwedwjoi")
            val message = mapOf("type" to "init","person" to SharedPreference.username)
            webSocket.send(gson.toJson(message))
        }
    }

    fun join() {
        Log.i(TAG,"Connected val duting join: ${isConnected.value},JOinkey: ${join_key.value}")
        if (join_key.value != null) {
            val message = mapOf("type" to "init", "join" to join_key.value,"person" to SharedPreference.username)
            webSocket.send(gson.toJson(message))
            initEstablished = true

            coroutineScope.launch {
                delay(500)
                sendLocationMessage(coreContext.onLocationEvent.value?.get("latitude") ?: 0.0,coreContext.onLocationEvent.value?.get("longitude") ?: 0.0)
            }

        } else {
            Log.i(TAG,"Join Key Value NUll: ${join_key.value}")
        }
    }

    fun sendLocationMessage(lat: Double?,lon: Double?) {
        Log.i(TAG,"Location Data,,,,: ${lat},${lon}")
        if (isConnected.value == true && initEstablished) {
            val message = mapOf(
                "type" to "Location Data",
                "person" to SharedPreference.username,
                "latitude" to lat.toString(),
                "longitude" to lon.toString()
            )
            webSocket.send(gson.toJson(message))
        } else {
            Log.i(TAG,"Could not send location, InitEstabled: ${initEstablished},isConnected: ${isConnected.value}")
        }
    }

    fun sendDeleteMessage() {
        Log.i(TAG,"Deleting ${join_key.value}")
        if (isConnected.value == true) {
            val message = mapOf(
                "type" to "destroy",
                "person" to SharedPreference.username,
                "join" to join_key.value
            )
            webSocket.send(gson.toJson(message))
        } else {
            Log.i(TAG,"Could not send location, InitEstabled: ${initEstablished},isConnected: ${isConnected.value}")
        }
    }

    fun disConnect() {
        enableJoin = false
        initEstablished = false
        if (isConnected.value == true) {
            if (destroyCurrent) {
                sendDeleteMessage()
            } else {
                isDisconnectDueToNetworkChange = false
                webSocket.cancel()
            }
        }
        isConnected.value = false
    }

    fun checkNaddToHashMap(peerDetails: peerDetails) {
        val hashMap = onPeerLocationEvent.value ?: HashMap()

        if (!hashMap.containsKey(peerDetails.person)) {
            hashMap[peerDetails.person] = peerLatLon(
                peerDetails.latitude,
                peerDetails.longitude
            )
        } else {
            val data = hashMap[peerDetails.person]
            if (data != null) {
                data.latitude = peerDetails.latitude
                data.longitude = peerDetails.longitude
            }
        }
        onPeerLocationEvent.postValue(hashMap)
    }
}
