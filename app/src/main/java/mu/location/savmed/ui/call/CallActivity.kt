package mu.location.savmed.ui.call

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import mu.location.savmed.CallNavGraphDirections
import mu.location.savmed.MainActivity
import mu.location.savmed.R
import mu.location.savmed.SavMed.Companion.bleClient
import mu.location.savmed.SavMed.Companion.bleServer
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.SavMed.Companion.webSocket
import mu.location.savmed.bluetooth.bluetoothLE.NearByFragment
import mu.location.savmed.databinding.ActivityCallBinding
import mu.location.savmed.ui.call.viewModelFactory.CurrentCallViewModelFactory
import mu.location.savmed.ui.call.viewModels.CurrentCallViewModel
import mu.location.savmed.ui.chat.viewModel.AbstractConversationViewModel
import mu.location.savmed.ui.chat.viewModel.ConversationViewModel
import mu.location.savmed.ui.main.SharedMainViewModel
import mu.location.savmed.ui.medical.MedicalInfoActivity
import mu.location.savmed.utils.DialogUtils

class CallActivity : AppCompatActivity() {

    companion object {
        const val TAG = "[Call Activity]"
    }

    val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var binding : ActivityCallBinding
    private lateinit var navController : NavController

    private lateinit var bottomNav: BottomNavigationView

    private lateinit var callViewModel: CurrentCallViewModel
    private lateinit var callViewModelFactory: CurrentCallViewModelFactory

    private lateinit var conversationViewModel: AbstractConversationViewModel

    private lateinit var sharedMainViewModel: SharedMainViewModel

    var chatNotificationArgs = false

    private val navListener = BottomNavigationView.OnNavigationItemSelectedListener {

        when (it.itemId) {
            R.id.main_home -> {
                val i = Intent(applicationContext,MainActivity::class.java)
                i.putExtra("frag",1)
                startActivity(i)
                finish()
                return@OnNavigationItemSelectedListener true
            }
            R.id.call -> {
                return@OnNavigationItemSelectedListener true
            }
            R.id.nearBy -> {
                val i = Intent(applicationContext,MainActivity::class.java)
                i.putExtra("frag",2)
                startActivity(i)
                finish()
                return@OnNavigationItemSelectedListener true
            }
            R.id.locationMap -> {
                startActivity(Intent(applicationContext, MainActivity::class.java))
                overridePendingTransition(0, 0)
                finish()
                return@OnNavigationItemSelectedListener true
            }
        }

        true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        callViewModel = run {
             ViewModelProvider(this)[CurrentCallViewModel::class.java]
        }

        sharedMainViewModel = run {
            ViewModelProvider(this)[SharedMainViewModel::class.java]
        }

        conversationViewModel = run {
            ViewModelProvider(this)[ConversationViewModel::class.java]
        }

        bleClient.locationReadComplete.observe(this) { locationRead ->
            if (locationRead) {
                    callViewModel.sendNearByUsers()
            }
        }

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainerView2) as NavHostFragment
        navController = navHostFragment.navController

        bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNav.setOnNavigationItemSelectedListener(navListener)
        bottomNav.selectedItemId = R.id.call

        chatNotificationArgs = intent.getBooleanExtra("Chat",false)
        Log.i(TAG,"From ChatNotif $chatNotificationArgs")
        if (chatNotificationArgs == true) {
            val localSipUri = intent.getStringExtra("LocalSipUri")
            val remoteSipUri = intent.getStringExtra("RemoteSipUri")

            Log.i(TAG,"Notification Found LocalSipUri: [${localSipUri}] RemoteSipUri: [${remoteSipUri}]")

            if (localSipUri != null && remoteSipUri != null) {
                navController.navigate(
                    CallNavGraphDirections.actionGlobalConversationFragment(
                        localSipUri,
                        remoteSipUri
                    )
                )
            }
        }

        webSocket.join_key.observe(this) { value ->
            Log.i(TAG,"In join_key libe $value ${callViewModel.enableOutgoingCall}")
            if (callViewModel.enableOutgoingCall) {
                Log.i(TAG,"Join Key value: $value")
                callViewModel.outgoingCall(value)
            } else {
                Log.i(TAG,"Outgoing Call NOt Enabled: ${callViewModel.enableOutgoingCall}")
            }
        }

        // Websocket initialized in APP class
        webSocket.isConnected.observe(this) { value ->
            if (value) {
                Toast.makeText(this, "Websocket Connection Successfull!", Toast.LENGTH_SHORT).show()
            }
//            } else {
//                Toast.makeText(this,"Websocket Connection Disconnected!",Toast.LENGTH_SHORT).show()
//            }


            if (callViewModel.enableOutgoingCall) {
                Log.i(TAG,"Outgoing call Enabled!")
                if (value) {
                    webSocket.initiate()
                } else {
                    Toast.makeText(this,"Websocket Connection Failed!",Toast.LENGTH_SHORT).show()
                    Log.i(TAG,"Websocket Connection Failed Initiating Outgoing Call")
                    callViewModel.outgoingCall("")
                }
            }
        }

        bleServer.messageReceivedFromBLE.observe(this){ result ->

            if (!bleServer.isPrevMessage) {
                Log.i(NearByFragment.TAG,"mesdg slpash $result")
                DialogUtils.showSplashDialogNearBy(result,this) { resultz ->
                    if (resultz) {
                        Log.i(MainActivity.TAG, "Dialog confirmed")
                    } else {
                        Log.i(MainActivity.TAG, "Dialog dismissed or canceled")
                    }
                }
                bleServer.isPrevMessage = true
            } else {
                Log.i(NearByFragment.TAG,"message already displayed")
            }
        }

        coreContext.isOutgoingCall.observe(this) { isOutgoingCall ->
            Log.i("outgoing callll","calllll$isOutgoingCall")
            if(isOutgoingCall) {
                navController.navigate(R.id.outgoingCallFragment)
            }
        }
        coreContext.isIncomingCall.observe(this) { isIncomingCall ->
            Log.i("incoming callll","calllll$isIncomingCall")
            if (isIncomingCall) {
                navController.navigate(R.id.incomingCallFragment)
            }
            // callsViewModel.IncomingPostDataAPI(coreContext.remoteUri.value.toString())
        }
        coreContext.isActiveCall.observe(this) { isActiveCall ->
            if(isActiveCall) {
                navController.navigate(R.id.activeCallFragment)
            }
        }
        coreContext.registrationStatus.observe(this) { registrationStatus ->

            findViewById<RadioButton>(R.id.connectStatus).setText(registrationStatus)

            if (registrationStatus.equals("Registration successful")) {
                findViewById<RadioButton>(R.id.connectStatus).setText("Connected")
                findViewById<RadioButton>(R.id.connectStatus).isChecked = true
            }
        }
        coreContext.isCallEnded.observe(this) { isCallEnded ->
            if(isCallEnded) {
                val i = Intent(this,MainActivity::class.java)
                startActivity(i)
                finish()
            }
        }
        callViewModel.goToEndedCallEvent.observe(this) {
            navController.navigate(R.id.contactFragment)
        }
    }

    init{
        onBackPressedDispatcher.addCallback(this /* lifecycle owner */, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val intent = Intent(this@CallActivity,MainActivity::class.java)
                startActivity(intent)
                finish()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        bottomNav.selectedItemId = R.id.call
    }

}