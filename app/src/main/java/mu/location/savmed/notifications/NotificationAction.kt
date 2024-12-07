package mu.location.savmed.notifications

import android.app.NotificationManager
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import mu.location.savmed.SavMed.Companion.coreContext
import org.linphone.core.Address
import org.linphone.core.ChatRoomParams
import org.linphone.core.ConferenceParams

class NotificationAction : BroadcastReceiver() {
    companion object {
        private const val TAG = "[Notification Broadcast Receiver]"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val notificationId = intent?.getIntExtra(NotificationsManager.INTENT_NOTIF_ID, 0)
        org.linphone.core.tools.Log.i(
            "$TAG Got notification broadcast for ID [$notificationId]"
        )

        // Wait for coreContext to be ready to handle intent
        while (!coreContext.isReady()) {
            Thread.sleep(50)
        }

        if (intent?.action == NotificationsManager.INTENT_ANSWER_CALL_NOTIF_ACTION || intent?.action == NotificationsManager.INTENT_HANGUP_CALL_NOTIF_ACTION) {
            if (notificationId != null) {
                handleCallIntent(intent,notificationId)
            }
        }  else if (intent != null) {
            if (intent.action == NotificationsManager.INTENT_REPLY_MESSAGE_NOTIF_ACTION || intent.action == NotificationsManager.INTENT_MARK_MESSAGE_AS_READ_NOTIF_ACTION) {
                if (context != null) {
                    if (notificationId != null) {
                        handleChatIntent(context, intent, notificationId)
                    }
                }
            }
        }
    }

    private fun handleChatIntent(context: Context, intent: Intent, notificationId: Int) {
        val remoteSipAddress = intent.getStringExtra(NotificationsManager.INTENT_REMOTE_ADDRESS)
        if (remoteSipAddress == null) {
            Log.e(TAG,"Remote SIP address is null for notification ID [$notificationId]")
            return
        }
        val localIdentity = intent.getStringExtra(NotificationsManager.INTENT_LOCAL_IDENTITY)
        if (localIdentity == null) {
            Log.e(TAG ,"Local identity is null for notification ID [$notificationId]")
            return
        }

        val reply = getMessageText(intent)?.toString()
        if (intent.action == NotificationsManager.INTENT_REPLY_MESSAGE_NOTIF_ACTION) {
            if (reply == null) {
                Log.e(TAG,"Couldn't get reply text")
                return
            }
        }

        coreContext.postOnCoreThread { core ->
            val remoteAddress = core.interpretUrl(remoteSipAddress, false)
            if (remoteAddress == null) {
                Log.e(TAG,
                    "Couldn't interpret remote address [$remoteSipAddress]"
                )
                return@postOnCoreThread
            }

            val localAddress = core.interpretUrl(localIdentity, false)
            if (localAddress == null) {
                Log.e(TAG,
                    "Couldn't interpret local address [$localIdentity]"
                )
                return@postOnCoreThread
            }

            val params: ChatRoomParams? = null
            val room = core.searchChatRoom(
                params,
                localAddress,
                remoteAddress,
                arrayOfNulls<Address>(
                    0
                )
            )
            if (room == null) {
                Log.e(TAG,
                    "Couldn't find conversation for remote address [$remoteSipAddress] and local address [$localIdentity]"
                )
                return@postOnCoreThread
            }

            if (intent.action == NotificationsManager.INTENT_REPLY_MESSAGE_NOTIF_ACTION) {
                val msg = room.createMessageFromUtf8(reply)
                msg.userData = notificationId
                msg.addListener(coreContext.notificationManager.chatMessageListener)
                msg.send()
                room.markAsRead()
                Log.i(TAG,"Reply sent for notif id [$notificationId]")
            } else if (intent.action == NotificationsManager.INTENT_MARK_MESSAGE_AS_READ_NOTIF_ACTION) {
                Log.i(TAG,"Marking chat room from notification id [$notificationId] as read")
                room.markAsRead()
                if (!coreContext.notificationManager.dismissChatNotification(room)) {
                    Log.w(TAG,
                        "Notifications Manager failed to cancel notification"
                    )
                    val notificationManager = context.getSystemService(
                        NotificationManager::class.java
                    )
                    notificationManager.cancel(NotificationsManager.CHAT_TAG, notificationId)
                }
            }
        }
    }

    private fun getMessageText(intent: Intent): CharSequence? {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        return remoteInput?.getCharSequence(NotificationsManager.KEY_TEXT_REPLY)
    }

    private fun handleCallIntent(intent: Intent?,notificationId: Int) {
        val remoteSipAddress = intent?.getStringExtra(NotificationsManager.INTENT_REMOTE_ADDRESS)
        if (remoteSipAddress == null) {
            org.linphone.core.tools.Log.e("$TAG Remote SIP address is null for call notification ID [$notificationId]")
            return
        }

        coreContext.postOnCoreThread { core ->
            val call = core.calls.find {
                it.remoteAddress.asStringUriOnly() == remoteSipAddress
            }
            if (call == null) {
                org.linphone.core.tools.Log.e("$TAG Couldn't find call from remote address [$remoteSipAddress]")
            } else {
                if (intent.action == NotificationsManager.INTENT_ANSWER_CALL_NOTIF_ACTION) {
                    coreContext.answerCall(call)
                } else {
                    coreContext.terminateCall(call)
                }
            }
        }
    }

}

//if (intent != null) {
//    if((intent.action) == "ACCEPT") {
//        Log.i("uoooo","Im in intent.action")
//
//        val sipIntent = Intent(context, SipActiivty::class.java).apply {
//            flags = Intent.FLAG_ACTIVITY_NEW_TASK // Required to start activity from non-activity context
//        }
//        context!!.startActivity(sipIntent)
//
//        val notificationManager = context?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//        notificationManager.cancel(123)
//    }
//
//    if((intent.action) == "HANGUP") {
//        val sipIntent = Intent(context, SipActiivty::class.java).apply {
//            flags = Intent.FLAG_ACTIVITY_NEW_TASK // Required to start activity from non-activity context
//        }
//        context?.startActivity(sipIntent)
//
//        val notificationManager = context?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//        notificationManager.cancel(123)
//    }
//} else {
//    Log.i("FROM BROADCAST","NO INTENT FETCHED")
////}