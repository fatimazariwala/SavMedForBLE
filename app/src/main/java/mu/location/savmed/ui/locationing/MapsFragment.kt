package mu.location.savmed.ui.locationing

import android.location.Geocoder
import androidx.fragment.app.Fragment
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.NavArgs
import androidx.navigation.fragment.navArgs
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import mu.location.savmed.R
import java.util.Locale
import kotlin.properties.Delegates

class MapsFragment : Fragment() {

 //   private val args: MapsFragmentArgs by navArgs()

    var latitudezz: Double ?= 0.0
    var longitudezz: Double ?= 0.0
    var dist: Int ?= 0
    lateinit var foundUserName: String


    private val callback = OnMapReadyCallback { googleMap ->
        // Coordinates for the marker
//        val loc = LatLng(latitudezz ?: 0.0,longitudezz ?: 0.0)
//
//        // Add a marker at the specified location
//        val marker = googleMap.addMarker(
//            MarkerOptions()
//                .position(loc)
//                .title("Help Need By $foundUserName")
//        )
//
//        // Move the camera to the marker's location
//        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(loc, 14f))
//
//        // Add a red filled circle around the marker
//        googleMap.addCircle(
//            CircleOptions()
//                .center(loc)
//                .radius(500.0) // Circle radius in meters
//                .strokeColor(resources.getColor(R.color.red, null))
//                .fillColor(resources.getColor(R.color.red_translucent, null)) // Adjust transparency
//                .strokeWidth(2f)
//        )
//
//        // Fetch and display the address in the marker's info window
//        marker?.let {
//            fetchAddressFromLatLng(loc) { address ->
//                it.snippet = address
//                it.showInfoWindow()
//            }
//        }
//
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
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?

//        latitudezz = args.lat
//        longitudezz = args.lon
//        dist = args.dist
//        foundUserName = args.foundUserName

        mapFragment?.getMapAsync(callback)
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
}
