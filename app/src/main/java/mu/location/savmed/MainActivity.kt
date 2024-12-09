package mu.location.savmed

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
//import mu.location.savmed.SavMed.Companion.bluetoothController
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.SavMed.Companion.corePreferences
import mu.location.savmed.bluetooth.bluetoothLE.NearByFragment
import mu.location.savmed.ui.RippleFragment
import mu.location.savmed.ui.RippleFragment.Companion
import mu.location.savmed.ui.auth.EmergencyContacts.EmergencyContactsViewModel
import mu.location.savmed.ui.call.CallActivity
import mu.location.savmed.ui.locationing.LocationActivity
import mu.location.savmed.ui.medical.MedicalInfoActivity
import mu.location.savmed.utils.ActivityHolder
import mu.location.savmed.utils.SettingsManager
import mu.location.savmed.utils.SharedPreference

class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG = "[Main Activity]"
    }

   // private lateinit var bluetoothLEViewModel: BluetoothLEViewModel
    private lateinit var emrContactsViewModel: EmergencyContactsViewModel

    private lateinit var navController : NavController

    private var permissionsChecked = false
    var selectedFragment : Fragment ?= null

    val backgroundLocResult = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) {
            Log.i(TAG,"Background Location Permissions Granted!")
        } else {
            val dialogResult = showSplashDialog("Please Allow All Time Location Permissions For Background Tracking During Emergencies.")

            if (dialogResult) {
                val param = arrayOf(
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
                launchPermissionAgain(param)
            } else {
                Toast.makeText(
                    this,
                    "Background Tracking disabled..",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Permission Launcher
    private val permissionCheck = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->

        if (results.all { it.value }) {
            Log.i(TAG,"All permisisons Granted!")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (results[Manifest.permission.POST_NOTIFICATIONS] == true) {
                Log.i(TAG, "In Post Granted")
            } else {
                Toast.makeText(
                    this,
                    "Call Background service Not Functioning Please Allow Notifications Permissions..",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (results[Manifest.permission.MANAGE_OWN_CALLS] == true) {
                Log.i(TAG, "In Manage Own Calls Granted")
            } else {
                Toast.makeText(
                    this,
                    "Call service Not Functioning Please Call Permissions..",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true || results[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            val param = arrayOf(
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
            launchPermissionAgain(param)
        } else {
                Toast.makeText(
                    this,
                    "Location Tracking Disabled Please Allow Location Permissions!",
                    Toast.LENGTH_LONG
                ).show()
        }
    }

    private fun launchPermissionAgain(param: Array<String>) {

        Log.i(TAG,"In lain for back")
        if (param.contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            backgroundLocResult.launch(
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        } else {
            permissionCheck.launch(
                param
            )
        }
    }

    private val navListener = BottomNavigationView.OnNavigationItemSelectedListener {
        // By using switch we can easily get the
        // selected fragment by using there id
        when (it.itemId) {
            R.id.main_home -> {
                navController.navigate(R.id.rippleFragment)
                return@OnNavigationItemSelectedListener true
            }
            R.id.call -> {
                startActivity(Intent(applicationContext, CallActivity::class.java))
                overridePendingTransition(0, 0)
                return@OnNavigationItemSelectedListener true
            }
            R.id.nearBy -> {
                navController.navigate(R.id.nearByFragment)
               // return@OnNavigationItemSelectedListener true
            }
            R.id.medical -> {
                startActivity(Intent(applicationContext, MedicalInfoActivity::class.java))
                overridePendingTransition(0, 0)
                return@OnNavigationItemSelectedListener true
            }
        }

        true
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkAndRequestPermissions()
        SharedPreference.init(this)
        initializeViewModels()
        ActivityHolder.MainActivity = this

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNav.setOnNavigationItemSelectedListener(navListener)

        //supportFragmentManager.beginTransaction().replace(R.id.fragmentContainerView, RippleFragment()).commit()

//        bluetoothLEController = AndroidBluetoothLEController(this)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (
                    SettingsManager.hasPermission(
                        android.Manifest.permission.MANAGE_OWN_CALLS,
                        this
                    ) && SettingsManager.hasPermission(
                        android.Manifest.permission.POST_NOTIFICATIONS,
                        this
                    )
                ) {
                    corePreferences.keepServiceAlive = true
                } else {

                    permissionCheck.launch(
                        arrayOf(
                            Manifest.permission.POST_NOTIFICATIONS,
                            Manifest.permission.MANAGE_OWN_CALLS
                        )
                    )
                }
            } else {
                if (
                    SettingsManager.hasPermission(
                        android.Manifest.permission.MANAGE_OWN_CALLS,
                        this
                    )
                ) {
                    corePreferences.keepServiceAlive = true
                } else {
                    permissionCheck.launch(
                        arrayOf(
                            Manifest.permission.MANAGE_OWN_CALLS
                        )
                    )
                }
            }
        } else { corePreferences.keepServiceAlive = true }

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainerView) as NavHostFragment
        navController = navHostFragment.navController

        val intentExtra = intent.getIntExtra("frag",0)
        if (intentExtra != 0) {
            if (intentExtra == 2) {
                Log.i(TAG,"i gett....ripple" )
                navController.navigate(R.id.nearByFragment)
                bottomNav.id = R.id.nearBy
            }
        }

        val locationManager = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled) {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent);

            Toast.makeText(this, "Enable GPS for precise location!", Toast.LENGTH_SHORT).show()
        }

//        if (!isNetworkEnabled) {
//            // Prompt user to enable network
//            val networkIntent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
//            startActivity(networkIntent)
//
//            Toast.makeText(this, "Enable Network for location services!", Toast.LENGTH_SHORT).show()
//        }
//        if (coreContext.isCoreAvailable()) {
//            coreContext.postOnCoreThread {
//                coreContext.contactsManager.getInstituteContactsFromEndpoint()
//            }
//        } else {
//            val delayMillis = (10000..20000).random().toLong() // Random delay between 10 and 20 seconds
//            Log.i("Main Activity", "Latitude or Longitude is null, retrying after $delayMillis milliseconds.")

//            // Use Handler to post a delayed task
//            Handler(Looper.getMainLooper()).postDelayed({
//                coreContext.contactsManager.getInstituteContactsFromEndpoint()
//            }, delayMillis)
//        }

//        var prevConnectionState = bluetoothLEViewModel.state.value
//        lifecycleScope.launch {
//            bluetoothLEViewModel.state.collect { state ->
//
//                if (state.message != null) {
//                    Log.i("Received", state.message)
//                    showSplashDialog(state.message)
//                }
//                prevConnectionState = state
//            }
//        }

        observeRegistrationStatus()
        observeEmergencyContacts()
    }


    private fun initializeViewModels() {

        // Below was comente dout on 24/11/2024 at 5:30
       // bluetoothLEViewModel = ViewModelProvider(this, BluetoothLEViewModelFactory(bluetoothLEController))[BluetoothLEViewModel::class.java]
        emrContactsViewModel = ViewModelProvider(this)[EmergencyContactsViewModel::class.java]
    }


//    private fun initializeSharedPreferences() {
//        initializeViewModels()
//        sharedPreferences = getSharedPreferences("shared_prefs", Context.MODE_PRIVATE)
//        usernameSIP = sharedPreferences.getString("username_key", "").orEmpty()
//        if (usernameSIP.isNotEmpty()) {
//            emrContactsViewModel.getEmergencyContacts(usernameSIP)
//        }
//    }

    private fun observeRegistrationStatus() {
        coreContext.registrationStatus.observe(this) { registrationStatus ->

            findViewById<RadioButton>(R.id.connectStatus).setText(registrationStatus)

            if (registrationStatus.equals("Registration successful")) {
                findViewById<RadioButton>(R.id.connectStatus).setText("Connected")
                findViewById<RadioButton>(R.id.connectStatus).isChecked = true
            }
        }
    }

    private fun observeEmergencyContacts() {
        emrContactsViewModel.contactsList.observe(this) { contactList ->
            for (contact in contactList) {
                if (!coreContext.emrContact.contains(contact)) {
                    coreContext.emrContact.add(contact) // Explicitly add the contact
                }
                Log.i(TAG,coreContext.emrContact.size.toString())
                for(contactz in coreContext.emrContact) {
                    Log.i(TAG,"EMR OCntacts= ${contactz}")
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {

        if (!permissionsChecked) {

            val permissionsNeeded = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.MANAGE_OWN_CALLS,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )

            if (permissionsNeeded.any { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
                permissionCheck.launch(permissionsNeeded)
            } else {
                corePreferences.keepServiceAlive = true
            }
            permissionsChecked = true
        }
    }

    private fun showSplashDialog(message: String): Boolean {

        val dialogBuilder = AlertDialog.Builder(this)
        val result = CompletableDeferred<Boolean>()

        dialogBuilder.setMessage(message)
            .setCancelable(false) // Prevent dismissing the dialog by tapping outside
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss() // Dismiss the dialog when "OK" is pressed
                result.complete(true) // Complete with true when OK is pressed
            }

            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss() // Dismiss the dialog when "Cancel" is pressed
                result.complete(false) // Complete with false when Cancel is pressed
            }

        // Create and show the dialog
        val alert = dialogBuilder.create()
        alert.show()

        // Set the size of the dialog
        alert.window?.setLayout(800, 400) // Set size of the dialog

        // Auto-dismiss the dialog after a certain time
        alert.setOnShowListener {
            alert.getButton(AlertDialog.BUTTON_POSITIVE).postDelayed({
                alert.dismiss()
                result.complete(false) // Complete with false if dismissed automatically
            }, 2000) // Dismiss after 2 seconds
        }

        // Block and wait for the result
        return runBlocking { result.await() }
    }

}

