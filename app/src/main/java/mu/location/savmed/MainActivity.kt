package mu.location.savmed

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import mu.location.savmed.SavMed.Companion.bleServer
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.SavMed.Companion.corePreferences
import mu.location.savmed.SavMed.Companion.webSocket
import mu.location.savmed.bluetooth.bluetoothLE.NearByFragment
import mu.location.savmed.bluetooth.bluetoothLE.models.BluetoothLEViewModel
import mu.location.savmed.bluetooth.bluetoothLE.models.GlobalEventTriggers
import mu.location.savmed.ui.call.CallActivity
import mu.location.savmed.ui.call.viewModelFactory.CurrentCallViewModelFactory
import mu.location.savmed.ui.call.viewModels.CurrentCallViewModel
import mu.location.savmed.ui.chat.viewModel.AbstractConversationViewModel
import mu.location.savmed.ui.chat.viewModel.ConversationViewModel
import mu.location.savmed.ui.main.SharedMainViewModel
import mu.location.savmed.utils.DialogUtils
import mu.location.savmed.utils.SettingsManager
import mu.location.savmed.utils.SharedPreference

class MainActivity : AppCompatActivity(){

    companion object {
        const val TAG = "[Main Activity]"
    }

   // private lateinit var bluetoothLEViewModel: BluetoothLEViewModel

    private lateinit var navController : NavController
    var chatNotificationArgs = false


    private var permissionsChecked = false
    var currentSelectedItemId : Int = 0

    private lateinit var callViewModel: CurrentCallViewModel
    private lateinit var callViewModelFactory: CurrentCallViewModelFactory

    private lateinit var conversationViewModel: AbstractConversationViewModel

    private lateinit var bleViewModel: BluetoothLEViewModel

    private lateinit var sharedMainViewModel: SharedMainViewModel

    lateinit var bottomNav: BottomNavigationView

