package mu.location.savmed.ui.locationing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.location.Geocoder
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.WorkerThread
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.maps.android.SphericalUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mu.location.savmed.R
import mu.location.savmed.SavMed
import mu.location.savmed.SavMed.Companion.bleServer
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.SavMed.Companion.webSocket
import mu.location.savmed.utils.RetrofitInstance
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xml.sax.Parser
import retrofit2.HttpException
import java.io.IOException
import java.net.URL
import java.util.Locale
import kotlin.math.ln


class MapsFragment : Fragment() {

 //   private val args: MapsFragmentArgs by navArgs()

    companion object {
        const val TAG = "[Maps Fragment]"
    }

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var latitudezz: Double ?= 0.0
    var longitudezz: Double ?= 0.0

    var nearBy_lat: Double = 0.0
    var nearBy_lon: Double = 0.0

    lateinit var mapFragment: SupportMapFragment
    lateinit var map: GoogleMap

    var dist: Int ?= 0
    lateinit var foundUserName: String
    lateinit var currentUserBitMap: Bitmap

    var zoomLevel: Float = 0.0f
    val builder: LatLngBounds.Builder = LatLngBounds.Builder()

    lateinit var currentUserLoc: LatLng
    var currentUserMarker: Marker ?= null
    var currentUserCircle: Circle?= null

    lateinit var nearByUserLoc: LatLng
    var nearByUserMarker: Marker ?= null
    var nearByUserCircle: Circle?= null

    private val callback = OnMapReadyCallback { googleMap ->

        map = googleMap

        val options = PolylineOptions()
        options.color(Color.RED)
        options.width(5f)
  //       Coordinates for the marker
        currentUserLoc = LatLng(latitudezz ?: 0.0,longitudezz ?: 0.0)

        if (nearBy_lat != 0.0 && nearBy_lon != 0.0) {
            Log.i(TAG,"Nearby data ${nearBy_lat} ${nearBy_lon}")

            nearByUserLoc = LatLng(nearBy_lat,nearBy_lon)

            nearByUserMarker = googleMap.addMarker(
                MarkerOptions()
                    .position(nearByUserLoc)
                    .title("${if (::foundUserName.isInitialized) foundUserName else ""} Current Location.")
            )

            nearByUserMarker?.let {
                fetchAddressFromLatLng(nearByUserLoc) { address ->
                    it.snippet = address
                    it.showInfoWindow()
                }
            }

            nearByUserMarker?.position?.let { builder.include(it) }


            nearByUserCircle = googleMap.addCircle(
                CircleOptions()
                    .center(nearByUserLoc)
                    .radius(200.0) // Circle radius in meters
                    .strokeColor(resources.getColor(R.color.red, null))
                    .fillColor(Color.argb(128, 255, 0, 0)) // Adjust transparency
                    .strokeWidth(2f)
            )

            Log.i(TAG,"I am val of nearBy_loc: ${nearByUserLoc},loc: ${currentUserLoc}")
            val dist = SphericalUtil.computeDistanceBetween(nearByUserLoc,currentUserLoc)
            val circleRad = (dist/2)
            Log.i(TAG,"I am val of dist: ${dist},circleRad: ${circleRad}")
            zoomLevel = getZoomLevel(circleRad).toFloat()
            Log.i(TAG,"setting to zoom level $zoomLevel")
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(nearByUserLoc,zoomLevel))

//            val url = getURL(currentUserLoc, nearByUserLoc)
//            GetDirection(url).execute()
//            async {
//                val result = URL(url).readText()
//                uiThread {
//
//                }
//            }
        }

        // Add a marker at the specified location
        currentUserMarker = googleMap.addMarker(
            MarkerOptions()
                .position(currentUserLoc)
                .title("Your Current Location.")
                .icon(BitmapDescriptorFactory.fromBitmap(currentUserBitMap))
        )
        currentUserMarker?.position?.let { builder.include(it) }

