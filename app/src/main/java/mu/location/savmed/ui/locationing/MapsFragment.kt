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
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.launch
import mu.location.savmed.R
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.SavMed.Companion.webSocket
import mu.location.savmed.bluetooth.bluetoothLE.models.GlobalEventTriggers
import mu.location.savmed.databinding.FragmentMapsBinding
import mu.location.savmed.ui.locationing.DataProcessing.FetchURL
import mu.location.savmed.ui.locationing.DataProcessing.TaskLoadedCallback
import mu.location.savmed.ui.locationing.models.MapsViewModel
import mu.location.savmed.websocket.peerLatLon


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

//    var nearBy_lat: Double = 0.0//19.0216176
//    var nearBy_lon: Double = 0.0//72.8704915

    var dist: Int ?= 0
//    lateinit var foundUserName: String
    lateinit var currentUserBitMap: Bitmap

    var zoomLevel: Float = 0.0f
    val builder: LatLngBounds.Builder = LatLngBounds.Builder()

    lateinit var currentUserLoc: LatLng
    var currentUserMarker: Marker?= null
    var currentUserCircle: Circle?= null

    var addNearUserMarker = false
//
//    lateinit var nearByUserLoc: List<LatLng>
//    var nearByUserMarker: List<Marker>?= null
//    var nearByUserCircle: List<Circle>?= null

    private val nearByUserMarkers: MutableMap<String, Marker> = mutableMapOf()
    private val nearByUserCircles: MutableMap<String, Circle> = mutableMapOf()


    lateinit var currentPolyline: Polyline
    lateinit var startPolyLine: Polyline
    lateinit var endPolyLine: Polyline

    lateinit var mapsViewModel: MapsViewModel

    @SuppressLint("PotentialBehaviorOverride")
    private val callback = OnMapReadyCallback { googleMap ->

        map = googleMap

        val options = PolylineOptions()
        options.color(Color.RED)
        options.width(5f)
  //       Coordinates for the marker
        currentUserLoc = LatLng(latitudezz ?: 0.0,longitudezz ?: 0.0)

        if (addNearUserMarker) {
            addPeerData (webSocket.onPeerLocationEvent.value!!)
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
            zoomLevel = map.cameraPosition.zoom
            val adjustedRadii = (Math.pow(0.5,zoomLevel.toDouble()))*Math.pow(10.0,7.0)
            if (currentUserCircle != null) {
                currentUserCircle!!.radius = adjustedRadii
            }
            for (rad in nearByUserCircles) {
                rad.value.radius = adjustedRadii
            }
//            if (nearByUserCircle != null) {
//                nearByUserCircle!!.radius = adjustedRadii
//            }
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

                lifecycleScope.launch {
                    coreContext._globalEvents.emit(GlobalEventTriggers.DestroyWsSession)
                }

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

//                if (mapsViewModel.isDirectionsClicked == true && nearByUserLoc != LatLng(0.0,0.0) && ::nearByUserLoc.isInitialized ) {
//                    FetchURL(this).execute(
//                        mapsViewModel.getUrl(
//                            currentUserLoc,
//                            nearByUserLoc,
//                            "walking"
//                        ), "walking"
//                    );
//                }

                if (::map.isInitialized && currentUserMarker != null) {
                    currentUserMarker?.position = LatLng(latitudezz!!,longitudezz!!)
                    currentUserCircle?.center = LatLng(latitudezz!!,longitudezz!!)
                } else {
                    mapFragment.getMapAsync(callback)
                }
            }
        }

        webSocket.onPeerLocationEvent.observe(viewLifecycleOwner) { peerMap ->
            Log.i(TAG, "Received Peer data $peerMap")
            addPeerData (peerMap)
        }


//        binding.direct.setOnClickListener() {
//
//            Log.i(TAG,"in direct click!!")
//            mapsViewModel.isDirectionsClicked = true
//            if (nearByUserMarkers != null && currentUserMarker != null) {
//                Log.i(TAG,"Nearby Marker position: [${nearByUserMarker!!.position.latitude},${nearByUserMarker!!.position.longitude}], Current Market postion: [${currentUserMarker!!.position.latitude},${currentUserMarker!!.position.longitude}]")
//                FetchURL(this).execute(
//                    mapsViewModel.getUrl(
//                        currentUserLoc,
//                        nearByUserLoc,
//                        "walking"
//                    ), "walking"
//                );
//            }
//        }

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

    fun addPeerData(peerMap:  HashMap<String, peerLatLon>) {
        val adjustedRadii = (Math.pow(0.5,zoomLevel.toDouble()))*Math.pow(10.0,7.0)

        for ((userId, location) in peerMap) {
            Log.i(TAG, "Processing peer: $userId")

            if (location.latitude != 0.0 && location.longitude != 0.0) {
                val userLocation = LatLng(location.latitude, location.longitude)

                // If marker exists, update position
                if (nearByUserMarkers.containsKey(userId)) {
                    nearByUserMarkers[userId]?.position = userLocation
                    nearByUserCircles[userId]?.center = userLocation
                } else {

                    if (::map.isInitialized) {
                        Log.i(TAG,"in map initjjjsjs")
                        val marker = map.addMarker(
                            MarkerOptions()
                                .position(userLocation)
                                .title("$userId's Location")
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                        )

                        // Create a circle around the user
                        val circle = map.addCircle(
                            CircleOptions()
                                .center(userLocation)
                                .radius(adjustedRadii) // Adjust radius as needed
                                .strokeColor(resources.getColor(R.color.red, null))
                                .fillColor(Color.argb(128, 255, 0, 0))
                                .strokeWidth(2f)
                        )

                        // Store marker and circle in HashMaps
                        nearByUserMarkers[userId] = marker!!
                        nearByUserCircles[userId] = circle
                    } else {
                        Log.i(TAG,"initialize map...")
                        addNearUserMarker = true
                    }

                }
            }
        }
    }
    fun createNewPolyLine(startPoint: LatLng?, endPoint: LatLng?) {
//        if (::startPolyLine.isInitialized) {
//            Log.i(TAG, "In start polyLine Remove!!!")
//            startPolyLine.remove()
//        }
//        if (::endPolyLine.isInitialized) {
//            Log.i(TAG, "In end polyLine Remove!!!")
//            endPolyLine.remove()
//        }
//        val startPolylineArray = ArrayList<LatLng?>()
//        val endPolylineArray = ArrayList<LatLng?>()
//        val startPolyOps = PolylineOptions()
//        val endPolyOps = PolylineOptions()
//
//        startPolylineArray.add(startPoint)
//        startPolylineArray.add(currentUserMarker!!.position)
//        startPolyOps.addAll(startPolylineArray)
//        startPolyOps.color(Color.LTGRAY)
//        startPolyLine = map.addPolyline(startPolyOps)
//
//        endPolylineArray.add(endPoint)
//        endPolylineArray.add(nearByUserMarker!!.position)
//        endPolyOps.addAll(endPolylineArray)
//        endPolyOps.color(Color.LTGRAY)
//        endPolyLine = map.addPolyline(endPolyOps)
    }

}