        val backgroundLocResult = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) {
                Log.i(TAG,"Background Location Permissions Granted!")
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

        when (it.itemId) {
            R.id.main_home -> {
                navController.navigate(R.id.rippleFragment)
                return@OnNavigationItemSelectedListener true
            }
            R.id.call -> {
                if (SharedPreference.username != "") {
                    navController.navigate(R.id.contactFragment)
                } else {
                    Toast.makeText(this,"Please Login!",Toast.LENGTH_SHORT).show()
                }
                return@OnNavigationItemSelectedListener true
            }
            R.id.nearBy -> {
                if (SharedPreference.username != "") {
                    navController.navigate(R.id.nearByFragment)
                }  else {
                    Toast.makeText(this,"Please Login!",Toast.LENGTH_SHORT).show()
                }
                return@OnNavigationItemSelectedListener true
            }
            R.id.locationMap -> {
                if (SharedPreference.username != "") {
                   navController.navigate(R.id.mapsFragment)
                } else {
                    Toast.makeText(this,"Please Login!",Toast.LENGTH_SHORT).show()
                }
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

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainerView) as NavHostFragment
        navController = navHostFragment.navController
        
        bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNav.setOnNavigationItemSelectedListener(navListener)

        if (savedInstanceState != null) {
            val navState = savedInstanceState.getBundle("nav_state")
            if (navState != null) {
                Log.i(TAG,"in NavSTate...")
                navController.restoreState(navState)
            }
        }

        chatNotificationArgs = intent.getBooleanExtra("Chat",false)
        Log.i(TAG,"From ChatNotif $chatNotificationArgs")
        if (chatNotificationArgs == true) {
            val localSipUri = intent.getStringExtra("LocalSipUri")
            val remoteSipUri = intent.getStringExtra("RemoteSipUri")

            Log.i(TAG,"Notification Found LocalSipUri: [${localSipUri}] RemoteSipUri: [${remoteSipUri}]")

            if (localSipUri != null && remoteSipUri != null) {

                val bundle = Bundle().apply {
                    putString("localSipUri",localSipUri)
                    putString("remoteSipUri",remoteSipUri)
                }
                navController.navigate(R.id.conversationFragment,bundle)
            }
        }

       // currentSelectedItemId = bottomNav.selectedItemId  [use this from vewModel]

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

        val locationManager = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled) {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent);

            Toast.makeText(this, "Enable GPS for precise location!", Toast.LENGTH_SHORT).show()
        }

        observeRegistrationStatus()
        observeMessages()
        observeEvents()
       // observeEmergencyContacts()
    }


    private fun initializeViewModels() {

        callViewModel = run {
            ViewModelProvider(this)[CurrentCallViewModel::class.java]
        }

        sharedMainViewModel = run {
            ViewModelProvider(this)[SharedMainViewModel::class.java]
        }

        conversationViewModel = run {
            ViewModelProvider(this)[ConversationViewModel::class.java]
        }

        bleViewModel = run {
            ViewModelProvider(this)[BluetoothLEViewModel::class.java]
        }
        // Below was comente dout on 24/11/2024 at 5:30
       // bluetoothLEViewModel = ViewModelProvider(this, BluetoothLEViewModelFactory(bluetoothLEController))[BluetoothLEViewModel::class.java]
    }

    private fun observeMessages() {

        webSocket.errorMessage.observe(this) { msg ->
            if (msg == "KEY_NOT_FOUND") {
                Toast.makeText(this,"Invalid Rejoin Key!",Toast.LENGTH_SHORT).show()
            }
        }

        webSocket.join_key.observe(this) { value ->
            Log.i(CallActivity.TAG,"In join_key libe $value ${callViewModel.enableOutgoingCall}")
            if (callViewModel.enableOutgoingCall) {
                Log.i(CallActivity.TAG,"Join Key value: $value")
                callViewModel.outgoingCall(value)
            } else {
                Log.i(CallActivity.TAG,"Outgoing Call NOt Enabled: ${callViewModel.enableOutgoingCall}")
            }
        }

        webSocket.isConnected.observe(this) { value ->
            Log.i(TAG,"New COnnnnn")
            if (value) {
                Toast.makeText(this, "Websocket Connection Successfull!", Toast.LENGTH_SHORT).show()
            }

            if (callViewModel.enableOutgoingCall) {
                Log.i(CallActivity.TAG,"Outgoing call Enabled!")
                if (value) {
                    webSocket.initiate()
                } else {
                    Toast.makeText(this,"Websocket Connection Failed!",Toast.LENGTH_SHORT).show()
                    Log.i(CallActivity.TAG,"Websocket Connection Failed Initiating Outgoing Call")
                    callViewModel.outgoingCall("")
                }
            }
        }

        bleServer.messageReceivedFromBLE.observe(this){ result ->
            if (!bleServer.isPrevMessage) {
                Log.i(NearByFragment.TAG,"mesdg slpash $result")
                DialogUtils.showSplashDialogNearBy(result,this) { resultz ->
                    if (resultz) {
                        Log.i(TAG, "Dialog confirmed")
                        navController.navigate(R.id.mapsFragment)
                    } else {
                        Log.i(TAG, "Dialog dismissed or canceled")
                    }
                }
                bleServer.isPrevMessage = true
            } else {
                Log.i(NearByFragment.TAG,"message already displayed")
            }
        }
    }

    private fun observeRegistrationStatus() {
        coreContext.registrationStatus.observe(this) { registrationStatus ->

            findViewById<RadioButton>(R.id.connectStatus).setText(registrationStatus)

            if (registrationStatus.equals("Registration successful")) {
                findViewById<RadioButton>(R.id.connectStatus).setText("Connected")
                findViewById<RadioButton>(R.id.connectStatus).isChecked = true
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

    override fun onResume() {
        super.onResume()
        //bottomNav.selectedItemId = currentSelectedItemId
        Log.d("NavDebug", "Current Destination: ${findNavController(R.id.fragmentContainerView).currentDestination}")
    }

    private fun observeEvents() {
        coreContext.globalEvents.onEach { result ->
            when (result) {
                GlobalEventTriggers.ConnectionEstablished -> {
                    Toast.makeText(this,"Connected!",Toast.LENGTH_SHORT).show()
                }
                is GlobalEventTriggers.Error -> {
                    Toast.makeText(this,result.message,Toast.LENGTH_LONG).show()
                }
                GlobalEventTriggers.DataTransferSucceeded -> {
                    Toast.makeText(this,"Message Sent!",Toast.LENGTH_SHORT).show()
                }
                GlobalEventTriggers.LiveLocationCheck -> {
                    Log.i(TAG,"in live loc mesg recv")
                    DialogUtils.showSplashDialogCheck("","","", this) { resultz ->
                        if (resultz) {
                            Log.i(TAG, "Dialog confirmed")
                            webSocket.changeSession = true
                            coreContext.performLiveLocJOIN("")
                        }
                    }
                }
                GlobalEventTriggers.DestroyWsSession -> {
                    Log.i(TAG,"in live loc mesg recv")
                    DialogUtils.showSplashDialogCheck("Do you want to Destroy the Session or Disconnect From the Session?\n \bNOTE: If you Destroy the session Everyone's Location Tracking on the Session will be disabled!\b",
                        "Destroy","Disconnect", this) { resultz ->
                        if (resultz) {
                            Log.i(TAG, "Dialog confirmed")
                            webSocket.destroyCurrent = true
                        } else {
                            webSocket.destroyCurrent = false
                        }
                        webSocket.disConnect()
                    }
                }
                GlobalEventTriggers.RingerPermissionError -> {
                    Toast.makeText(this,"Please Allow Do nOt disturb Access!",Toast.LENGTH_LONG).show()
                }
                GlobalEventTriggers.CallButtonPressed -> {
                    Toast.makeText(this,"Call In progress! Please Wait!",Toast.LENGTH_LONG).show()
                }
                GlobalEventTriggers.UserNotFound -> {
                    Toast.makeText(this,"User Not Found!",Toast.LENGTH_LONG).show()
                }
                else -> { }
            }
        }
        .catch { throwable ->
            Log.e(NearByFragment.TAG, "Error: $throwable")
        }
        .launchIn(lifecycleScope)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.i(TAG,"buldble navstate ${navController.saveState()}")
        outState.putBundle("nav_state", navController.saveState())
    }
}

