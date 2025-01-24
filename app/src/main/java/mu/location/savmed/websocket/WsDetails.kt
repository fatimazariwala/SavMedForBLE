package mu.location.savmed.websocket

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import mu.location.savmed.utils.SharedPreference
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocketListener

// Will be initializied using Application Context
class WsDetails (context: Context) {

    companion object {
        const val TAG = "WsDetails"
    }

    private lateinit var webSocket: okhttp3.WebSocket
    private val client: OkHttpClient = OkHttpClient()
    private val gson = Gson()
    val isConnected = MutableLiveData<Boolean>()

    val join_key = MutableLiveData<String>()
    var enableJoin = false

    val onPeerLocationEvent = MutableLiveData<peerDetails>()

    init {
        isConnected.value = false
    }

    fun connect() {
        if (::webSocket.isInitialized) {
            disConnect()
        }
       // if (isConnected.value == false) {
            val request = Request.Builder().url("ws://212.38.94.76:8001").build()
            Log.i(TAG, "in COnnect......Ws")

            webSocket = client.newWebSocket(request, object : WebSocketListener() {

                override fun onOpen(webSocket: okhttp3.WebSocket, response: Response) {
                    super.onOpen(webSocket, response)
                    Log.i(TAG, "Connection opened: ${response.message}")
                    isConnected.postValue(true)
                    if (enableJoin) {
                        join()
                    }
//                runOnUiThread {
//                    Toast.makeText(context, "Connection Open!", Toast.LENGTH_SHORT).show()
//                }
                }

                override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                    super.onMessage(webSocket, text)
                    Log.i(TAG, "Message received: $text")
                    processReceivedMessage(text)
//                runOnUiThread {
//                    Toast.makeText(this@MainActivity, "Message Received!", Toast.LENGTH_SHORT).show()
//                }
                }

                override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                    super.onClosed(webSocket, code, reason)
                    Log.i(TAG, "Connection closed: $reason")

                    isConnected.postValue(false)

//                runOnUiThread {
//                    Toast.makeText(this@MainActivity, "Connection Closed!", Toast.LENGTH_SHORT).show()
//                }
                }

                override fun onFailure(
                    webSocket: okhttp3.WebSocket,
                    t: Throwable,
                    response: Response?
                ) {
                    super.onFailure(webSocket, t, response)
                    Log.e(TAG, "WebSocket failure", t)
                    isConnected.postValue(false)
//                runOnUiThread {
//                    Toast.makeText(this@MainActivity, "WebSocket Failure!", Toast.LENGTH_SHORT).show()
//                }
                }
            })
//        } else {
//            Log.i(TAG,"WS Connection Already Initiated!")
//        }
    }

    fun processReceivedMessage(text: String) {
        Log.i(TAG,"In process recened Message....ws")
        if (text.contains("init")) {
            val join: joinData = Gson().fromJson(text, joinData::class.java)
            Log.i(TAG,"received Join_key: ${join.join}")
            join_key.postValue(join.join)
        } else if (text.contains("Location Data")) {
            val peerDetails: peerDetails = Gson().fromJson(text,peerDetails::class.java)
            Log.i(TAG,"Peer Data: $peerDetails")
            onPeerLocationEvent.postValue(peerDetails)
        }
    }

    fun initiate() {
        if (isConnected.value == true) {
            val message = mapOf("type" to "init")
            webSocket.send(gson.toJson(message))
        }
    }

    fun join() {
        Log.i(TAG,"COnnected VAl duting join: ${isConnected.value},JOinkey: ${join_key.value}")
        if (join_key.value != null) {
            val message = mapOf("type" to "init", "join" to join_key.value)
            webSocket.send(gson.toJson(message))
        } else {
            Log.i(TAG,"Join Key Value NUll: ${join_key.value}")
        }
    }

    fun sendLocationMessage(lat: Double?,lon: Double?) {
        Log.i(TAG,"Location Data,,,,: ${lat},${lon}")
        if (isConnected.value == true) {
            val message = mapOf(
                "type" to "Location Data",
                "person" to SharedPreference.username,
                "role" to if (enableJoin) "EMR" else "EMH",
                "latitude" to lat.toString(),
                "longitude" to lon.toString()
            )
            webSocket.send(gson.toJson(message))
        }
    }

    fun disConnect() {
        webSocket.cancel()
    }
}