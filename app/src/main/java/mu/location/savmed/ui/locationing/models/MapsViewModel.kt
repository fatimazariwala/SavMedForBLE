package mu.location.savmed.ui.locationing.models

import android.R
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Geocoder
import android.util.Log
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import mu.location.savmed.SavMed.Companion.webSocket
import java.util.Locale
import kotlin.math.ln


class MapsViewModel: ViewModel() {

    companion object {
        const val TAG = "[Maps ViewModel]"
    }

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    var isDirectionsClicked = false

    lateinit var userAtPolyLine: LatLng
    var userMarkerAtPolyLine: Marker?= null
    var userCircleAtPolyLine: Circle?= null

    val manualJoinKey = MutableLiveData<String>()

    init {
        if (!webSocket.join_key.value.isNullOrEmpty()) {
            manualJoinKey.postValue(webSocket.join_key.value)
        }
    }


    fun fetchAddressFromLatLng(latLng: LatLng,context: Context, callback: (String) -> Unit) {
        val geocoder = Geocoder(context, Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            if (addresses != null) {
                if (addresses.isNotEmpty()) {
                    val address = addresses[0].getAddressLine(0)
                    callback(address)
                } else {
                    callback("No address found")
                }
            } else {
                callback("No address found")
            }
        } catch (e: Exception) {
            callback("Unable to fetch address")
        }
    }

    fun vectorToBitmap(context: Context, drawableId: Int): Bitmap {
        val drawable = AppCompatResources.getDrawable(context, drawableId) ?: throw IllegalArgumentException("Invalid drawable ID")
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    fun getZoomLevel(radius: Double): Int {
        Log.i(TAG,"i am radius $radius")
        val scale = radius / 500
        return ((16 - ln(scale) / ln(2.0)).toInt())
    }

//    fun getURL(from : LatLng, to : LatLng) : String {
//        val origin = "origin=" + from.latitude + "," + from.longitude
//        val dest = "destination=" + to.latitude + "," + to.longitude
//        val sensor = "sensor=false"
//        val key = "key=AIzaSyBL3tCKWE9hgJE50EvpFiAshvJeYJy7bfU"
//        val params = "$origin&$dest&$sensor&$key"
//        return "https://maps.googleapis.com/maps/api/directions/json?$params"
//    }

    fun getUrl(origin: LatLng, dest: LatLng, directionMode: String): String {
        val str_origin = "origin=" + origin.latitude + "," + origin.longitude
        val str_dest = "destination=" + dest.latitude + "," + dest.longitude
        val mode = "mode=$directionMode"
        val parameters = "$str_origin&$str_dest&$mode"
        val output = "json"
        val url =
            "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters + "&key=" + "AIzaSyBL3tCKWE9hgJE50EvpFiAshvJeYJy7bfU"
        return url
    }
}