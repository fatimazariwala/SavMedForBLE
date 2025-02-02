package mu.location.savmed.ui.call.viewModels

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.launch
import mu.location.savmed.SavMed.Companion.bleClient
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.SavMed.Companion.webSocket
import mu.location.savmed.bluetooth.bluetoothLE.models.LocationChar
import mu.location.savmed.bluetooth.bluetoothLE.models.NearByForAPI
import mu.location.savmed.contacts.ContactsManager.Companion.SAVMED_ADDRESS_BOOK_FRIEND_LIST
import mu.location.savmed.ui.call.CallActivity
import mu.location.savmed.ui.contacts.models.EndSwitchCallBack
import mu.location.savmed.ui.contacts.fragments.ContactFragment
import mu.location.savmed.ui.locationing.models.locationData
import mu.location.savmed.utils.Event
import mu.location.savmed.utils.RetrofitInstance
import mu.location.savmed.utils.SavMedUtils
import mu.location.savmed.utils.SharedPreference
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.CallListenerStub
import org.linphone.core.ChatRoom
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.tools.Log
import retrofit2.Callback
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CurrentCallViewModel @UiThread constructor(private val callBack: EndSwitchCallBack?=  null) : ViewModel() {

    companion object {
        private const val TAG = "[Current Call ViewModel]"
    }

    var outGoingCallDetails: OutGoingCallDetails? = null
    var enableOutgoingCall = false

    var chatRoom: ChatRoom? = null
    var lastNearByAddress: String ?= null

    val displayedName = MutableLiveData<String>()

    val callStatus = MutableLiveData<String>()
    val searchFilter = MutableLiveData<String>()

    val canBePaused = MutableLiveData<Boolean>()

    val isSpeakerEnabled = MutableLiveData<Boolean>()

    val isPaused = MutableLiveData<Boolean>()

    val isPausedByRemote = MutableLiveData<Boolean>()

    val isMicrophoneMuted = MutableLiveData<Boolean>()

    var address = ""

    val goToEndedCallEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val requestRecordAudioPermission: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val callDuration = MutableLiveData<Int>()

    private lateinit var currentCall: Call

    private val callListener = object : CallListenerStub() {
        @WorkerThread
        override fun onStateChanged(call: Call, state: Call.State, message: String) {
            Log.i("$TAG Call [${call.remoteAddress.asStringUriOnly()}] state changed [$state]")
            if (SavMedUtils.isCallEnding(call.state)) {
                // If current call is being terminated but there is at least one other call, switch
                val core = call.core
                val callsCount = core.callsNb
                Log.i(
                    "$TAG Call is being ended, check for another current call (currently [$callsCount] calls)"
                )
                if (callsCount > 0) {
                    val newCurrentCall = core.currentCall ?: core.calls.firstOrNull()
                    if (newCurrentCall != null) {
                        Log.i(
                            "$TAG From now on current call will be [${newCurrentCall.remoteAddress.asStringUriOnly()}]"
                        )
                        configureCall(newCurrentCall)
                    } else {
                        Log.e(
                            "$TAG Failed to get a valid call to display, go to ended call fragment"
                        )
                        updateCallDuration()
                        val text = if (call.state == Call.State.Error) {
                            SavMedUtils.getCallErrorInfoToast(call)
                        } else {
                            ""
                        }
                        goToEndedCallEvent.postValue(Event(text))
                    }
                } else {
                    updateCallDuration()
                    Log.i("$TAG Call is ending, go to ended call fragment")
                    // Show that call was ended for a few seconds, then leave
                    val text = if (call.state == Call.State.Error) {
                        SavMedUtils.getCallErrorInfoToast(call)
                    } else {
                        ""
                    }
                    goToEndedCallEvent.postValue(Event(text))
                }
            }

            isPaused.postValue(isCallPaused())
            isPausedByRemote.postValue(call.state == Call.State.PausedByRemote)
            canBePaused.postValue(canCallBePaused())
        }
        @WorkerThread
        override fun onAudioDeviceChanged(call: Call, audioDevice: AudioDevice) {
            Log.i("$TAG Audio device changed [${audioDevice.id}]")
            updateOutputAudioDevice(audioDevice)
        }
    }

    private val coreListener = object : CoreListenerStub() {

        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State?,
            message: String
        ) {
            super.onCallStateChanged(core, call, state, message)

            android.util.Log.i("in core lisner","yoooooooo")

            if(state == Call.State.OutgoingInit) {
                android.util.Log.i("in core lisner","$state")
                configureCall(call)
            }
            if(state == Call.State.IncomingReceived) {
                //IncomingPostDataAPI(call.remoteAddress.username.toString())
            }

            if (::currentCall.isInitialized) {
                if (call != currentCall) {
                    if (call == core.currentCall && state != Call.State.Pausing) {
                        Log.w(
                            "$TAG Current call has changed, now is [${call.remoteAddress.asStringUriOnly()}] with state [$state]"
                        )
                        currentCall.removeListener(callListener)
                        configureCall(call)
                    } else if (SavMedUtils.isCallIncoming(call.state)) {
                        Log.w(
                            "$TAG A call is being received [${call.remoteAddress.asStringUriOnly()}], using it as current call unless declined"
                        )
                        currentCall.removeListener(callListener)
                        configureCall(call)
                    }
                }
            } else {
                Log.w(
                    "$TAG There was no current call (shouldn't be possible), using [${call.remoteAddress.asStringUriOnly()}] anyway"
                )
                configureCall(call)
            }
        }
    }


    init {
        coreContext.postOnCoreThread { core ->
            android.util.Log.i("in init","core init")
            core.addListener(coreListener)
            val call = core.currentCall ?: core.calls.firstOrNull()

            if (call != null) {
                Log.i("$TAG Found Call [${call.remoteAddress.asStringUriOnly()}")
                configureCall(call)
            } else {
                Log.i("$TAG No Calls Found")
            }
        }
        enableOutgoingCall = false
    }

    @UiThread
    fun answer() {
        coreContext.postOnCoreThread { core ->
            val call = core.calls.find {
                SavMedUtils.isCallIncoming(it.state)
            }
            if (call != null) {
                Log.i("$TAG Answering call [${call.remoteAddress.asStringUriOnly()}]")
                coreContext.answerCall(call)
            } else {
                Log.e("$TAG No call found in incoming state, can't answer any!")
            }
        }

    }

    @UiThread
    fun hangUp() {
        Log.i("in handup","in hangggup")
        coreContext.postOnCoreThread {
            if (::currentCall.isInitialized) {
                Log.i("$TAG Terminating call manually [${currentCall.remoteAddress.asStringUriOnly()}]")
                coreContext.terminateCall(currentCall)
            } else {
                Log.i("Callnot initiatlized")
            }
        }

    }

    fun toggleMuteMicrophone() {Log.i("in micorphone mutee","muteeee")
        if (ActivityCompat.checkSelfPermission(
                coreContext.context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestRecordAudioPermission.postValue(Event(true))
            return
        }

        coreContext.postOnCoreThread {
            if (::currentCall.isInitialized) {
                val micMuted = currentCall.microphoneMuted

                currentCall.microphoneMuted = !micMuted

                if (micMuted) {
                    Log.w("$TAG Muting microphone")
                } else {
                    Log.i("$TAG Un-muting microphone")
                }
                isMicrophoneMuted.postValue(!micMuted)
            }
        }
    }

    @UiThread
    fun refreshMicrophoneState() {
        coreContext.postOnCoreThread {
            if (::currentCall.isInitialized) {
                val micMuted = currentCall.microphoneMuted

                if (micMuted != isMicrophoneMuted.value) {
                    if (micMuted) {
                        Log.w("$TAG Microphone is muted, updating button state accordingly")
                    } else {
                        Log.i("$TAG Microphone is not muted, updating button state accordingly")
                    }
                    isMicrophoneMuted.postValue(micMuted)
                }
            }
        }
    }

    @WorkerThread
    private fun updateOutputAudioDevice(audioDevice: AudioDevice?) {
        Log.i("updating.....","Updatingsssss")
        isSpeakerEnabled.postValue(audioDevice?.type == AudioDevice.Type.Speaker)
//        isHeadsetEnabled.postValue(     // not needed at the moment
//            audioDevice?.type == AudioDevice.Type.Headphones || audioDevice?.type == AudioDevice.Type.Headset
//        )
//        isBluetoothEnabled.postValue(audioDevice?.type == AudioDevice.Type.Bluetooth)
//
    }

    @UiThread
    fun changeAudioOutputDevice() {

        Log.i("CHANGING audio","output audio Changing")
        coreContext.postOnCoreThread { core ->

            val currentAudioDevice = core.currentCall?.outputAudioDevice
            val speakerEnabled = currentAudioDevice?.type == AudioDevice.Type.Speaker

            for (audioDevice in core.audioDevices) {
                if (speakerEnabled && audioDevice.type == AudioDevice.Type.Earpiece) {
                    core.currentCall?.outputAudioDevice = audioDevice
                    updateOutputAudioDevice(audioDevice)
                } else if (!speakerEnabled && audioDevice.type == AudioDevice.Type.Speaker) {
                    core.currentCall?.outputAudioDevice = audioDevice
                    updateOutputAudioDevice(audioDevice)
                }
            }
        }
    }

    @UiThread
    fun togglePause() {
        Log.i("in pauseeee","iiii")
        coreContext.postOnCoreThread {
            if (::currentCall.isInitialized) {

                when (isCallPaused()) {
                    true -> {
                        Log.i(
                            "$TAG Resuming call [${currentCall.remoteAddress.asStringUriOnly()}]"
                        )
                        currentCall.resume()
                    }

                    false -> {
                        Log.i(
                            "$TAG Pausing call [${currentCall.remoteAddress.asStringUriOnly()}]"
                        )
                        currentCall.pause()
                    }
                }

            }
        }
    }

    private fun configureCall(call : Call) {
        currentCall = call
         val remoteContactAddress = call.remoteContactAddress
        //gotToCallEvent.postValue(Event(true))
        Log.i("In configue call",call.remoteAddress.asStringUriOnly())
        call.addListener(callListener)

        if (ActivityCompat.checkSelfPermission(
                coreContext.context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(
                "$TAG RECORD_AUDIO permission wasn't granted yet, considering microphone as muted!"
            )
            isMicrophoneMuted.postValue(true)
        } else {
            val micMuted = call.conference?.microphoneMuted ?: call.microphoneMuted
            if (micMuted) {
                Log.w("$TAG Microphone is currently muted")
            }
            isMicrophoneMuted.postValue(micMuted)
        }

        val audioDevice = call.outputAudioDevice
        if (audioDevice != null) {
            updateOutputAudioDevice(audioDevice)
        }

        displayedName.postValue(currentCall.remoteAddress.username)
        callDuration.postValue(call.duration)
    }

    @WorkerThread
    private fun isCallPaused(): Boolean {
        if (::currentCall.isInitialized) {
            return when (currentCall.state) {
                Call.State.Paused, Call.State.Pausing -> true
                else -> false
            }
        }
        return false
    }

    @WorkerThread
    private fun canCallBePaused(): Boolean {
        return ::currentCall.isInitialized && !currentCall.mediaInProgress() && when (currentCall.state) {
            Call.State.StreamsRunning, Call.State.Pausing, Call.State.Paused -> true
            else -> false
        }
    }

    @WorkerThread
    fun updateCallDuration() {
        if (::currentCall.isInitialized) {
            callDuration.postValue(currentCall.duration)
        }
    }

    fun createBasicChatRoom(remoteUri: String) {

        val core = coreContext.core
        val account = core.defaultAccount
        if(account == null) {
            android.util.Log.e(
                "Chat Activity","No default account found"
            )
            return
        }

        val params = core.createDefaultChatRoomParams()
        params.backend = ChatRoom.Backend.Basic
        params.isEncryptionEnabled = false
        params.isGroupEnabled = false
        params.subject = "One-to-One CHat room"

        if (params.isValid) {
            val remoteAddress = Factory.instance().createAddress("sip:${remoteUri}@212.38.94.76")

            if (remoteAddress != null) {

                val localAddress = core.defaultAccount?.params?.identityAddress

                val existingChatRoom = core.searchChatRoom(params,localAddress,null, arrayOf(remoteAddress))
                if (existingChatRoom == null) {
                    org.linphone.core.tools.Log.i("No existing ChatRoom Found. Creating a New One")
                    val newChatRoom = core.createChatRoom(params,localAddress, arrayOf(remoteAddress))
                    if (newChatRoom != null) {
                        chatRoom = newChatRoom
//                        val id = SavMedUtils.getChatRoomId(chatRoom)
                        android.util.Log.i("Chat Activity","Conversation Successfully Created]")
                    } else {
                        android.util.Log.e("Chat Activity","Failed to create a chatRoom with [${remoteAddress}]")
                    }
                } else {
                    android.util.Log.w("Chat Activity","Conversation with ${remoteAddress} found!")
                    chatRoom = existingChatRoom
                }
            }
        }
    }

    fun initializeWebSocket(remoteUri: String,fragmentContext: Context,frag: String?=null) {
        enableOutgoingCall = true
        outGoingCallDetails = OutGoingCallDetails(
            remoteUri = remoteUri,
            fragmentContext = fragmentContext,
            frag = frag
        )
        if (webSocket.isConnected.value != true) {
            webSocket.connect()
        } else {
            outgoingCall("")
        }
    }

    fun outgoingCall(join_key: String) {
        Log.i(TAG,"Receibed Jin_key: ${join_key}")
        enableOutgoingCall = false

        val remoteUri = outGoingCallDetails?.remoteUri
        val fragmentContext = outGoingCallDetails?.fragmentContext
        val frag = outGoingCallDetails?.frag
        outGoingCallDetails = null

        val lat = coreContext.onLocationEvent.value?.get("latitude")?: 0.0
        val lon = coreContext.onLocationEvent.value?.get("longitude") ?: 0.0
        lastNearByAddress = null
        val geocoder = Geocoder(fragmentContext!!)
        try {
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            address = addresses!![0].getAddressLine(0)
        } catch (e: Exception) {
            address = "Unable to fetch address"
        }

        Log.i(TAG,"in outside forgggggg")

        //informEmrContacts()
       // sendNearByUsers()
        viewModelScope.launch {
            bleClient.startBLEScan()
        }

        viewModelScope.launch {

            val gson = Gson();
            var LocJson = gson.toJson(
                locationData(
                    lat,
                    lon,
                    0,
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                        Locale("en", "IN")
                    ).format(
                        Date()
                    ),
                    address,
                    coreContext.core.defaultAccount?.params?.identityAddress?.username.toString(),
                    remoteUri!!.trim(),
                )
            );
            android.util.Log.i(ContactFragment.TAG, LocJson);

            val call: retrofit2.Call<locationData?>? = try {

                RetrofitInstance.apiLocation.postLocationData(
                    locationData(
                        Latitude = coreContext.onLocationEvent.value?.get("latitude") ?: 0.0,
                        Longitude = coreContext.onLocationEvent.value?.get("longitude") ?: 0.0,
                        sqlStatus = 0,Address = address,
                        CalleruserName = coreContext.core.defaultAccount?.params?.identityAddress?.username.toString(),
                        ReceiveruserName = remoteUri.trim(),
                        timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                            Locale("en", "IN")
                        ).format(
                            Date()
                        )
                    )
                )

            } catch (e: IOException) {
                Log.i(TAG,"from clocco ${e.message.toString()}")
                return@launch
            } catch (e: HttpException) {
                Log.i(TAG,"from clocco ${e.message.toString()}")
                return@launch
            }

            call?.enqueue(object: Callback<locationData?> {
                override fun onResponse(
                    call: retrofit2.Call<locationData?>,
                    response: Response<locationData?>
                ) {
                    val responz = response.body()
                    Log.i(TAG,"Response : -------${responz?.Latitude},${responz?.Longitude},${responz?.sqlStatus},${responz?.ReceiveruserName}")
                }

                override fun onFailure(
                    call: retrofit2.Call<locationData?>,
                    t: Throwable
                ) {
                    Log.i(TAG,"Response : Failure -----${t.message}")
                }
            })
        }
        coreContext.postOnCoreThread {
            coreContext.startCall(
                remoteUri!!.trim(),
                if (join_key != "") join_key else ""
            )
        }
        displayedName.postValue(remoteUri!!.trim())

        if (frag == "nearByFrag") {
            startCallActivity(context = fragmentContext)
        } else {
            callBack?.switchToOutgoingCallFragment()

        }
    }

    fun startCallActivity(context: Context) {
        val i = Intent(context, CallActivity::class.java)
        context.startActivity(i)
    }

     fun informEmrContacts() {
        val friendList = coreContext.core.getFriendListByName(SAVMED_ADDRESS_BOOK_FRIEND_LIST)?.friends
        for (contact in friendList ?: emptyArray()) {
            if (contact.starred) {
                createBasicChatRoom(contact.address?.username.toString())
                val message =
                    "EMR Help Needed by ${coreContext.core.defaultAccount?.params?.identityAddress?.username} at ->\n ${address}"
                android.util.Log.i(ContactFragment.TAG, message)
                val chatMessage = chatRoom!!.createMessageFromUtf8(message)
                chatMessage.send()
            }
        }
    }

    fun sendNearByUsers() {

        val nearBySavMedUsers = bleClient.scannedDevices.value
        val user = nearBySavMedUsers.findLast { device ->
            device.isSavMed
        }
        if (user != null && user.address != lastNearByAddress) {
            Log.i(TAG,"User Not Null ${user.address}")
               // if (user.isSavMed) {
                    Log.i(TAG,"Jsnskjwjiowejio... [${user.name}]..[${user.latLon?.lat}]. [${user.latLon?.lon}].")
                    val call: retrofit2.Call<NearByForAPI?>? = try {

                        Log.i(TAG,"Sending Request....")
                        RetrofitInstance.apiNearBy.postNearByUsers(
                            NearByForAPI (
                                em_responder = user.name ?: "Saved_User",
                                em_caller = SharedPreference.username,
                                em_responder_location = user.latLon ?: LocationChar(0.0,0.0),
                                event_timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                                    Locale("en", "IN")
                                ).format(
                                    Date()
                                )
                            )
                        )
                    } catch (e: IOException) {
                        Log.i(TAG,"Error Sending NearBY Data: [${e.message}]")
                        return
                    } catch (e: HttpException) {
                        Log.i(TAG,"Error Sending NearBY Data: [${e.message}]")
                        return
                    }

                    call?.enqueue(object: Callback<NearByForAPI?> {
                        override fun onResponse(
                            call: retrofit2.Call<NearByForAPI?>,
                            response: Response<NearByForAPI?>
                        ) {
                            Log.i(TAG,"Response From NearBy: [${response.body()}] [${response.code()}]")
                            if (response.code() == 200 && response.isSuccessful) {
                                lastNearByAddress = user.address
                            } else {
                                Log.i(TAG,"Response Failure")
                            }
                        }

                        override fun onFailure(
                            call: retrofit2.Call<NearByForAPI?>,
                            t: Throwable
                        ) {
                            Log.i(TAG,"Response : Failure -----${t.message}")
                        }
                    })
               // }
        } else {
            Log.i(TAG,"User Already Present... [${user?.address}] [$lastNearByAddress]")
        }
    }


    @UiThread
    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread { core ->
            if (::currentCall.isInitialized) {
                core.removeListener(coreListener)
                currentCall.removeListener(callListener)
            }
        }
    }
}
