package mu.location.savmed.ui.locationing

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import mu.location.savmed.utils.ActivityHolder
import mu.location.savmed.MainActivity
import mu.location.savmed.R
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.databinding.ActivityLocationBinding
import mu.location.savmed.utils.RetroFit
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class LocationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLocationBinding

    var lat: Double = 0.0
    var lon: Double = 0.0

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityLocationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ActivityHolder.LocationActivity = this

        findViewById<Button>(R.id.btnHome).setOnClickListener() {
            val i = Intent(this@LocationActivity, MainActivity::class.java)
            startActivity(i)
            finish();
        }

        binding.swLocationsupdates.setOnClickListener() {
            updateUIVALUES()
        }

    }

    fun updateUIVALUES() {

        lat = (coreContext.onLocationEvent["latitude"] ?: 0.0)
        lon = (coreContext.onLocationEvent["longitude"] ?: 0.0)

        binding.tvLat.text = lat.toString()
        binding.tvLon.text = lon.toString()
        binding.tvAccuracy.text = "Unavailable"
        binding.tvAltitude.text = "Unavailable"
        binding.tvSpeed.text = "Unavailable"

        val geocoder = Geocoder(this)
        try {
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            binding.tvAddress.text = addresses!![0].getAddressLine(0)
        } catch (e: Exception) {
            binding.tvAddress.text = "Unable to fetch address"
        }
    }

    init {
        onBackPressedDispatcher.addCallback(this /* lifecycle owner */, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val intent = Intent(this@LocationActivity,MainActivity::class.java)
                startActivity(intent)
                finish()
            }
        })
    }

}