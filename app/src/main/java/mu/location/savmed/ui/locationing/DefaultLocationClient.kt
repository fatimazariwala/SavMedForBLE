package mu.location.savmed.ui.locationing

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.SavMed.Companion.isWebSocketInitialized
import mu.location.savmed.SavMed.Companion.webSocket
import mu.location.savmed.utils.RetrofitInstance
import retrofit2.Call
import retrofit2.Callback
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date

class DefaultLocationClient(
    private val context: Context,
    private val client: FusedLocationProviderClient
    )  {

    companion object {
        const val TAG = "[Default Location Client]"
    }
    val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @SuppressLint("SimpleDateFormat")
    fun getDateTime() : String {
        val date = Date()
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val formattedDate = formatter.format(date)
        return formattedDate
    }

    fun getLocationUpdates(interval : Long): Flow<Location> {
        return callbackFlow {
            Log.i("in def","indef")
            if(ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
                Log.i("[Location Permission]","Permission Not Granted!")
                throw Exception("Location permission Unavailable!")
            } else {
                coreContext.isLocationGranted = true

                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                if (!isGpsEnabled) {
                    throw Exception("GPS Disabled")
                } else if (!isNetworkEnabled) {
                    throw Exception("Network Disabled")
                }
                val request = LocationRequest.create()
                    .setInterval(interval)
                    .setFastestInterval(interval)

                val locationCallback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        super.onLocationResult(result)
                        result.locations.lastOrNull()?.let { location ->
                            launch {
//                                coreContext.core.defaultAccount?.setCustomHeader("latitude",location.latitude.toString())
//                                coreContext.core.defaultAccount?.setCustomHeader("longitude",location.longitude.toString())
                                Log.i(TAG,"Value of OnLocation Event: ${coreContext.onLocationEvent.value?.get("latitude")}")
                                if (coreContext.onLocationEvent.value?.get("latitude") != location.longitude || coreContext.onLocationEvent.value?.get("longitude") != location.latitude) {
                                    if (isWebSocketInitialized() && webSocket.isConnected.value == true) {
                                        webSocket.sendLocationMessage(
                                            lat = location.latitude,
                                            lon = location.longitude
                                        )
                                    } else {
                                        if (!isWebSocketInitialized()) {
                                            Log.i(
                                                TAG,
                                                "Skipping WebSocket SEnd, Websocket not init: ${isWebSocketInitialized()}"
                                            )
                                        } else if (webSocket.isConnected.value != true) {
                                            Log.i(TAG, "Websocket Not connected: ${webSocket.isConnected.value}")
                                        }

                                    }
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val gson = Gson();
                                        val LocJson = gson.toJson(liveLocationData(
                                            location.latitude, location.longitude,
                                            0, "", "live", getDateTime(),
                                            coreContext.core.defaultAccount?.params?.identityAddress?.username.toString()
                                        ));
                                        Log.i("[Location Client]", LocJson);

                                        val call: Call<liveLocationData?>? = try {

                                            RetrofitInstance.apiLiveLocation.postLiveLocationData(liveLocationData(
                                                location.latitude, location.longitude,
                                                0, "", "live", getDateTime(),
                                                coreContext.core.defaultAccount?.params?.identityAddress?.username.toString()
                                            ))

                                        } catch (e: IOException) {
                                            Log.i("[Location Client]", e.message.toString())
                                            return@launch
                                        } catch (e: HttpException) {
                                            Log.i("[Location Client]", e.message.toString())
                                            return@launch
                                        }

                                        call?.enqueue(object: Callback<liveLocationData?> {
                                            override fun onResponse(
                                                call: Call<liveLocationData?>,
                                                response: Response<liveLocationData?>
                                            ) {
                                                val responz = response.body()
                                                Log.i("[Location Client]","Response : ${responz?.lat},${responz?.userName},${responz?.sqlStatus}")
                                            }

                                            override fun onFailure(
                                                call: Call<liveLocationData?>,
                                                t: Throwable
                                            ) {
                                                Log.i("[Location Client]","Response : Failure ${t.message}")
                                            }
                                        })
                                    }
                                    send(location)
                                } else {
                                    Log.i(TAG,"Location Value Not Changed!")
                                }
                            }
                        }
                    }
                }

                client.requestLocationUpdates(
                    request,
                    locationCallback,
                    Looper.getMainLooper()
                )

                awaitClose {
                    client.removeLocationUpdates(locationCallback)
                }
            }
        }
    }
}