        // Move the camera to the marker's location
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentUserLoc, zoomLevel))

        // Add a red filled circle around the marker
        currentUserCircle = googleMap.addCircle(
            CircleOptions()
                .center(currentUserLoc)
                .radius(300.0) // Circle radius in meters
                .strokeColor(resources.getColor(R.color.blue_main_700, null))
                .fillColor(ColorUtils.setAlphaComponent(resources.getColor(R.color.blue, null), 128)) // Adjust transparency
                .strokeWidth(2f)
        )

        // Fetch and display the address in the marker's info window
        currentUserMarker?.let {
            fetchAddressFromLatLng(currentUserLoc) { address ->
                it.snippet = address
                it.showInfoWindow()
            }
        }

        val bounds = builder.build()
        val padding = 100 // Padding around the bounds (in pixels)

        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))

        googleMap.setOnCameraIdleListener {
            val adjustedRadii = (Math.pow(0.5,zoomLevel.toDouble()))*Math.pow(10.0,7.0)
            if (currentUserCircle != null) {
                currentUserCircle!!.radius = adjustedRadii
            }
            if (nearByUserCircle != null) {
                nearByUserCircle!!.radius = adjustedRadii
            }
        }

//        // Set a listener for info window click (optional)
//        googleMap.setOnInfoWindowClickListener { clickedMarker ->
//            // Handle info window click
//        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_maps, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mapFragment = (childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?)!!

        currentUserBitMap = vectorToBitmap(requireContext(),R.drawable.map_current_user_marker)
        zoomLevel = 3f

