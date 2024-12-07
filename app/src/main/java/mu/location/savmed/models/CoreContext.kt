package mu.location.savmed.models

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import mu.location.savmed.MainActivity
import mu.location.savmed.R
import mu.location.savmed.SavMed.Companion.coreContext
//import mu.location.savmed.SavMed.Companion.bluetoothController
import mu.location.savmed.SavMed.Companion.corePreferences
import mu.location.savmed.contacts.ContactsDB
import mu.location.savmed.contacts.ContactsManager
import mu.location.savmed.notifications.NotificationsManager
import mu.location.savmed.ui.auth.EmergencyContacts.EmergencyContact
import mu.location.savmed.ui.auth.EmergencyContacts.EmergencyContactsViewModel
import mu.location.savmed.ui.auth.EmergencyContacts.EmergencyContactsViewModel.Companion
import mu.location.savmed.ui.call.CallActivity
//import mu.location.savmed.sip.SipActivity
import mu.location.savmed.ui.call.services.CoreForeground
import mu.location.savmed.utils.ActivityMonitor
import mu.location.savmed.utils.AppUtils
import mu.location.savmed.utils.Event
import mu.location.savmed.utils.RetrofitInstance
import mu.location.savmed.utils.SavMedUtils
import mu.location.savmed.utils.SettingsManager
import org.linphone.core.Account
import org.linphone.core.AudioDevice
import org.linphone.core.BuildConfig
import org.linphone.core.Call
import org.linphone.core.ChatMessage
import org.linphone.core.ChatRoom
import org.linphone.core.CodecPriorityPolicy
import org.linphone.core.Config
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.GlobalState
import org.linphone.core.LogLevel
import org.linphone.core.LoggingService
import org.linphone.core.LoggingServiceListenerStub
import org.linphone.core.MediaEncryption
import org.linphone.core.ProxyConfig
import org.linphone.core.Reason
import org.linphone.core.RegistrationState
import retrofit2.HttpException
import java.io.IOException

class CoreContext @UiThread constructor(val context: Context) : HandlerThread("Core Thread") {

    companion object {
        private const val TAG = "[Core Context]"
    }

    lateinit var core: Core
    var isLocationGranted = false
    var isGpsEnabled = false
    var isNetworkAvailable = false
    lateinit var currentAccount: Account
    var isConnectedToAndroidAuto: Boolean = false

    val emrContact: MutableList<EmergencyContact> = mutableListOf()
    val onLocationEvent: HashMap<String, Double> = HashMap()

    private val activityMonitor = ActivityMonitor()

    private val mainThread = Handler(Looper.getMainLooper())

    val notificationManager: NotificationsManager by lazy {
        NotificationsManager(context)
    }

    val contactsManager = ContactsManager()

    var remoteUri = MutableLiveData<String>()
    var isActiveCall = MutableLiveData<Boolean>()
    var isOutgoingCall = MutableLiveData<Boolean>()
    var isIncomingCall = MutableLiveData<Boolean>()
    var isCallEnded = MutableLiveData<Boolean>()

    private var _callStatus = MutableLiveData<String>()
    val callStatus : LiveData<String> = _callStatus

    private var _registrationStatus = MutableLiveData<String>()
    val registrationStatus : LiveData<String> = _registrationStatus

    fun newCallStatus(callStatus : String) {
        _callStatus.value = callStatus
        Log.i("Call live",callStatus)
    }

    fun RegistrationStatus(registrationStatus : String) {
        _registrationStatus.value = registrationStatus
        Log.i("Regis live",registrationStatus)
    }

    private lateinit var coreThread: Handler

    fun isCoreAvailable(): Boolean {
        return ::core.isInitialized
    }

    private var previousCallState = Call.State.Idle

