package mu.location.savmed.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service.STOP_FOREGROUND_REMOVE
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.LocusIdCompat
import mu.location.savmed.MainActivity
import mu.location.savmed.R
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.bluetooth.bluetoothLE.models.writeMessage
import mu.location.savmed.compatibility.Compatibility
import mu.location.savmed.contacts.AvatarGenerator
import mu.location.savmed.contacts.getAvatarBitmap
import mu.location.savmed.contacts.getPerson
import mu.location.savmed.ui.call.CallActivity
import mu.location.savmed.ui.call.services.CoreForeground
import mu.location.savmed.ui.call.services.CoreInCallService
import mu.location.savmed.utils.AppUtils
import mu.location.savmed.utils.FileUtils
import mu.location.savmed.utils.SavMedUtils
import mu.location.savmed.utils.SettingsManager.hasPermission
import mu.location.savmed.utils.ShortCutUtils
import org.linphone.core.Address
import org.linphone.core.Call
import org.linphone.core.ChatMessage
import org.linphone.core.ChatMessageListenerStub
import org.linphone.core.ChatRoom
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Friend
import org.linphone.core.tools.Log

class NotificationsManager @MainThread constructor(private val context: Context) {

    companion object {
        private const val TAG = "[Notifications Manager]"

        const val INTENT_HANGUP_CALL_NOTIF_ACTION = "mu.savMed.HANGUP_CALL_ACTION"
        const val INTENT_ANSWER_CALL_NOTIF_ACTION = "mu.savMed.ANSWER_CALL_ACTION"
        const val INTENT_NOTIF_ID = "NOTIFICATION_ID"
        val FOREGROUND_NOTIFY_CHANNEL_ID = "FOREGROUND_NOTIFY"
        val INCOMING_CALL_NOTIFY_CHANNEL_ID = "INCOMING_CALL_NOTIFICATION"
        val CALL_NOTIFY_CHANNEL_ID = "CALL_NOTIFICATION"
        const val INTENT_REPLY_MESSAGE_NOTIF_ACTION = "mu.savMed.REPLY_ACTION"
        const val INTENT_MARK_MESSAGE_AS_READ_NOTIF_ACTION = "mu.savMed.MARK_AS_READ_ACTION"

        const val MISSED_CALL_NOTIFICATION_ID = "MISSED_CALL_NOTIFICATION"

        const val BLE_MESSAGE_CHANNEL = "BLE_MESSAGE_NOTIFY"

        const val KEY_TEXT_REPLY = "key_text_reply"
        const val INTENT_LOCAL_IDENTITY = "LOCAL_IDENTITY"
        const val INTENT_REMOTE_ADDRESS = "REMOTE_ADDRESS"

        const val CHAT_TAG = "Chat"
        const val CHAT_NOTIFICATIONS_GROUP = "CHAT_NOTIF_GROUP"

        private const val INCOMING_CALL_ID = 1
        private const val KEEP_ALIVE_FOR_THIRD_PARTY_ACCOUNTS_ID = 5
    }

    private var currentInCallServiceNotificationId = -1
    private var currentKeepAliveThirdPartyAccountsForegroundServiceNotificationId = -1

    private var currentlyRingingCallRemoteAddress: Address? = null

    lateinit var amManager: AudioManager
    var prevAm: Int = 0
    var ringerState: Boolean = false

    private val notificationManager: NotificationManagerCompat by lazy {
        NotificationManagerCompat.from(context)
    }

    private var inCallService: CoreInCallService? = null
    private var keepAliveService: CoreForeground? = null

    private val callNotificationsMap: HashMap<String, Notifiable> = HashMap()
    private val chatNotificationsMap: HashMap<String, Notifiable> = HashMap()
    private val previousChatNotifications: ArrayList<Int> = arrayListOf()

    private val notificationsMap = HashMap<Int, Notification>()

    private var currentlyDisplayedChatRoomId: String = ""


    init {
        for (notification in notificationManager.activeNotifications) {
            if (notification.tag.isNullOrEmpty()) {
                Log.w(
                    "$TAG Found existing (call?) notification [${notification.id}] without tag, cancelling it"
                )
                notificationManager.cancel(notification.id)
            }  else if (notification.tag == CHAT_TAG) {
                Log.i(
                    "[Notifications Manager] Found existing chat notification [${notification.id}]"
                )
                previousChatNotifications.add(notification.id)
            }
        }
        amManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State?,
            message: String
        ) {
            Log.i("$TAG Call state changed: [$state]")
            when (state) {
                Call.State.IncomingReceived, Call.State.IncomingEarlyMedia -> {
                    Log.i(
                        "$TAG Showing incoming call notification for [${call.remoteAddress.asStringUriOnly()}]"
                    )
                    showCallNotification(call, true)
                }

                Call.State.OutgoingInit -> {
                    Log.i(
                        "$TAG Showing outgoing call notification for [${call.remoteAddress.asStringUriOnly()}]"
                    )
                    showCallNotification(call, false)
                }

                Call.State.Connected -> {
                    if (call.dir == Call.Dir.Incoming) {
                        Log.i(
                            "$TAG Connected call was incoming (so it was answered), removing incoming call notification"
                        )
                        removeIncomingCallNotification()
                    }

                    Log.i(
                        "$TAG Showing connected call notification for [${call.remoteAddress.asStringUriOnly()}]"
                    )
                    showCallNotification(call, false)
                }

                Call.State.Updating -> {
                    val notifiable = getNotifiableForCall(call)
                    if (notifiable.notificationId == currentInCallServiceNotificationId) {
                        Log.i(
                            "$TAG Update foreground Service type in case video was enabled/disabled since last time"
                        )
                        startInCallForegroundService(call)
                    }
                }

                Call.State.End, Call.State.Error -> {
                    val remoteSipAddress = call.remoteAddress
                    if (call.dir == Call.Dir.Incoming && currentlyRingingCallRemoteAddress?.weakEqual(
                            remoteSipAddress
                        ) == true
                    ) {
                        Log.i(
                            "$TAG Incoming call has been declined, cancelling incoming call notification"
                        )
                        removeIncomingCallNotification()
                    }

                    Log.i(
                        "$TAG Removing terminated/declined call notification for [${remoteSipAddress.asStringUriOnly()}]"
                    )
                    dismissCallNotification(call)
                }

                Call.State.Released -> { }

                else -> { }
            }
        }

