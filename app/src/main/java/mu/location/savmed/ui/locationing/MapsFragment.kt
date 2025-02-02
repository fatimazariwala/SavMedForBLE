package mu.location.savmed.ui.locationing

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnMarkerDragListener
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.android.SphericalUtil
import mu.location.savmed.R
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.SavMed.Companion.webSocket
import mu.location.savmed.databinding.FragmentMapsBinding
import mu.location.savmed.ui.locationing.DataProcessing.FetchURL
import mu.location.savmed.ui.locationing.DataProcessing.TaskLoadedCallback
import mu.location.savmed.ui.locationing.models.MapsViewModel


class MapsFragment : Fragment(),TaskLoadedCallback {

 //   private val args: MapsFragmentArgs by navArgs()

    companion object {
        const val TAG = "[Maps Fragment]"
    }

    lateinit var binding: FragmentMapsBinding

    lateinit var mapFragment: SupportMapFragment
    lateinit var map: GoogleMap

    var latitudezz: Double = 0.0
    var longitudezz: Double = 0.0

    var nearBy_lat: Double = 0.0//19.0216176
    var nearBy_lon: Double = 0.0//72.8704915

    var dist: Int ?= 0
    lateinit var foundUserName: String
    lateinit var currentUserBitMap: Bitmap

    var zoomLevel: Float = 0.0f
    val builder: LatLngBounds.Builder = LatLngBounds.Builder()

    lateinit var currentUserLoc: LatLng
    var currentUserMarker: Marker?= null
    var currentUserCircle: Circle?= null

    lateinit var nearByUserLoc: LatLng
    var nearByUserMarker: Marker?= null
    var nearByUserCircle: Circle?= null

    lateinit var currentPolyline: Polyline
    lateinit var startPolyLine: Polyline
    lateinit var endPolyLine: Polyline

    lateinit var mapsViewModel: MapsViewModel