    private val coreListener = object : CoreListenerStub() {


        override fun onGlobalStateChanged(core: Core, state: GlobalState, message: String) {
            org.linphone.core.tools.Log.i("$TAG Global state changed [$state]")

            if (state == GlobalState.On) {
                // Wait for GlobalState.ON as some settings modification won't be saved
                // in RC file if Core isn't ON
                onCoreStarted()
            }
        }

        @WorkerThread
        override fun onConfiguringStatus(
            core: Core,
            status: Config.ConfiguringState?,
            message: String?
        ) {
            org.linphone.core.tools.Log.i("$TAG Configuring state changed [$status], message is [$message]")
            if (status == Config.ConfiguringState.Successful) {
                corePreferences.firstLaunch = false
                Log.i(TAG,"Configuration Successful")

            } else if (status == Config.ConfiguringState.Failed) {
                Log.i(TAG,"Configuration Failed")
            }
        }

        @WorkerThread
        override fun onAudioDevicesListUpdated(core: Core) {
            org.linphone.core.tools.Log.i("$TAG Available audio devices list was updated")
        }


        @WorkerThread
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State?,
            message: String
        ) {
            postOnMainThread { newCallStatus(message) }
            remoteUri.postValue(call.remoteAddress.username)

            val currentState = call.state
            org.linphone.core.tools.Log.i(
                "$TAG Call [${call.remoteAddress.asStringUriOnly()}] state changed [$currentState]"
            )
            when (currentState) {
                Call.State.OutgoingInit,Call.State.OutgoingRinging -> {
                    isOutgoingCall.postValue(true)
                }
                Call.State.IncomingReceived,Call.State.IncomingEarlyMedia -> {
                    postOnMainThread {
                        showCallActivity()
                    }
                    isIncomingCall.postValue(true)
                }
                Call.State.Connected -> {
                    isActiveCall.postValue(true)
                    isIncomingCall.postValue(false)
                    isOutgoingCall.postValue(false)
                    postOnMainThread {
                        showCallActivity()
                    }
                }
                Call.State.StreamsRunning -> { }
                Call.State.Error -> {
                    isIncomingCall.postValue(false)
                    isOutgoingCall.postValue(false)
                    isActiveCall.postValue(false)
                    val errorInfo = call.errorInfo
                    org.linphone.core.tools.Log.w(
                        "$TAG Call error reason is [${errorInfo.reason}](${errorInfo.protocolCode}): ${errorInfo.phrase}"
                    )
                    val text = SavMedUtils.getCallErrorInfoToast(call)
                }
                Call.State.Released -> {
                    isIncomingCall.postValue(false)
                    isOutgoingCall.postValue(false)
                    isActiveCall.postValue(false)

                    postOnMainThread {
                        showCallActivity()
                    }
                }
                else -> { }
            }

            previousCallState = currentState
        }

        override fun onRegistrationStateChanged(
            core: Core,
            proxyConfig: ProxyConfig,
            state: RegistrationState?,
            message: String
        ) {
            super.onRegistrationStateChanged(core, proxyConfig, state, message)
            Log.i(TAG,"State Regissss: $state ")
            postOnMainThread { RegistrationStatus(message) }
        }