        @WorkerThread
        override fun onLastCallEnded(core: Core) {
            Log.i("$TAG Last call ended, stopping foreground service")
            stopInCallCallForegroundService()
        }

        override fun onMessageReceived(core: Core, chatRoom: ChatRoom, message: ChatMessage) {
            super.onMessageReceived(core, chatRoom, message)
            Log.i(TAG,"Message Received from ${chatRoom.peerAddress} ${message.contents.forEach { content ->
                content.utf8Text
            }}")
            val id = SavMedUtils.getChatRoomId(chatRoom)
            if(currentlyDisplayedChatRoomId.isNotEmpty() && id == currentlyDisplayedChatRoomId) {
                Log.i(TAG,"ChatRoom already In ForeGround Skipping Notification")
                return
            }
            if (ShortCutUtils.isShortcutToChatRoomAlreadyCreated(context, chatRoom)) {
                Log.i("$TAG Conversation shortcut already exists")
                showChatRoomNotification(chatRoom, arrayOf(message))
            } else {
                Log.i(
                    "$TAG Ensure conversation shortcut exists for notification"
                )
                ShortCutUtils.createShortcutsToChatRooms(context)
                showChatRoomNotification(chatRoom, arrayOf(message))
            }
        }

        override fun onMessagesReceived(
            core: Core,
            chatRoom: ChatRoom,
            messages: Array<ChatMessage>
        ) {
            super.onMessagesReceived(core, chatRoom, messages)
            Log.i(TAG,"In messages received")

            for ( rooms in coreContext.core.chatRooms) {
                Log.i(TAG,"chat rooms  ${rooms.peerAddress.username} ${rooms.peerAddress.domain} ${rooms.localAddress.username} ${rooms.localAddress.domain}")
            }
            for (msg in messages) {
                Log.i(TAG,"Message Received from ${chatRoom.peerAddress.username} ")
                for (cont in msg.contents) {
                    Log.i(TAG,"message content = ${cont.utf8Text}")
                }
            }

            val id = SavMedUtils.getChatRoomId(chatRoom)
            if(currentlyDisplayedChatRoomId.isNotEmpty() && id == currentlyDisplayedChatRoomId) {
                Log.i(TAG,"ChatRoom already In ForeGround Skipping Notification")
                return
            }
            if (ShortCutUtils.isShortcutToChatRoomAlreadyCreated(context, chatRoom)) {
                Log.i("$TAG Conversation shortcut already exists")
                showChatRoomNotification(chatRoom, messages)
            } else {
                Log.i(
                    "$TAG Ensure conversation shortcut exists for notification"
                )
                ShortCutUtils.createShortcutsToChatRooms(context)
                showChatRoomNotification(chatRoom, messages)
            }
        }

        override fun onChatRoomRead(core: Core, chatRoom: ChatRoom) {
            super.onChatRoomRead(core, chatRoom)
            Log.i(TAG,"Chat room with id: [${SavMedUtils.getChatRoomId(chatRoom)}] has been marked as Read")
            dismissChatNotification(chatRoom)
        }
    }

    val chatMessageListener = object: ChatMessageListenerStub() {
        override fun onMsgStateChanged(message: ChatMessage, state: ChatMessage.State?) {
            super.onMsgStateChanged(message, state)

            message.userData ?: return
            val id = message.userData as Int
            Log.i(TAG,"Reply Message State Changed [$state] for id [$id]")

            if (state == ChatMessage.State.Delivered || state == ChatMessage.State.Displayed) {
                val address = message.chatRoom.peerAddress.asStringUriOnly()
                val notifiable = chatNotificationsMap[address]
                if (notifiable != null) {
                    if (notifiable.notificationId != id) {
                        Log.w(TAG,"ID Doesn't match: ${notifiable.notificationId} != id")
                    }
                    displayReplyMessageNotification(message, notifiable)
                } else {
                    Log.e("$TAG Couldn't find notification for conversation $address")
                    cancelNotification(id, CHAT_TAG)
                }
            } else if (state == ChatMessage.State.NotDelivered) {
                Log.e("$TAG Reply wasn't delivered")
                cancelNotification(id, CHAT_TAG)
            }

        }
    }

    @WorkerThread
    private fun displayReplyMessageNotification(message: ChatMessage,notifiable: Notifiable) {
        Log.i(TAG,"Updating reply Notification")

        val text = message.contents.find { content -> content.isText }?.utf8Text ?: ""
        val senderAddress = message.fromAddress
        val reply = NotifiableMessage(
            text,
            null,
            notifiable.myself ?: SavMedUtils.getDisplayName(senderAddress),
            senderAddress.asStringUriOnly(),
            System.currentTimeMillis(),
            isOutgoing = true
        )
        notifiable.messages.add(reply)

        val chatRoom = message.chatRoom
        val pendingIntent = getChatRoomPendingIntent(chatRoom,notifiable.notificationId)
        val me = coreContext.contactsManager.getMePerson(chatRoom.localAddress)
        val notification = createMessageNotification(
            notifiable,
            pendingIntent,
            SavMedUtils.getChatRoomId(chatRoom),
            me
        )
        notify(notifiable.notificationId,notification, CHAT_TAG)
    }

    @WorkerThread
    fun dismissChatNotification(chatRoom: ChatRoom): Boolean {
       val address = chatRoom.peerAddress.asStringUriOnly()
        val notifiable: Notifiable? = chatNotificationsMap[address]
        if (notifiable != null) {
            Log.i(TAG,
                "Dismiss notification for conversation [${chatRoom.peerAddress.asStringUriOnly()}] with [${chatRoom.peerAddress.asStringUriOnly()}] with id [${SavMedUtils.getChatRoomId(chatRoom)}]"
            )
            notifiable.messages.clear()
            cancelNotification(notifiable.notificationId, CHAT_TAG)
            return true
        } else {
            val previousNotificationId = previousChatNotifications.find { id ->
                id == SavMedUtils.getChatRoomId(chatRoom).hashCode()
            }
            if (previousNotificationId != null) {
                Log.i(
                    "$TAG Found previous notification with same ID [$previousNotificationId], canceling it"
                )
                cancelNotification(previousNotificationId, CHAT_TAG)
                return true
            }
        }
        return false
    }

    // Create chatRoom Channels
    @RequiresApi(Build.VERSION_CODES.O)
    @MainThread
    private fun createChannels(clearPreviousChannels: Boolean) {
        if (clearPreviousChannels) {
            Log.w("$TAG We were asked to remove all existing notification channels")
            for (channel in notificationManager.notificationChannels) {
                try {
                    Log.i("$TAG Deleting notification channel ID [${channel.id}]")
                    if (channel.id != FOREGROUND_NOTIFY_CHANNEL_ID) {
                        notificationManager.deleteNotificationChannel(channel.id)
                    }
                } catch (e: Exception) {
                    Log.e("$TAG Failed to delete notification channel ID [${channel.id}]: $e")
                }
            }
        }

        createKeepAliveServiceChannel()
        createIncomingCallNotificationChannel()
        createActiveCallNotificationChannel()
        createMessageChannel()
        createBleMessageChannel()
    }

    @WorkerThread
    fun setCurrentlyDisplayedChatRoomId(id: String) {
        Log.i(
            "$TAG Currently displayed conversation is [$id], messages received in it won't be notified"
        )
        currentlyDisplayedChatRoomId = id
    }

    @WorkerThread
    fun resetCurrentlyDisplayedChatRoomId() {
        currentlyDisplayedChatRoomId = ""
        Log.i("$TAG Reset currently displayed conversation")
    }

    @SuppressLint("NewApi")
    @MainThread
    private fun createKeepAliveServiceChannel() {
        val id = FOREGROUND_NOTIFY_CHANNEL_ID
        val name = "Keep alive Service notification"

        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW).apply {
            description = "Keep notification to receive incoming calls"
        }
        notificationManager.createNotificationChannel(channel)
    }

    @SuppressLint("NewApi")
    @MainThread
    private fun createBleMessageChannel() {
        val id = BLE_MESSAGE_CHANNEL
        val name = "Ble Message Notification"

        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Important to received Near-by users Messages in BackGround"
        }
        notificationManager.createNotificationChannel(channel)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @MainThread
    private fun createIncomingCallNotificationChannel() {
        val id = INCOMING_CALL_NOTIFY_CHANNEL_ID
        val name = "Incoming Call Notification"

        val ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setLegacyStreamType(AudioManager.STREAM_RING)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build()

        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
            description = name
            setSound(ringtone, audioAttributes)
        }
        notificationManager.createNotificationChannel(channel)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @MainThread
    private fun createActiveCallNotificationChannel() {
        val id = CALL_NOTIFY_CHANNEL_ID
        val name = "Active Call Notification"

        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = name
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @MainThread
    private fun createMessageChannel() {
        val id = context.getString(R.string.notification_channel_chat_id)
        val name = context.getString(R.string.notification_channel_chat_name)

        val channel = NotificationChannel(id,name,NotificationManager.IMPORTANCE_HIGH).apply {
            description = name
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @WorkerThread
    fun onCoreStarted(core: Core, clearChannels: Boolean) {
        Log.i("$TAG Core has been started")

        coreContext.postOnMainThread {
            createChannels(clearChannels)
        }

        core.addListener(coreListener)
    }

    @WorkerThread
    fun onCoreStopped(core: Core) {
        Log.i("$TAG Getting destroyed, clearing foreground Service & call notifications")
        core.removeListener(coreListener)
    }

    @WorkerThread
    private fun showChatRoomNotification(chatRoom: ChatRoom,messages: Array<ChatMessage>) {
        val notifiable = getNotifiableForConversation(chatRoom,messages)

        if (notifiable.messages.isNotEmpty()) {
            val me = coreContext.contactsManager.getMePerson(chatRoom.localAddress)
            val pendingIntent = getChatRoomPendingIntent(chatRoom,notifiable.notificationId)
            val notification = createMessageNotification (
                notifiable,
                pendingIntent,
                SavMedUtils.getChatRoomId(chatRoom),
                me
            )
            notify(notifiable.notificationId,notification, CHAT_TAG)
        } else {
            Log.w(TAG,"No Message To Display")
        }
    }

    @WorkerThread
    private fun showCallNotification(call: Call,isIncoming: Boolean) {

        Log.i("in show call","innnnn---")
        val notifiable = getNotifiableForCall(call)

        Log.i("in show call","innnnn${isIncoming},${call.state}")

        val callNotificationIntent = Intent(context, CallActivity::class.java)
        callNotificationIntent.addFlags((Intent.FLAG_ACTIVITY_NEW_TASK))
        if(isIncoming) {
            callNotificationIntent.putExtra("IncomingCall",true)
        } else {
            callNotificationIntent.putExtra("ActiveCall",true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            callNotificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = createCallNotification(
            context,
            call,
            notifiable,
            pendingIntent,
            isIncoming
        )

        if (isIncoming) {
            currentlyRingingCallRemoteAddress = call.remoteAddress
            notify(INCOMING_CALL_ID, notification)
            if (currentInCallServiceNotificationId == -1) {
                startIncomingCallForegroundService(notification)
            }
        } else {
            notify(notifiable.notificationId,notification)
            if (currentInCallServiceNotificationId == -1) {
                startInCallForegroundService(call)
            }
        }
    }

    @WorkerThread
    private fun dismissCallNotification(call: Call) {
        val address = call.remoteAddress.asStringUriOnly()
        val notifiable: Notifiable? = callNotificationsMap[address]
        if (notifiable != null) {
            cancelNotification((notifiable.notificationId))
            callNotificationsMap.remove(address)
        } else {
            Log.w("$TAG No Notification found for call with [$address]")
        }
    }


    @WorkerThread
    private fun notify(id: Int, notification: Notification, tag: String? = null) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(
                "$TAG Notifying using ID [$id] and ${if (tag == null) "without tag" else "with tag [$tag]"}"
            )
            try {
                notificationManager.notify(tag, id, notification)
            } catch (iae: IllegalArgumentException) {
                if (inCallService == null && tag == null) {
                    // We can't notify using CallStyle if there isn't a foreground service running
                    Log.w(
                        "$TAG Foreground Service hasn't started yet, can't display a CallStyle notification until then: $iae"
                    )
                } else {
                    Log.e("$TAG Illegal Argument Exception occurred: $iae")
                }
            } catch (e: Exception) {
                Log.e("$TAG Exception occurred: $e")
            }
        } else {
            Log.w("$TAG POST_NOTIFICATIONS permission wasn't granted")
        }
    }
    @MainThread
    fun onInCallServiceStarted(service: CoreInCallService) {
        Log.i("$TAG Service has been started")
        inCallService = service

        coreContext.postOnCoreThread { core ->
            if (core.callsNb == 0) {
                Log.w("$TAG No call anymore, stopping service")
                stopInCallCallForegroundService()
            } else if (currentInCallServiceNotificationId == -1) {
                Log.i(
                    "$TAG At least a call is still running and no foreground Service notification was found"
                )
                val call = core.currentCall ?: core.calls.first()
                startInCallForegroundService(call)
            }
        }
    }

    @WorkerThread
    fun removeIncomingCallNotification() {
        if (currentInCallServiceNotificationId == INCOMING_CALL_ID) {
            if (inCallService != null) {
                Log.i(
                    "$TAG Service found, stopping it as foreground before cancelling notification"
                )
                inCallService?.stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                Log.w("$TAG Incoming call foreground notification Service wasn't found, weird...")
            }
            currentInCallServiceNotificationId = -1
        } else {
            Log.i(
                "$TAG Incoming call notification wasn't used to keep running Service as foreground"
            )
        }

        cancelNotification(INCOMING_CALL_ID)
        currentlyRingingCallRemoteAddress = null
    }

    @SuppressLint("NewApi")
    @WorkerThread
    private fun startIncomingCallForegroundService(notification: Notification) {
        Log.i("$TAG Trying to start foreground service using incoming call notification")
        val service = inCallService
        if (service != null) {
            Log.i(
                "$TAG Service already started, staring it as foreground using notification ID [$INCOMING_CALL_ID]"
            )
            Compatibility.startServiceForeground(
                service,
                INCOMING_CALL_ID,
                notification,
                Compatibility.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            )
            currentInCallServiceNotificationId = INCOMING_CALL_ID
        } else {
            Log.w("$TAG Calling Service not started yet..")
        }
    }

    @SuppressLint("NewApi")
    @WorkerThread
    private fun startInCallForegroundService(call: Call) {
        Log.i("$TAG Trying to start/update foreground Service using call notification")

        val channel = notificationManager.getNotificationChannel(CALL_NOTIFY_CHANNEL_ID)
        val importance = channel?.importance ?: NotificationManagerCompat.IMPORTANCE_NONE
        if (importance == NotificationManagerCompat.IMPORTANCE_NONE) {
            Log.e("$TAG Calls channel has ben disabled, can't start foreground service")
            return
        }

        val notifiable = getNotifiableForCall(coreContext.core.currentCall ?: coreContext.core.calls.first())
        val notification = notificationManager.activeNotifications.find {
            it.id == notifiable.notificationId
        }
         if (notification == null) {
             Log.w("$TAG No notification found for call aborting")
             return
         }
        Log.i("$TAG Found Notification [${notification.id}] for current call")

        var mask = Compatibility.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        val callState = call.state
        if (!SavMedUtils.isCallIncoming(callState) && !SavMedUtils.isCallOutgoing(callState) && !SavMedUtils.isCallEnding(
                callState
            )
        ) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                mask = mask or Compatibility.FOREGROUND_SERVICE_TYPE_MICROPHONE
                Log.i(
                    "$TAG RECORD_AUDIO permission has been granted, adding FOREGROUND_SERVICE_TYPE_MICROPHONE to foreground Service types mask"
                )
            }
        }
        val service = inCallService
        if (service != null) {
            Log.i(
                "$TAG Service found, starting it as foreground using notification ID [${notifiable.notificationId}] with type(s) [$mask]"
            )
            Compatibility.startServiceForeground(
                service,
                notifiable.notificationId,
                notification.notification,
                mask
            )
            currentInCallServiceNotificationId = notifiable.notificationId
        } else {
            Log.w("$TAG Core Foreground Service hasn't started yet")
        }
    }

    @WorkerThread
    private fun stopInCallCallForegroundService() {
        val service = inCallService
        if (service != null) {
            Log.i(
                "$TAG Stopping foreground Service (was using notification ID [$currentInCallServiceNotificationId])"
            )
            service.stopForeground(STOP_FOREGROUND_REMOVE)
            service.stopSelf()
            currentInCallServiceNotificationId = -1
        } else {
            Log.w("$TAG Can't stop foreground Service & notif, no Service was found")
        }
    }

    @WorkerThread
    private fun getNotifiableForConversation(chatRoom: ChatRoom,messages: Array<ChatMessage>): Notifiable {
        val address = chatRoom.peerAddress.asStringUriOnly()

        var notifiable = chatNotificationsMap[address]

        if (notifiable == null) {
            notifiable = Notifiable(SavMedUtils.getChatRoomId(chatRoom).hashCode())
            notifiable.myself = SavMedUtils.getDisplayName(chatRoom.localAddress)
            notifiable.localIdentity = chatRoom.localAddress.asStringUriOnly()
            notifiable.remoteAddress = chatRoom.peerAddress.asStringUriOnly()

            if (chatRoom.hasCapability(ChatRoom.Capabilities.OneToOne.toInt())) {
                notifiable.isGroup = false
            } else {
                notifiable.isGroup = true
                notifiable.groupTitle = chatRoom.subject
            }

            for (msg in chatRoom.unreadHistory) {
                if (msg.isRead || msg.isOutgoing) continue
                val notificationMessage = getNotifiableForChatMessage(msg)
                notifiable.messages.add(notificationMessage)
            }
        } else {
            for (msg in messages) {
                if (msg.isRead || msg.isOutgoing) continue
                val notificationMessage = getNotifiableForChatMessage(msg)
                notifiable.messages.add(notificationMessage)
            }
        }

        chatNotificationsMap[address] = notifiable
        return notifiable
    }

    @WorkerThread
    private fun getNotifiableForChatMessage(message: ChatMessage): NotifiableMessage {
        val contact = coreContext.contactsManager.findContactByAddress(message.fromAddress)
        val displayName = contact?.name ?: SavMedUtils.getDisplayName(message.fromAddress)
        val text = SavMedUtils.getPlainTextDescribingMessage(message)
        val address = message.fromAddress
        val notifiableMessage = NotifiableMessage (
            text,
            contact,
            displayName,
            address.asStringUriOnly(),
            message.time * 1000,
            isOutgoing = message.isOutgoing
        )

        for (content in message.contents) {
            if (content.isFile) {
                val path = content.filePath
                if (path != null) {
                    val contentUri = FileUtils.getPublicFilePath(context, path)
                    val filePath = contentUri.toString()
                    val extension = FileUtils.getExtensionFromFileName(filePath)
                    if (extension.isNotEmpty()) {
                        val mime = FileUtils.getMimeTypeFromExtension(extension)
                        notifiableMessage.filePath = contentUri
                        notifiableMessage.fileMime = mime
                        Log.i(TAG,"Added file $contentUri with MIME $mime to notification")
                    } else {
                        Log.e(TAG,"Couldn't find extension for incoming message with file $path")
                    }
                }
            }
        }
        return notifiableMessage
    }

    @WorkerThread
    private fun getNotifiableForCall(call: Call) : Notifiable {
        val address = call.remoteAddress.asStringUriOnly()
        var notifiable : Notifiable? = callNotificationsMap[address]
        if (notifiable == null) {
            notifiable = Notifiable((getNotificationIdForCall(call)))
            notifiable.remoteAddress = call.remoteAddress.asStringUriOnly()

            callNotificationsMap[address] = notifiable
        }
        return notifiable
    }

    @WorkerThread
    private fun getNotificationIdForCall(call: Call): Int {
        return call.callLog.startDate.toInt()
    }

    @WorkerThread
    private fun createMessageNotification(
        notifiable: Notifiable,
        pendingIntent: PendingIntent,
        id: String,
        me: Person
    ): Notification {

        Log.i(TAG,"I am tryyna create message notification.....")
        val style = NotificationCompat.MessagingStyle(me)
        val allPersons = arrayListOf<Person>()

        var lastPersonAvatar: Bitmap? = null
        var lastPerson: Person? = null

        for (message in notifiable.messages) {
            val friend = message.friend
            val person = getPerson(friend,message.sender)

            if (!message.isOutgoing) {
                lastPerson = person
                lastPersonAvatar = friend?.getAvatarBitmap()

                if (allPersons.find { it.key == person.key } == null) {
                    allPersons.add(person)
                }
            }

            val senderPerson = if (message.isOutgoing) null else person
            val tmp = NotificationCompat.MessagingStyle.Message(
                message.message,
                message.time,
                senderPerson
            )
            if (message.filePath != null) tmp.setData(message.fileMime,message.filePath)

            style.addMessage(tmp)
            if (message.isOutgoing) {
                style.addHistoricMessage(tmp)
            }
        }

        style.conversationTitle = if (notifiable.isGroup) notifiable.groupTitle else lastPerson?.name
        style.isGroupConversation = notifiable.isGroup
        Log.i(TAG,
            "Conversation is ${if (style.isGroupConversation) "group" else "1-1"} with title [${style.conversationTitle}]"
        )

        Log.i(TAG,"Calling Ringer Reset For Messaging")
        ringerState = checkModeAndSet()

        val largeIcon = lastPersonAvatar
        val notificationBuilder = NotificationCompat.Builder(
            context,
            context.getString(R.string.notification_channel_chat_id)
        )
            .setSmallIcon(R.drawable.chat_teardrop_text)
            .setAutoCancel(true)
            .setLargeIcon(largeIcon)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(CHAT_NOTIFICATIONS_GROUP)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setNumber(notifiable.messages.size)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setStyle(style)
            .setContentIntent(pendingIntent)
            .addAction(getMarkMessageAsReadAction(notifiable))
            .addAction(getReplyMessageAction(notifiable))
            .setShortcutId(id)
            .setLocusId(LocusIdCompat(id))

        for (person in allPersons) {
            notificationBuilder.addPerson(person)
        }
        return notificationBuilder.build()
    }

    @SuppressLint("MissingPermission")
    fun createBleMessageNotification(message: writeMessage){
        Log.i(
            "$TAG Trying to start keep alive for third party accounts foreground Service using call notification"
        )

        val channelId = BLE_MESSAGE_CHANNEL
        val channel = notificationManager.getNotificationChannel(channelId)
        val importance = NotificationManagerCompat.IMPORTANCE_HIGH

        Log.i(TAG,"Calling Ringer Mode Set For Neaby")
        ringerState = checkModeAndSet()

        val intent = Intent(context,MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setAutoCancel(true)
                .setOngoing(true)
                .setContentTitle("Needed By SOS: ${message.From}")
                .setContentText("Approx ${message.dist} away from you!")
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSilent(false)
                .setContentIntent(pendingIntent)
            val notification = builder.build()
        if (hasPermission(Manifest.permission.POST_NOTIFICATIONS,context)) {
            notificationManager.notify(BLE_MESSAGE_CHANNEL.hashCode(), notification)
        } else {
            Log.e(TAG,"SEcurity Exception Could not show NearBy permission [ALLOW POST NOTIFICATIONS PERMISSIONS]")
        }
    }

    @WorkerThread
    private fun createCallNotification(
        context: Context,
        call:Call,
        notifiable: Notifiable,
        pendingIntent: PendingIntent?,
        isIncoming: Boolean
    ) : Notification {
        val declineIntent = getCallDeclinePendingIntent(notifiable)
        val answerIntent = getCallAnswerPendingIntent(notifiable)

        val remoteAddress = call.remoteAddress
        val remoteContactAddress = call.remoteContactAddress

        val conferenceInfo = if (remoteContactAddress != null) {
            call.core.findConferenceInformationFromUri(remoteContactAddress) ?: call.callLog.conferenceInfo
        } else {
            call.callLog.conferenceInfo
        }

        val conference = call.conference
        val isConference  = conference != null || conferenceInfo != null

        val caller = if (isConference) {
            val subject = conferenceInfo?.subject ?: conference?.subject ?: remoteAddress.username ?: remoteAddress.asString()
            Person.Builder()
                .setName(subject)
                .setImportant(false)
                .build()
        } else {
            val displayName = remoteAddress.username ?: remoteAddress.asString()
            Person.Builder()
                .setName(displayName)
                .setKey(displayName)
                .setImportant(false)
                .build()
        }

        val style = if (isIncoming) {
            if (!Compatibility.hasFullScreenIntentPermission(context)) {
                Log.e(
                    "$TAG Android >= 14 & full screen intent permission wasn't granted, incoming call may not be visible!"
                )
            } else {
                Log.i("Full Screen Intent","GRANTED")
            }
            NotificationCompat.CallStyle.forIncomingCall(
                caller,
                declineIntent,
                answerIntent
            )
        } else {
            NotificationCompat.CallStyle.forOngoingCall(
                caller,
                declineIntent
            )
        }

        val channel = if (isIncoming) {
            INCOMING_CALL_NOTIFY_CHANNEL_ID
        } else {
            CALL_NOTIFY_CHANNEL_ID
        }

        Log.i(
            "Creating notification for ${if (isIncoming) "incoming" else "outgoing"} ${if (isConference) "conference" else "call"} on channel [$channel]"
        )

        Log.i(TAG,"Calling Ringer Reset")
        ringerState = checkModeAndSet()

        val builder = NotificationCompat.Builder(
            context,
            channel
        ).apply {
            try{
                style.setIsVideo(false)
                setStyle(style)
            } catch(iae:IllegalArgumentException) {
                Log.e(
                    "$TAG Can't use notification call style: $iae"
                )
            }
            setColorized(true)
            setOnlyAlertOnce(true)
            setSmallIcon(R.drawable.ic_stat_name)
            setCategory(NotificationCompat.CATEGORY_CALL)
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            if (isIncoming) {
                setPriority(NotificationCompat.PRIORITY_MAX)
            } else {
                setPriority(NotificationCompat.PRIORITY_HIGH)
            }
            setWhen(System.currentTimeMillis())
            setAutoCancel(false)
            setOngoing(true)
            setContentIntent(pendingIntent)
            setFullScreenIntent(pendingIntent,true)
        }

        return builder.build()
    }

    @AnyThread
    private fun getReplyMessageAction(notifiable: Notifiable): NotificationCompat.Action {
        val replyLabel = "Reply"
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY).setLabel(replyLabel).build()
        val replyIntent = Intent(context,NotificationAction::class.java)
        replyIntent.action = INTENT_REPLY_MESSAGE_NOTIF_ACTION
        replyIntent.putExtra(INTENT_NOTIF_ID,notifiable.notificationId)
        replyIntent.putExtra(INTENT_LOCAL_IDENTITY,notifiable.localIdentity)
        replyIntent.putExtra(INTENT_REMOTE_ADDRESS,notifiable.remoteAddress)

//
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notifiable.notificationId,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        return NotificationCompat.Action.Builder(
            R.drawable.paper_plane_right,
            "Reply",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .setShowsUserInterface(false)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .build()
    }

    @AnyThread
    private fun getMarkMessageAsReadAction(notifiable: Notifiable): NotificationCompat.Action {
        val markAsReadPendingIntent = getMarkMessageAsReadPendingIntent(notifiable)
        return NotificationCompat.Action.Builder(
            R.drawable.envelope_simple_open,
            context.getString(R.string.notification_mark_message_as_read),
            markAsReadPendingIntent
        )
            .setShowsUserInterface(false)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .build()
    }

    @AnyThread
    private fun getMarkMessageAsReadPendingIntent(notifiable: Notifiable): PendingIntent {
        val markAsReadIntent = Intent(context, NotificationAction::class.java)
        markAsReadIntent.action = INTENT_MARK_MESSAGE_AS_READ_NOTIF_ACTION
        markAsReadIntent.putExtra(INTENT_NOTIF_ID, notifiable.notificationId)
        markAsReadIntent.putExtra(INTENT_LOCAL_IDENTITY, notifiable.localIdentity)
        markAsReadIntent.putExtra(INTENT_REMOTE_ADDRESS, notifiable.remoteAddress)

        return PendingIntent.getBroadcast(
            context,
            notifiable.notificationId,
            markAsReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    @WorkerThread
    private fun getPerson(friend: Friend?, displayName: String): Person {
        return friend?.getPerson()
            ?: Person.Builder()
                .setName(displayName)
                .setIcon(
                    AvatarGenerator(context).setInitials(AppUtils.getInitials(displayName)).buildIcon()
                )
                .setKey(displayName)
                .setImportant(false)
                .build()
    }


    @AnyThread
    fun getCallDeclinePendingIntent(notifiable: Notifiable): PendingIntent {
        val hangupIntent = Intent(context, NotificationAction::class.java)
        hangupIntent.action = INTENT_HANGUP_CALL_NOTIF_ACTION
        hangupIntent.putExtra(INTENT_NOTIF_ID, notifiable.notificationId)
        hangupIntent.putExtra(INTENT_REMOTE_ADDRESS, notifiable.remoteAddress)

        return PendingIntent.getBroadcast(
            context,
            3,
            hangupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    @AnyThread
    fun getCallAnswerPendingIntent(notifiable: Notifiable): PendingIntent {
        val answerIntent = Intent(context, NotificationAction::class.java)
        answerIntent.action = INTENT_ANSWER_CALL_NOTIF_ACTION
        answerIntent.putExtra(INTENT_NOTIF_ID, notifiable.notificationId)
        answerIntent.putExtra(INTENT_REMOTE_ADDRESS, notifiable.remoteAddress)

        return PendingIntent.getBroadcast(
            context,
            2,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    @WorkerThread
    fun getChatRoomPendingIntent(chatRoom: ChatRoom,notificationId: Int): PendingIntent {
        val args = Bundle()
        args.putBoolean("Chat", true)
        args.putString("RemoteSipUri", chatRoom.peerAddress.asStringUriOnly())
        args.putString("LocalSipUri", chatRoom.localAddress.asStringUriOnly())

        // Not using NavDeepLinkBuilder to prevent stacking a ConversationsListFragment above another one
        return TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(
                Intent(context, CallActivity::class.java).apply {
                    setAction(Intent.ACTION_MAIN) // Needed as well
                    putExtras(args) // Need to pass args here for Chat extra
                }
            )
            getPendingIntent(
                notificationId,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                args // Need to pass args here too for Remote & Local SIP URIs
            )!!
        }
    }

    @MainThread
    fun onKeepAliveServiceStarted(service: CoreForeground) {
        Log.i("$TAG Keep app alive for third party accounts Service has been started")
        keepAliveService = service
        startKeepAliveServiceForeground()

    }

    @RequiresApi(Build.VERSION_CODES.O)
    @MainThread
    private fun startKeepAliveServiceForeground() {
        Log.i(
            "$TAG Trying to start keep alive for third party accounts foreground Service using call notification"
        )

        val channelId = FOREGROUND_NOTIFY_CHANNEL_ID
        val channel = notificationManager.getNotificationChannel(channelId)
        val importance = channel?.importance ?: NotificationManagerCompat.IMPORTANCE_NONE
        if (importance == NotificationManagerCompat.IMPORTANCE_NONE) {
            Log.e(
                "$TAG Keep alive for third party accounts Service channel has been disabled, can't start foreground service!"
            )
            return
        }

        val service = keepAliveService
        if (service != null) {
            val pendingIntent = TaskStackBuilder.create(context).run {
                addNextIntentWithParentStack(
                    Intent(context, MainActivity::class.java).apply {
                        setAction(Intent.ACTION_MAIN) // Needed as well
                    }
                )
                getPendingIntent(
                    KEEP_ALIVE_FOR_THIRD_PARTY_ACCOUNTS_ID,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )!!
            }

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setAutoCancel(false)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSilent(true)
                .setContentIntent(pendingIntent)
            val notification = builder.build()

            Log.i(
                "$TAG Keep alive for third party accounts Service found, starting it as foreground using notification ID [$KEEP_ALIVE_FOR_THIRD_PARTY_ACCOUNTS_ID] with type [SPECIAL_USE]"
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Compatibility.startServiceForeground(
                    service,
                    KEEP_ALIVE_FOR_THIRD_PARTY_ACCOUNTS_ID,
                    notification,
                    Compatibility.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                service.startForeground(KEEP_ALIVE_FOR_THIRD_PARTY_ACCOUNTS_ID,notification)
            }
            currentKeepAliveThirdPartyAccountsForegroundServiceNotificationId = KEEP_ALIVE_FOR_THIRD_PARTY_ACCOUNTS_ID
        } else {
            Log.w("$TAG Keep alive for third party accounts Service hasn't started yet...")
        }
    }

    @MainThread
    fun onKeepAliveServiceDestroyed() {
        Log.i("$TAG Keep app alive for third party accounts Service has been destroyed")
        stopKeepAliveServiceForeground()
        keepAliveService = null
    }

    @MainThread
    private fun stopKeepAliveServiceForeground() {
        val service = keepAliveService
        if (service != null) {
            Log.i(
                "$TAG Stopping keep alive for third party accounts foreground Service (was using notification ID [$currentKeepAliveThirdPartyAccountsForegroundServiceNotificationId])"
            )
            service.stopForeground(STOP_FOREGROUND_REMOVE)
            service.stopSelf()
            currentKeepAliveThirdPartyAccountsForegroundServiceNotificationId = -1
        } else {
            Log.w(
                "$TAG Can't stop keep alive for third party accounts foreground Service & notif, no Service was found"
            )
        }
    }

    @MainThread
    fun onInCallServiceDestroyed() {
        Log.i("$TAG Service has been destroyed")
        inCallService = null
    }

    @WorkerThread
    fun cancelNotification(id: Int, tag: String? = null) {
        Log.i(
            "$TAG Canceling notification with ID [$id] and ${if (tag == null) "without tag" else "with tag [$tag]"}"
        )
        notificationManager.cancel(tag, id)
    }

    fun checkModeAndSet(): Boolean {
        if (prevAm != 0 || amManager.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            prevAm = amManager.ringerMode
        }

        if (amManager.ringerMode == AudioManager.RINGER_MODE_SILENT || amManager.ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
            Log.i(TAG,"Changing mode to Not Silent or Not Vibrate")
            amManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            return true
        } else {
            Log.i(TAG,"No Need to Reset Ringer Mode Already on Ringer Normal Mode RETURNING [$prevAm]")
            return prevAm != 0
        }
    }
    fun resetMode() {
        Log.i(TAG,"Resetting mode")
        amManager.ringerMode = prevAm
        prevAm = 0
    }

}

class Notifiable(val notificationId: Int) {
    var myself: String? = null

    var localIdentity: String? = null
    var remoteAddress: String? = null

    var isGroup: Boolean = false
    var groupTitle: String? = null
    val messages: ArrayList<NotifiableMessage> = arrayListOf()
}

class NotifiableMessage(
    var message: String,
    val friend: Friend?,
    val sender: String,
    val senderAddress: String,
    val time: Long,
    var filePath: Uri? = null,
    var fileMime: String? = null,
    val isOutgoing: Boolean = false,
    val isReaction: Boolean = false,
    val reactionToMessageId: String? = null,
    val reactionFrom: String? = null
)

//    var notificationManager : NotificationManager?= null
//
//    fun incoming_call_notification_channel(context: Context) {
//
//        notificationManager = context.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
//        if (Build.VERSION.SDK_INT>= Build.VERSION_CODES.O) {
//            val notificationChannel = NotificationChannel(
//                INCOMING_CALL_NOTIFY_CHANNEL_ID,"Incoming Call",
//                NotificationManager.IMPORTANCE_HIGH)
//            notificationChannel.enableVibration(true)
//            notificationManager?.createNotificationChannel(notificationChannel)
//        }
//    }
//
//    fun foreground_notification_channel(context: Context) {
//
//        notificationManager = context.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
//        if (Build.VERSION.SDK_INT>= Build.VERSION_CODES.O) {
//            val notificationChannel = NotificationChannel(
//                FOREGROUND_NOTIFY_CHANNEL_ID,"SavMed Call Service",
//                NotificationManager.IMPORTANCE_HIGH)
//            notificationChannel.enableVibration(true)
//            notificationManager?.createNotificationChannel(notificationChannel)
//        }
//    }
//
//    fun displayIncomingCallNotification(incomingUsername: String,context: Context) : Notification {
//
//        val incomingCallNotificationIntent = Intent(context, Siplogin::class.java)
//        incomingCallNotificationIntent.addFlags(
//            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION or Intent.FLAG_FROM_BACKGROUND
//        )
//        incomingCallNotificationIntent.putExtra("RECEIVING_INCOMING",incomingUsername)
//
//        val pendingIntent = PendingIntent.getActivity(
//            context,
//            0,
//            incomingCallNotificationIntent,
//            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
//        )
//
////        val acceptIntent = Intent(context, NotificationAction::class.java).apply {
////            action = "ACCEPT"
////        }
////        val acceptPendingIntent = PendingIntent.getBroadcast(
////            context,
////            0,
////            acceptIntent,
////            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
////        )
////
////        val hangupIntent = Intent(context, NotificationAction::class.java).apply {
////            action = "HANGUP"
////        }
////        val hangupPendingIntent = PendingIntent.getBroadcast(
////            context,
////            0,
////            hangupIntent,
////            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
////        )
//        val notification = NotificationCompat.Builder(context,
//            INCOMING_CALL_NOTIFY_CHANNEL_ID
//        )
//            .setContentTitle("Incoming Call")
//            .setContentText(incomingUsername)
//            .setSmallIcon(R.drawable.ic_stat_name)
//            .setPriority(NotificationCompat.PRIORITY_HIGH)
//            .addAction(0,"ACCEPT",pendingIntent)
//            .addAction(0,"HANGUP",pendingIntent)
//            .setCategory(NotificationCompat.CATEGORY_CALL)
//            .setOngoing(false)
//            .setAutoCancel(true)
//
//        if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
//            notification.setChannelId(INCOMING_CALL_NOTIFY_CHANNEL_ID)
//        }
//        return  notification.build()
//    }
//
//    fun displayForegroundCallNotification(context: Context) : Notification {
//
//        val callNotificationIntent = Intent(context, MainActivity::class.java)
//        callNotificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//
//        val pendingIntent = PendingIntent.getActivity(
//            context,
//            0,
//            callNotificationIntent,
//            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
//        )
//
//        val notification = NotificationCompat.Builder(context, FOREGROUND_NOTIFY_CHANNEL_ID)
//            .setContentTitle("SavMed Call Service")
//            .setContentText("Keep Notification to Receive Incoming Calls")
//            .setSmallIcon(R.drawable.ic_stat_name)
//            .setPriority(NotificationCompat.PRIORITY_HIGH)
//            .setSound(null)
//            .setSilent(true)
//            .setPriority(NotificationCompat.PRIORITY_HIGH)
//            .setAutoCancel(false)
//            .setContentIntent(pendingIntent)
//            .setOngoing(true)
//        if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
//            notification.setChannelId(FOREGROUND_NOTIFY_CHANNEL_ID)
//        }
//        return  notification.build()
//    }
//
//    companion object {
//        val INCOMING_CALL_NOTIFY_CHANNEL_ID = "INCOMING_CALL_NOTIFICATION"
//        val FOREGROUND_NOTIFY_CHANNEL_ID = "FOREGROUND_NOTIFY"
//    }
//}