//        bleServer.charFrom = "Aditya"
//        if (!bleServer.charFrom.isNullOrEmpty()) {
//            foundUserName = bleServer.charFrom!!
//
//            coreContext.postOnCoreThread {
//                getLiveMarkerOfNearByUser()
//            }
//        }

        coreContext.onLocationEvent.observe(viewLifecycleOwner) { location ->
            if (
                location.get("latitude") != 0.0 && location.get("longitude") != 0.0 &&
                location.get("longitude") != longitudezz && location.get("latitude") != latitudezz
                ) {
                latitudezz = location.get("latitude")
                longitudezz = location.get("longitude")
                if (::map.isInitialized && currentUserMarker != null) {
                    currentUserMarker?.position = LatLng(latitudezz!!,longitudezz!!)
                    currentUserCircle?.center = LatLng(latitudezz!!,longitudezz!!)
                } else {
                    mapFragment.getMapAsync(callback)
                }
            }
            toggleProgressLayoutVisibility(view)
        }

        webSocket.onPeerLocationEvent.observe(viewLifecycleOwner) { peerDetails ->
            Log.i(TAG,"Received Peer data $peerDetails")
            if (
                peerDetails.latitude != 0.0 && peerDetails.longitude != 0.0 &&
                peerDetails.latitude != nearBy_lat && peerDetails.longitude != nearBy_lon
            ) {
                nearBy_lat = peerDetails.latitude
                nearBy_lon = peerDetails.longitude
                foundUserName = peerDetails.person
                Log.i(TAG,"map ${::map.isInitialized} ${nearByUserMarker}")
                if (nearByUserMarker != null) {
                    Log.i(TAG,"uoiiipoop")
                    nearByUserMarker?.position = LatLng(nearBy_lat,nearBy_lon)
                    nearByUserCircle?.center = LatLng(nearBy_lat,nearBy_lon)
                    nearByUserMarker?.title = "${foundUserName} Current Location"
                } else {
                    if (::map.isInitialized) {
                        map.clear()
                        mapFragment.getMapAsync(callback)
                    }
                }
            } else {
                Log.i(TAG,"Peer data not chaned!!!!")
            }
        }

        toggleProgressLayoutVisibility(view)
    }

    // Function to fetch an address from latitude and longitude
    private fun fetchAddressFromLatLng(latLng: LatLng, callback: (String) -> Unit) {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
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

    @WorkerThread
    fun getLiveMarkerOfNearByUser() {
        coroutineScope.launch {
            try {
                Log.i(TAG,"In Getting Marker for nearby")
                val data = bleServer.charFrom?.let {
                    RetrofitInstance.apiLiveLocation.getLiveLocForNearBy(
                        it
                    )
                }
                if (data?.isSuccessful == true) {
                    val nearByData: List<getLiveLocationData?>? = data.body()
                    Log.i(TAG,"final dat ${data.body()}")
                    if (nearByData != null) {
                        for (userData in nearByData) {
                            nearBy_lat = userData?.latitude ?: 0.0
                            nearBy_lon = userData?.longitude ?: 0.0

                            coreContext.postOnMainThread {
                                if (::map.isInitialized) {
                                    map.clear()
                                }
                                mapFragment.getMapAsync(callback)
                            }
                        }
                    }
                } else {
                    Log.e(TAG,"Could not Load Emergency Contact API Failure!!")
                }
            } catch (e : HttpException) {
                Log.i(TAG,e.message().toString())
            } catch (e: IOException) {
                Log.i(TAG,e.message.toString())
            }
        }
    }

    private fun getZoomLevel(radius: Double): Int {
        Log.i(TAG,"i am radius $radius")
        val scale = radius / 500
        return ((16 - ln(scale) / ln(2.0)).toInt())
    }

    private fun getURL(from : LatLng, to : LatLng) : String {
        val origin = "origin=" + from.latitude + "," + from.longitude
        val dest = "destination=" + to.latitude + "," + to.longitude
        val sensor = "sensor=false"
        val key = "key=AIzaSyBL3tCKWE9hgJE50EvpFiAshvJeYJy7bfU"
        val params = "$origin&$dest&$sensor&$key"
        return "https://maps.googleapis.com/maps/api/directions/json?$params"
    }

    private fun decodePoly(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = LatLng(lat.toDouble() / 1E5,
                lng.toDouble() / 1E5)
            poly.add(p)
        }

        return poly
    }

    @Suppress("StaticFieldLeak")
    private inner class GetDirection(val url : String) : AsyncTask<Void, Void, List<List<LatLng>>>(){

        @Deprecated("Deprecated in Java")
        override fun doInBackground(vararg params: Void?): List<List<LatLng>> {

            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val data = response.body!!.string()

            val result =  ArrayList<List<LatLng>>()
            try{
                Log.i(TAG,"Sending polyline data")

                val respObj = Gson().fromJson(data,MapData::class.java)
                val path =  ArrayList<LatLng>()

                coreContext.postOnMainThread {
                    view?.findViewById<TextView>(R.id.direction)?.text = respObj.routes[0].legs[0].steps[0].maneuver
                    view?.findViewById<TextView>(R.id.stepCount)?.text = respObj.routes[0].legs[0].steps.size.toString()
                    view?.findViewById<TextView>(R.id.endDest)?.text = respObj.routes[0].legs[0].distance.text

                    Log.i(TAG,"Direction: ${respObj.routes[0].legs[0].steps[0].maneuver},StepCount: ${respObj.routes[0].legs[0].steps.size.toString()},EndDest: ${respObj.routes[0].legs[0].distance.text}")
                }

                for (data in respObj.routes) {
                    Log.i(TAG,"ResObj: ${data.legs}")
                    for (leg in data.legs) {
                        Log.i(TAG,"Looping through Leg: STeps: ${leg.steps} ${leg.steps.size},Distance: ${leg.distance.text} ${leg.distance.value},Duration: ${leg.duration}")
                        for(step in leg.steps) {
                            Log.i(TAG,"Loopsing through steps ${step.maneuver} ")
                        }
                    }
                }

                for (i in 0 until respObj.routes[0].legs[0].steps.size){
                    path.addAll(decodePoly(respObj.routes[0].legs[0].steps[i].polyline.points))
                    Log.i(TAG,"Data going in decode: ${respObj.routes[0].legs[0].steps[i].polyline.points}")
                }
                Log.i(TAG,"Received polyline data: ${path.size}")
                result.add(path)
            } catch (e:Exception) {
                e.printStackTrace()
            }
            return result
        }

        override fun onPostExecute(result: List<List<LatLng>>) {
            val lineoption = PolylineOptions()
            for (i in result.indices){
                lineoption.addAll(result[i])
                Log.i(TAG,"result: ${result}")
                lineoption.width(10f)
                lineoption.color(Color.CYAN)
                lineoption.geodesic(true)
            }
            map.addPolyline(lineoption)
        }
    }


    private fun toggleProgressLayoutVisibility (view: View){
//        if (latitudezz == 0.0 && longitudezz == 0.0) {
//            view.findViewById<TextView>(R.id.content_text).visibility = View.VISIBLE
//            view.findViewById<View>(R.id.dim_overlay).visibility = View.VISIBLE
//            view.findViewById<ProgressBar>(R.id.loading_spinner).visibility = View.VISIBLE
//        } else {
//            view.findViewById<TextView>(R.id.content_text).visibility = View.GONE
//            view.findViewById<View>(R.id.dim_overlay).visibility = View.GONE
//            view.findViewById<ProgressBar>(R.id.loading_spinner).visibility = View.GONE
//        }
    }
}