        override fun onAccountAdded(core: Core, account: Account) {
            org.linphone.core.tools.Log.i(
                "$TAG New account configured: [${account.params.identityAddress?.asStringUriOnly()}]"
            )

            if (!corePreferences.keepServiceAlive) {
                org.linphone.core.tools.Log.w(
                    "$TAG Newly added account (or the whole Core) doesn't support push notifications, enabling keep-alive foreground service..."
                )
                corePreferences.keepServiceAlive = true
                startKeepAliveService()
            } else {
                org.linphone.core.tools.Log.i(
                    "$TAG Newly added account (or the whole Core) doesn't support push notifications but keep-alive foreground service is already enabled, nothing to do"
                )
            }

        }
    }

    private val loggingServiceListener = object : LoggingServiceListenerStub() {
        @WorkerThread
        override fun onLogMessageWritten(
            logService: LoggingService,
            domain: String,
            level: LogLevel,
            message: String
        ) {
            when (level) {
                LogLevel.Error -> android.util.Log.e(domain, message)
                LogLevel.Warning -> android.util.Log.w(domain, message)
                LogLevel.Message -> android.util.Log.i(domain, message)
                LogLevel.Fatal -> android.util.Log.wtf(domain, message)
                else -> android.util.Log.d(domain, message)
            }
           // FirebaseCrashlytics.getInstance().log("[$domain] [${level.name}] $message")
        }
    }

    init {
        (context as Application).registerActivityLifecycleCallbacks(activityMonitor)
    }

    @WorkerThread
    fun startKeepAliveService() {
        val serviceIntent = Intent(Intent.ACTION_MAIN).setClass(
            context,
            CoreForeground::class.java
        )
        org.linphone.core.tools.Log.i("$TAG Starting Keep alive for third party accounts Service")
        context.startService(serviceIntent)
    }

    @WorkerThread
    fun stopKeepAliveService() {
        val serviceIntent = Intent(Intent.ACTION_MAIN).setClass(
            context,
            CoreForeground::class.java
        )
        org.linphone.core.tools.Log.i(
            "$TAG Stopping Keep alive for third party accounts Service"
        )
        context.stopService(serviceIntent)
    }


    @WorkerThread
    override fun run() {


        val contactsDB = ContactsDB(context)
        val databasePath = context.getDatabasePath("friends.db").absolutePath

        org.linphone.core.tools.Log.i("$TAG Creating Core")
        Looper.prepare()

        Factory.instance().loggingService.addListener(loggingServiceListener)
        org.linphone.core.tools.Log.i("$TAG Crashlytics enabled, register logging service listener")

        org.linphone.core.tools.Log.i("=========================================")
        org.linphone.core.tools.Log.i("==== Linphone-android information dump ====")
        //org.linphone.core.tools.Log.i("VERSION=${BuildConfig.VERSION_NAME} / ${BuildConfig.VERSION_CODE}")
        // org.linphone.core.tools.Log.i("PACKAGE=${BuildConfig.APPLICATION_ID}")
        org.linphone.core.tools.Log.i("BUILD TYPE=${BuildConfig.BUILD_TYPE}")
        org.linphone.core.tools.Log.i("=========================================")

        val looper = Looper.myLooper() ?: return
        coreThread = Handler(looper)

        core = Factory.instance().createCoreWithConfig(corePreferences.config, context)
       // core = Factory.instance().createCore(null,null,context)
        core.isAutoIterateEnabled = true
        core.friendsDatabasePath = databasePath
        core.addListener(coreListener)

        Log.i(TAG,"Friend database path = ${core.friendsDatabasePath}")
        coreThread.postDelayed({ startCore() }, 50)

        val accountList = core.accountList.toList()

        Log.i("Account Inso size",core.accountList.size.toString())

        for (account in accountList) {
            account.contactAddress?.asString()?.let { it1 -> Log.i("Account Info--", it1) }
            core.addAccount(account)
            //currentAccount = account
        }

        if (SettingsManager.hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT,context)) {
            Log.i("CoreContext","Stating bluetooth adapter ")
            if (core.defaultAccount != null) {
                core.defaultAccount!!.params.identityAddress?.username?.let {
                    Log.i("CoreContext","Stating bluetooth adapter $it")
//                    bluetoothController.setAdapterName(
//                        it + "_SavMed"
//                    )
                }
            }
            Log.i("CoreContext","yes permsiss")
        } else {
            Log.i("CoreContext","No permsiss")
        }

        Looper.loop()
    }

    @WorkerThread
    fun startCore() {
        org.linphone.core.tools.Log.i("$TAG Starting Core")
        computeUserAgent()
        org.linphone.core.tools.Log.i("$TAG Core has been configured with user-agent [${core.userAgent}], starting it")
        core.start()

        if (SettingsManager.hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT,context)) {
//            if (core.defaultAccount != null) {
//                core.defaultAccount!!.params.identityAddress?.username?.let {
//                    bluetoothController.setAdapterName(
//                        it + "_SavMed"
//                    )
//                }
//            }
            Log.i("Permissss","yes permsiss")
        } else {
            Log.i("Permissss","No permsiss")
        }
    }

    @SuppressLint("NewApi")
    @WorkerThread
    fun onCoreStarted() {
        org.linphone.core.tools.Log.i("$TAG Core started, updating configuration if required")
        core.videoCodecPriorityPolicy = CodecPriorityPolicy.Auto

        if (corePreferences.keepServiceAlive) {
            startKeepAliveService()
        }

        notificationManager.onCoreStarted(core,false)
        contactsManager.getInstituteContactsFromEndpoint()
        //fetchApiData()
        Log.i(TAG ,"Build Type ${BuildConfig.BUILD_TYPE}")
    }

    @WorkerThread
    private fun destroyCore() {
        if (!::core.isInitialized) {
            return
        }

        val state = core.globalState
        if (state != GlobalState.On) {
            org.linphone.core.tools.Log.w("$TAG Core is in state [$state], do not continue destroy process")
            return
        }
        org.linphone.core.tools.Log.w("$TAG Stopping Core and destroying context related objects")

        postOnMainThread {
            (context as Application).unregisterActivityLifecycleCallbacks(activityMonitor)
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        //audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)

        core.stopAsync()

//        contactsManager.onCoreStopped(core)
//        telecomManager.onCoreStopped(core)
        notificationManager.onCoreStopped(core)

        // It's very unlikely the process will survive until the Core reaches GlobalStateOff sadly
        org.linphone.core.tools.Log.w("$TAG Core is shutting down but probably won't reach Off state")
    }

    override fun quit(): Boolean {
        destroyCore()
        return super.quit()
    }

    override fun quitSafely(): Boolean {
        destroyCore()
        return super.quitSafely()
    }

    @AnyThread
    fun isReady(): Boolean {
        return ::core.isInitialized
    }

    @AnyThread
    fun postOnCoreThread(
        @WorkerThread lambda: (core: Core) -> Unit
    ) {
        if (::coreThread.isInitialized) {
            coreThread.post {
                lambda.invoke(core)
            }
        } else {
            org.linphone.core.tools.Log.e("$TAG Core's thread not initialized yet!")
        }
    }

    @AnyThread
    fun postOnCoreThreadDelayed(
        @WorkerThread lambda: (core: Core) -> Unit,
        delay: Long
    ) {
        if (::coreThread.isInitialized) {
            coreThread.postDelayed({
                lambda.invoke(core)
            }, delay)
        } else {
            org.linphone.core.tools.Log.e("$TAG Core's thread not initialized yet!")
        }
    }

    @AnyThread
    fun postOnMainThread(
        @UiThread lambda: () -> Unit
    ) {
        mainThread.post {
            lambda.invoke()
        }
    }

    @WorkerThread
    fun answerCall(call: Call) {
        org.linphone.core.tools.Log.i("$TAG Answering call ${call.remoteAddress}")
        val params = core.createCallParams(call)
        call.accept()
        if (params == null) {
            org.linphone.core.tools.Log.w("$TAG Answering call without params!")
            call.accept()
            return
        }
        call.acceptWithParams(params)
    }


    @WorkerThread
    fun startCall(
        remoteUri : String
    ) {

        Log.i("In start call","Call initiation to $remoteUri")
        val remoteAddress = when {
            remoteUri.contains('@') && remoteUri.startsWith("sip") -> {
                Factory.instance().createAddress(remoteUri)
            }
            remoteUri.contains('@') -> {
                Factory.instance().createAddress("sip:$remoteUri")
            }
            remoteUri.startsWith("sip") && !remoteUri.contains('@') -> {
                Factory.instance().createAddress("$remoteUri@212.38.94.76")
            }
            else -> {
                Factory.instance().createAddress("sip:$remoteUri@212.38.94.76")
            }
        }

        if (remoteAddress == null) {
            Log.i(TAG ,"Could Not parse Remote Address")
            remoteAddress ?: return // If address parsing fails, we can't continue with outgoing call process

        }

        Log.i("Location fetch","${onLocationEvent["latitude"]},${onLocationEvent["longitude"]}")

        // We also need a CallParams object
        // Create call params expects a Call object for incoming calls, but for outgoing we must use null safely
        val params = core.createCallParams(null)
        params ?: return // Same for params

        params.addCustomHeader("Emergency","none")
        // We can now configure it
        // Here we ask for no encryption but we could ask for ZRTP/SRTP/DTLS
        params.mediaEncryption = MediaEncryption.None
        // If we wanted to start the call with video directly
        //params.enableVideo(true)

        // Finally we start the call
        core.inviteAddressWithParams(remoteAddress, params)
    }

    @WorkerThread
    fun terminateCall(call: Call) {
        if (call.dir == Call.Dir.Incoming && SavMedUtils.isCallIncoming(call.state)) {
            val reason = if (call.core.callsNb > 1) Reason.Busy else Reason.Declined
            org.linphone.core.tools.Log.i(
                "$TAG Declining call [${call.remoteAddress.asStringUriOnly()}] with reason [$reason]"
            )
            call.decline(reason)
        } else {
            org.linphone.core.tools.Log.i("$TAG Terminating call [${call.remoteAddress.asStringUriOnly()}]")
            call.terminate()
        }
    }

    @WorkerThread
    fun pauseOrResume() {
        if (core.callsNb == 0) return

        val call = core.currentCall ?: core.calls[0]

        when (call.state) {
            Call.State.Paused, Call.State.Pausing -> call.resume()
            else -> call.pause()
        }
    }

    @WorkerThread
    fun toggleSpeaker() {
        val currentAudioDevice = core.currentCall?.outputAudioDevice
        val speakerEnabled = currentAudioDevice?.type == AudioDevice.Type.Speaker

        for (audioDevice in core.audioDevices) {
            if (speakerEnabled && audioDevice.type == AudioDevice.Type.Earpiece) {
                core.currentCall?.outputAudioDevice = audioDevice
                return
            } else if (!speakerEnabled && audioDevice.type == AudioDevice.Type.Speaker) {
                core.currentCall?.outputAudioDevice = audioDevice
                return
            }
        }
    }

    @UiThread
    fun startEndCallActivity() {
        org.linphone.core.tools.Log.i("$TAG Starting End Call Activity");
        val i = Intent(context,MainActivity::class.java)
        i.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )
        context.startActivity(i)
    }

    @UiThread
    fun showCallActivity() {
        org.linphone.core.tools.Log.i("$TAG Starting Call activity")
        val intent = Intent(context, CallActivity::class.java)
        // This flag is required to start an Activity from a Service context
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )
        context.startActivity(intent)
    }
//
//    private fun startChatActivity(username: String?) {
//        val i  = Intent(context,ChatActivity::class.java)
//        i.putExtra("remoteAddress",username)
//        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
//        context.startActivity(i)
//    }

    @WorkerThread
    private fun computeUserAgent() {
        val deviceName = AppUtils.getDeviceName(context)
        val appName = context.getString(R.string.app_name)
//        val androidVersion = BuildConfig.VERSION_NAME
//        val userAgent = "${appName}Android/$androidVersion ($deviceName) LinphoneSDK"
        val userAgent = "${appName}Android/($deviceName)"
        val sdkVersion = context.getString(org.linphone.core.R.string.linphone_sdk_version)
        val sdkBranch = context.getString(org.linphone.core.R.string.linphone_sdk_branch)
        val sdkUserAgent = "$sdkVersion ($sdkBranch)"
        core.setUserAgent(userAgent, sdkUserAgent)
    }

    private fun fetchApiData() {
        Log.i(TAG,"NONe")
    }

}
