package mu.location.savmed.ui.chat.chatNew.model

import android.graphics.drawable.Drawable
import androidx.annotation.WorkerThread
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.utils.SavMedUtils
import org.linphone.core.EventLog
import org.linphone.core.tools.Log

class EventLogModel @WorkerThread constructor(
    val eventLog: EventLog,
    isGroupedWithPreviousOne: Boolean = false,
    isGroupedWithNextOne: Boolean = false,
    onContentClicked: ((fileModel: FileModel) -> Unit)? = null,
    onWebUrlClicked: ((url: String) -> Unit)? = null,
    onContactClicked: ((friendRefKey: String) -> Unit)? = null,
    onToastToShow: ((msg: String) -> Unit)? = null
) {
    companion object{
        private const val TAG = "[Event Log Model]"
    }

    val type: EventLog.Type = eventLog.type

    val isEvent = type != EventLog.Type.ConferenceChatMessage

    val model: Any = if (!isEvent) {
        val chatMessage = eventLog.chatMessage!!
        var replyTo = ""
        var isReply = chatMessage.isReply
        val replyText = if (chatMessage.isReply) {
            val replyMessage = chatMessage.replyMessage
            if (replyMessage != null) {
                val from = replyMessage.fromAddress
                //val avatarModel = coreContext.contactsManager.getContactAvatarModelForAddress(from)
                replyTo = SavMedUtils.getDisplayName(from)

                SavMedUtils.getPlainTextDescribingMessage(replyMessage)
            } else {
                Log.e(
                    "$TAG Failed to find the reply message from ID [${chatMessage.replyMessageId}]"
                )
                isReply = false
                ""
            }
        } else {
            ""
        }

        MessageModel(
            chatMessage,
            isReply,
            replyTo,
            replyText,
            chatMessage.replyMessageId,
            chatMessage.isForward,
            isGroupedWithPreviousOne,
            isGroupedWithNextOne,
            onContentClicked,
            onWebUrlClicked,
            onContactClicked,
            onToastToShow
        )
    } else {
        Log.i(TAG,"Conference Not Supported!")
    }

    val notifyId = eventLog.notifyId

    @WorkerThread
    fun destroy() {
        (model as? MessageModel)?.destroy()
    }
}