    @SuppressLint("PotentialBehaviorOverride")
    private val callback = OnMapReadyCallback { googleMap ->

        map = googleMap

//        map.setOnMarkerDragListener(object : OnMarkerDragListener {
//            override fun onMarkerDragStart(arg0: Marker) {
//                Log.d(
//                    "System out",
//                    "onMarkerDragStart..." + arg0.position.latitude + "..." + arg0.position.longitude
//                )
//            }
//
//            @SuppressLint("PotentialBehaviorOverride")
//            override fun onMarkerDragEnd(arg0: Marker) {
//                Log.d(
//                    "System out",
//                    "onMarkerDragEnd..." + arg0.position.latitude + "..." + arg0.position.longitude
//                )
//                if (currentUserMarker != null) {
//                    Log.i(TAG,"Setting postion of current user marker!")
//                    currentUserLoc = LatLng(arg0.position.latitude,arg0.position.longitude)
//                }
//                map.animateCamera(CameraUpdateFactory.newLatLng(arg0.position))
//            }
//
//            override fun onMarkerDrag(arg0: Marker) {
//                // TODO Auto-generated method stub
//                Log.i("System out", "onMarkerDrag...")
//            }
//        })
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
                    .title("${if (::foundUserName.isInitialized) foundUserName else "Aditya's"} Current Location.")
            )

            nearByUserMarker?.let {
                mapsViewModel.fetchAddressFromLatLng(nearByUserLoc,requireContext()) { address ->
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
            zoomLevel = mapsViewModel.getZoomLevel(circleRad).toFloat()
            Log.i(TAG,"setting to zoom level $zoomLevel")
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(nearByUserLoc,zoomLevel))

        }

        // Add a marker at the specified location
        currentUserMarker = googleMap.addMarker(
            MarkerOptions()
                .position(currentUserLoc)
                .title("Your Current Location.")
                .icon(BitmapDescriptorFactory.fromBitmap(currentUserBitMap))
                .draggable(true)
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
            mapsViewModel.fetchAddressFromLatLng(latLng =  currentUserLoc, context = requireContext(), callback =  { address ->
                it.snippet = address
                it.showInfoWindow()
            } )
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
    ): View {

        binding = FragmentMapsBinding.inflate(inflater,container, false)
        mapsViewModel = requireActivity().run {
            ViewModelProvider(this)[MapsViewModel::class.java]
        }
        binding.mapModel = mapsViewModel
        binding.wsModel = webSocket
        binding.lifecycleOwner = this
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mapFragment = (childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?)!!

        //manualJoinKey = view.findViewById(R.id.join_key_manul)
        currentUserBitMap = mapsViewModel.vectorToBitmap(requireContext(),R.drawable.map_current_user_marker)
        zoomLevel = 3f

        webSocket.isConnected.observe(viewLifecycleOwner) { stat ->
            if (stat) {
            } else {
                Toast.makeText(requireContext(),"Live Locationing Stopped!",Toast.LENGTH_SHORT).show()
            }
        }

        binding.wsConnStat.setOnClickListener() {
            Toast.makeText(requireContext(),"Live Locationing Stopping...!",Toast.LENGTH_SHORT).show()
            if (webSocket.isConnected.value == true) {
                webSocket.disConnect()
                mapsViewModel.manualJoinKey.value = ""
            } else {
                if (!mapsViewModel.manualJoinKey.value.isNullOrEmpty()) {
                    webSocket.join_key.postValue(mapsViewModel.manualJoinKey.value)
                    webSocket.enableJoin = true
                    webSocket.connect()
                } else {
                    Toast.makeText(requireContext(),"Please Provide Join Key!",Toast.LENGTH_SHORT).show()
                }

            }
        }

        webSocket.join_key.observe(viewLifecycleOwner) { key ->
            if (!key.isNullOrEmpty()) {
                mapsViewModel.manualJoinKey.postValue(key)
            }
        }

        coreContext.onLocationEvent.observe(viewLifecycleOwner) { location ->
            if (
                location.get("latitude") != 0.0 && location.get("longitude") != 0.0 &&
                location.get("longitude") != longitudezz && location.get("latitude") != latitudezz &&
                location.get("latitude") != null && location.get("longitude") != null
                ) {

                latitudezz = location.get("latitude")!!
                longitudezz = location.get("longitude")!!
                currentUserLoc = LatLng(latitudezz,longitudezz)

                if (mapsViewModel.isDirectionsClicked == true && nearByUserLoc != LatLng(0.0,0.0) && ::nearByUserLoc.isInitialized ) {
                    FetchURL(this).execute(
                        mapsViewModel.getUrl(
                            currentUserLoc,
                            nearByUserLoc,
                            "walking"
                        ), "walking"
                    );
                }

                if (::map.isInitialized && currentUserMarker != null) {
                    currentUserMarker?.position = LatLng(latitudezz!!,longitudezz!!)
                    currentUserCircle?.center = LatLng(latitudezz!!,longitudezz!!)
                } else {
                    mapFragment.getMapAsync(callback)
                }
            }
        }

        webSocket.onPeerLocationEvent.observe(viewLifecycleOwner) { peerDetails ->
            Log.i(TAG,"Received Peer data $peerDetails")
            if (
                peerDetails.latitude != 0.0 && peerDetails.longitude != 0.0 &&
                peerDetails.latitude != nearBy_lat && peerDetails.longitude != nearBy_lon
            ) {
                nearBy_lat = peerDetails.latitude
                nearBy_lon = peerDetails.longitude
                nearByUserLoc = LatLng(nearBy_lat,nearBy_lon)
                foundUserName = peerDetails.person

                if (mapsViewModel.isDirectionsClicked == true && currentUserLoc != LatLng(0.0,0.0) && ::currentUserLoc.isInitialized ) {
                    FetchURL(this).execute(
                        mapsViewModel.getUrl(
                            currentUserLoc,
                            nearByUserLoc,
                            "walking"
                        ), "walking"
                    );
                }

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

        binding.direct.setOnClickListener() {

            Log.i(TAG,"in direct click!!")
            mapsViewModel.isDirectionsClicked = true
            if (nearByUserMarker != null && currentUserMarker != null) {
                Log.i(TAG,"Nearby Marker position: [${nearByUserMarker!!.position.latitude},${nearByUserMarker!!.position.longitude}], Current Market postion: [${currentUserMarker!!.position.latitude},${currentUserMarker!!.position.longitude}]")
                FetchURL(this).execute(
                    mapsViewModel.getUrl(
                        currentUserLoc,
                        nearByUserLoc,
                        "walking"
                    ), "walking"
                );
            }
        }

       // toggleProgressLayoutVisibility(view)
    }

    override fun onTaskDone(startPoint: LatLng?, endPoint: LatLng?, vararg values: Any?) {
        val polylineOptions = values[0] as? PolylineOptions
        if (::currentPolyline.isInitialized) {
            Log.i(TAG, "In polyLine Remove!!!")
            currentPolyline.remove();
        }
        Log.i(TAG,"in here ${values[0]} start: [${startPoint?.latitude},${startPoint?.longitude}], End: [${endPoint?.latitude},${endPoint?.longitude}]")
        currentPolyline = map.addPolyline(polylineOptions!!);
        createNewPolyLine(startPoint, endPoint)
    }

    fun createNewPolyLine(startPoint: LatLng?, endPoint: LatLng?) {
        if (::startPolyLine.isInitialized) {
            Log.i(TAG, "In start polyLine Remove!!!")
            startPolyLine.remove()
        }
        if (::endPolyLine.isInitialized) {
            Log.i(TAG, "In end polyLine Remove!!!")
            endPolyLine.remove()
        }
        val startPolylineArray = ArrayList<LatLng?>()
        val endPolylineArray = ArrayList<LatLng?>()
        val startPolyOps = PolylineOptions()
        val endPolyOps = PolylineOptions()

        startPolylineArray.add(startPoint)
        startPolylineArray.add(currentUserMarker!!.position)
        startPolyOps.addAll(startPolylineArray)
        startPolyOps.color(Color.LTGRAY)
        startPolyLine = map.addPolyline(startPolyOps)

        endPolylineArray.add(endPoint)
        endPolylineArray.add(nearByUserMarker!!.position)
        endPolyOps.addAll(endPolylineArray)
        endPolyOps.color(Color.LTGRAY)
        endPolyLine = map.addPolyline(endPolyOps)
    }


    // Function to fetch an address from latitude and longitude
//    private fun fetchAddressFromLatLng(latLng: LatLng, callback: (String) -> Unit) {
//        val geocoder = Geocoder(requireContext(), Locale.getDefault())
//        try {
//            val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
//            if (addresses != null) {
//                if (addresses.isNotEmpty()) {
//                    val address = addresses[0].getAddressLine(0)
//                    callback(address)
//                } else {
//                    callback("No address found")
//                }
//            } else {
//                callback("No address found")
//            }
//        } catch (e: Exception) {
//            callback("Unable to fetch address")
//        }
//    }

//    fun vectorToBitmap(context: Context, drawableId: Int): Bitmap {
//        val drawable = AppCompatResources.getDrawable(context, drawableId) ?: throw IllegalArgumentException("Invalid drawable ID")
//        val bitmap = Bitmap.createBitmap(
//            drawable.intrinsicWidth,
//            drawable.intrinsicHeight,
//            Bitmap.Config.ARGB_8888
//        )
//        val canvas = Canvas(bitmap)
//        drawable.setBounds(0, 0, canvas.width, canvas.height)
//        drawable.draw(canvas)
//        return bitmap
//    }

//    @WorkerThread
//    fun getLiveMarkerOfNearByUser() {
//        coroutineScope.launch {
//            try {
//                Log.i(TAG,"In Getting Marker for nearby")
//                val data = bleServer.charFrom?.let {
//                    RetrofitInstance.apiLiveLocation.getLiveLocForNearBy(
//                        it
//                    )
//                }
//                if (data?.isSuccessful == true) {
//                    val nearByData: List<getLiveLocationData?>? = data.body()
//                    Log.i(TAG,"final dat ${data.body()}")
//                    if (nearByData != null) {
//                        for (userData in nearByData) {
//                            nearBy_lat = userData?.latitude ?: 0.0
//                            nearBy_lon = userData?.longitude ?: 0.0
//
//                            coreContext.postOnMainThread {
//                                if (::map.isInitialized) {
//                                    map.clear()
//                                }
//                                mapFragment.getMapAsync(callback)
//                            }
//                        }
//                    }
//                } else {
//                    Log.e(TAG,"Could not Load Emergency Contact API Failure!!")
//                }
//            } catch (e : HttpException) {
//                Log.i(TAG,e.message().toString())
//            } catch (e: IOException) {
//                Log.i(TAG,e.message.toString())
//            }
//        }
//    }

//    private fun getZoomLevel(radius: Double): Int {
//        Log.i(TAG,"i am radius $radius")
//        val scale = radius / 500
//        return ((16 - ln(scale) / ln(2.0)).toInt())
//    }
//
//    private fun getURL(from : LatLng, to : LatLng) : String {
//        val origin = "origin=" + from.latitude + "," + from.longitude
//        val dest = "destination=" + to.latitude + "," + to.longitude
//        val sensor = "sensor=false"
//        val key = "key=AIzaSyBL3tCKWE9hgJE50EvpFiAshvJeYJy7bfU"
//        val params = "$origin&$dest&$sensor&$key"
//        return "https://maps.googleapis.com/maps/api/directions/json?$params"
//    }
//
//    private fun decodePoly(encoded: String): List<LatLng> {
//        val poly = ArrayList<LatLng>()
//        var index = 0
//        val len = encoded.length
//        var lat = 0
//        var lng = 0
//
//        while (index < len) {
//            var b: Int
//            var shift = 0
//            var result = 0
//            do {
//                b = encoded[index++].toInt() - 63
//                result = result or (b and 0x1f shl shift)
//                shift += 5
//            } while (b >= 0x20)
//            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
//            lat += dlat
//
//            shift = 0
//            result = 0
//            do {
//                b = encoded[index++].toInt() - 63
//                result = result or (b and 0x1f shl shift)
//                shift += 5
//            } while (b >= 0x20)
//            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
//            lng += dlng
//
//            val p = LatLng(lat.toDouble() / 1E5,
//                lng.toDouble() / 1E5)
//            poly.add(p)
//        }
//
//        return poly
//    }
//
//    @Suppress("StaticFieldLeak")
//    private inner class GetDirection(val url : String) : AsyncTask<Void, Void, List<List<LatLng>>>(){
//
//        @Deprecated("Deprecated in Java")
//        override fun doInBackground(vararg params: Void?): List<List<LatLng>> {
//
//            val client = OkHttpClient()
//            val request = Request.Builder().url(url).build()
//            val response = client.newCall(request).execute()
//            val data = response.body!!.string()
//
//            val result =  ArrayList<List<LatLng>>()
//            try{
//                Log.i(TAG,"Sending polyline data")
//
//                val respObj = Gson().fromJson(data,MapData::class.java)
//                val path =  ArrayList<LatLng>()
//
////                coreContext.postOnMainThread {
//////                    view?.findViewById<TextView>(R.id.direction)?.text = respObj.routes[0].legs[0].steps[0].maneuver
//////                    view?.findViewById<TextView>(R.id.stepCount)?.text = respObj.routes[0].legs[0].steps.size.toString()
////                   // view?.findViewById<TextView>(R.id.endDest)?.text = respObj.routes[0].legs[0].distance.text
////
////                    Log.i(TAG,"Direction: ${respObj.routes[0].legs[0].steps[0].maneuver},StepCount: ${respObj.routes[0].legs[0].steps.size.toString()},EndDest: ${respObj.routes[0].legs[0].distance.text}")
////                }
//
//                for (data in respObj.routes) {
//                    Log.i(TAG,"ResObj: ${data.legs}")
//                    for (leg in data.legs) {
//                        Log.i(TAG,"Looping through Leg: STeps: ${leg.steps} ${leg.steps.size},Distance: ${leg.distance.text} ${leg.distance.value},Duration: ${leg.duration}")
//                        for(step in leg.steps) {
//                            Log.i(TAG,"Loopsing through steps ${step.maneuver} ")
//                        }
//                    }
//                }
//
//                for (i in 0 until respObj.routes[0].legs[0].steps.size){
//                    path.addAll(decodePoly(respObj.routes[0].legs[0].steps[i].polyline.points))
//                    Log.i(TAG,"Data going in decode: ${respObj.routes[0].legs[0].steps[i].polyline.points}")
//                }
//                Log.i(TAG,"Received polyline data: ${path.size}")
//                result.add(path)
//            } catch (e:Exception) {
//                e.printStackTrace()
//            }
//            return result
//        }
//
//        override fun onPostExecute(result: List<List<LatLng>>) {
//            val lineoption = PolylineOptions()
//            for (i in result.indices){
//                lineoption.addAll(result[i])
//                Log.i(TAG,"result: ${result}")
//                lineoption.width(10f)
//                lineoption.color(Color.CYAN)
//                lineoption.geodesic(true)
//            }
//            map.addPolyline(lineoption)
//        }
//    }
//
//

}