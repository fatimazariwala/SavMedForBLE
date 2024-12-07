package mu.location.savmed.utils

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import androidx.annotation.AnyThread
import androidx.annotation.DrawableRes
import androidx.annotation.WorkerThread
import androidx.core.text.toSpannable
import mu.location.savmed.R
import mu.location.savmed.SavMed.Companion.coreContext
import org.linphone.core.Account
import org.linphone.core.Address
import org.linphone.core.Call
import org.linphone.core.Call.Dir
import org.linphone.core.Call.Status
import org.linphone.core.CallLog
import org.linphone.core.ChatMessage
import org.linphone.core.ChatRoom
import org.linphone.core.ConferenceInfo
import org.linphone.core.Factory
import org.linphone.core.Reason
import org.linphone.core.tools.Log
import java.text.SimpleDateFormat
import java.util.Locale

class SavMedUtils {

    companion object {
        private const val TAG = "[Linphone Utils]"

        const val RECORDING_FILE_NAME_HEADER = "call_recording_"
        const val RECORDING_FILE_NAME_URI_TIMESTAMP_SEPARATOR = "_on_"
        const val RECORDING_FILE_EXTENSION = ".smff"

        private const val CHAT_ROOM_ID_SEPARATOR = "#~#"

        @WorkerThread
        fun getDefaultAccount(): Account? {
            return coreContext.core.defaultAccount ?: coreContext.core.accountList.firstOrNull()
        }

        @WorkerThread
        fun isChatRoomAGroup(chatRoom: ChatRoom): Boolean {
            val oneToOne = chatRoom.hasCapability(ChatRoom.Capabilities.OneToOne.toInt())
            val conference = chatRoom.hasCapability(ChatRoom.Capabilities.Conference.toInt())
            return !oneToOne && conference
        }
        @WorkerThread
        fun getAccountForAddress(address: Address): Account? {
            return coreContext.core.accountList.find {
                it.params.identityAddress?.weakEqual(address) == true
            }
        }

        @WorkerThread
        fun getAddressAsCleanStringUriOnly(address: Address): String {
            val scheme = address.scheme ?: "sip"
            val username = address.username
            if (username.orEmpty().isEmpty()) {
                return "$scheme:${address.domain}"
            }
            return  "$scheme:$username@${address.domain}"
        }

        @WorkerThread
        fun getDisplayName(address: Address?): String {
            if (address == null) return "[null]"
            if (address.displayName == null) {
                val account = coreContext.core.accountList.find { account ->
                    account.params.identityAddress?.asStringUriOnly() == address.asStringUriOnly()
                }
                val localDisplayName = account?.params?.identityAddress?.displayName
                // Do not return an empty local display name
                if (!localDisplayName.isNullOrEmpty()) {
                    return localDisplayName
                }
            }
            // Do not return an empty display name
            return address.displayName ?: address.username ?: address.asString()
        }

        @AnyThread
        @DrawableRes
        fun getChatIconResId(chatState: ChatMessage.State): Int {
            return when (chatState) {
                ChatMessage.State.Displayed, ChatMessage.State.FileTransferDone -> {
                    R.drawable.checks
                }
                ChatMessage.State.DeliveredToUser -> {
                    R.drawable.check
                }
                ChatMessage.State.Delivered -> {
                    R.drawable.envelope_simple
                }
                ChatMessage.State.NotDelivered, ChatMessage.State.FileTransferError -> {
                    R.drawable.warning_circle
                }
                ChatMessage.State.InProgress, ChatMessage.State.FileTransferInProgress -> {
                    R.drawable.animated_in_progress
                }
                else -> {
                    R.drawable.animated_in_progress
                }
            }
        }

        // WHat is this fun doing??
        @WorkerThread
        fun getFormattedTextDescribingMessage(message: ChatMessage): Spannable {
            val pair = getTextDescribingMessage(message)
            val builder = SpannableStringBuilder(
                "${pair.first} ${pair.second}".trim()
            )
            if (pair.first.isNotEmpty()) { // prevent error log due to zero length exclusive span
                builder.setSpan(
                    StyleSpan(Typeface.ITALIC),
                    0,
                    pair.first.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            return builder.toSpannable()
        }

        @WorkerThread
        fun getPlainTextDescribingMessage(message: ChatMessage): String {
            val pair = getTextDescribingMessage(message)
            return "${pair.first} ${pair.second}".trim()
        }

        @WorkerThread
        fun getTextDescribingMessage(message: ChatMessage): Pair<String, String> {
            // If message contains text, then use that
            var text = message.contents.find { content -> content.isText }?.utf8Text ?: ""
            var contentDescription = ""

            if (text.isEmpty()) {
                val firstContent = message.contents.firstOrNull()
                if (firstContent?.isIcalendar == true) {
                    val conferenceInfo = Factory.instance().createConferenceInfoFromIcalendarContent(
                        firstContent
                    )
                    text = firstContent.name.orEmpty()

                } else if (firstContent?.isVoiceRecording == true) {
                    val label = AppUtils.getString(
                        R.string.message_voice_message_content_description
                    )
                    val formattedDuration = SimpleDateFormat(
                        "mm:ss",
                        Locale.getDefault()
                    ).format(firstContent.fileDuration) // duration is in ms
                    contentDescription = "$label ($formattedDuration)"
                } else {
                    for (content in message.contents) {
                        if (text.isNotEmpty()) {
                            text += ", "
                        }
                        text += content.name
                    }
                }
            }

            return Pair(contentDescription, text)
        }

        @WorkerThread
        fun getChatRoomId(room: ChatRoom): String {
            return getChatRoomId(room.localAddress, room.peerAddress)
        }

        @WorkerThread
        fun getChatRoomId(localAddress: Address, remoteAddress: Address): String {
            val localSipUri = localAddress.clone()
            localSipUri.clean()
            val remoteSipUri = remoteAddress.clone()
            remoteSipUri.clean()
            return getChatRoomId(localSipUri.asStringUriOnly(), remoteSipUri.asStringUriOnly())
        }

        @AnyThread
        fun getChatRoomId(localSipUri: String, remoteSipUri: String): String {
            return "$localSipUri$CHAT_ROOM_ID_SEPARATOR$remoteSipUri"
        }

        @AnyThread
        fun isCallIncoming(callState: Call.State): Boolean {
            return when (callState) {
                Call.State.IncomingReceived, Call.State.IncomingEarlyMedia -> true
                else -> false
            }
        }

        @AnyThread
        fun isCallOutgoing(callState: Call.State, considerEarlyMedia: Boolean = true): Boolean {
            return when (callState) {
                Call.State.OutgoingInit, Call.State.OutgoingProgress, Call.State.OutgoingRinging -> true
                Call.State.OutgoingEarlyMedia -> considerEarlyMedia
                else -> false
            }
        }

        @AnyThread
        fun isCallPaused(callState: Call.State): Boolean {
            return when (callState) {
                Call.State.Pausing, Call.State.Paused, Call.State.PausedByRemote, Call.State.Resuming -> true
                else -> false
            }
        }

        @AnyThread
        fun isCallEnding(callState: Call.State): Boolean {
            return when (callState) {
                Call.State.End, Call.State.Error -> true
                else -> false
            }
        }

        @WorkerThread
        fun getCallErrorInfoToast(call: Call): String {
            val errorInfo = call.errorInfo
            Log.w(
                "$TAG Call error reason is [${errorInfo.reason}](${errorInfo.protocolCode}): ${errorInfo.phrase}"
            )
            return when (errorInfo.reason) {
                Reason.Busy -> {
                    "BUSY_NOTIFY"
                }
                Reason.IOError -> {
                   "IO_ERROR_NOTIFY"
                }
                Reason.NotAcceptable -> {
                    "CALL_NOT_ACCEPTABLE_NOTIFY"
                }
                Reason.NotFound -> {
                    "USER_NOT_FOUND_NOTIFY"
                }
                Reason.ServerTimeout -> {
                    "TIMEOUT_NOTIFY"
                }
                Reason.TemporarilyUnavailable -> {
                    "SERVICE_TEMPORARILY_UNAVAILABLE_NOTIFY"
                }
                else -> {
                    "${errorInfo.protocolCode} / ${errorInfo.phrase}"
                }
            }
        }

        @WorkerThread
        fun isCallLogMissed(callLog: CallLog): Boolean {
            if (callLog.dir == Dir.Outgoing) return false
            return callLog.status == Status.Missed ||
                    callLog.status == Status.Aborted ||
                    callLog.status == Status.EarlyAborted
        }
    }
}