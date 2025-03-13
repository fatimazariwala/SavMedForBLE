package mu.location.savmed.ui.chat.viewModel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import com.google.android.material.bottomsheet.BottomSheetBehavior
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.ui.chat.model.MessageModel
import mu.location.savmed.utils.Event
import org.linphone.core.tools.Log

class ChatMessageLongPressViewModel {
    companion object {
        const val TAG = "[Chat Message LongPress ViewModel]"
    }

    val visible = MutableLiveData<Boolean>()

    val hideForward = MutableLiveData<Boolean>()

    val hideCopyTextToClipboard = MutableLiveData<Boolean>()

    val horizontalBias = MutableLiveData<Float>()

    val messageModel = MutableLiveData<MessageModel>()

    val isMessageOutgoing = MutableLiveData<Boolean>()

    val isMessageInError = MutableLiveData<Boolean>()

    val replyToMessageEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val forwardMessageEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val deleteMessageEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val onDismissedEvent = MutableLiveData<Event<Boolean>>()

    init {
        visible.value = false
    }

    @UiThread
    fun setMessage(model: MessageModel) {
        hideCopyTextToClipboard.value = model.text.value.isNullOrEmpty()
        isMessageOutgoing.value = model.isOutgoing
        isMessageInError.value = model.isInError
        horizontalBias.value = if (model.isOutgoing) 1f else 0f
        messageModel.value = model
    }

    @UiThread
    fun dismiss() {
        onDismissedEvent.value = Event(true)
        visible.value = false
    }

    @UiThread
    fun resend() {
        Log.i("$TAG Re-sending message in error state")
        messageModel.value?.resend()
        dismiss()
    }

    @UiThread
    fun reply() {
        Log.i("$TAG Replying to message")
        replyToMessageEvent.value = Event(true)
        dismiss()
    }

    @UiThread
    fun copyClickListener() {
        Log.i(TAG,"Copying message text into clipboard")

        val text = messageModel.value?.text?.value.toString()
        val clipboard = coreContext.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val label = "Message"
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))

        dismiss()
    }

    @UiThread
    fun forwardClickListener() {
        Log.i("$TAG Forwarding message")
        forwardMessageEvent.value = Event(true)
        dismiss()
    }

    @UiThread
    fun deleteClickListener() {
        Log.i("$TAG Deleting message")
        deleteMessageEvent.value = Event(true)
        dismiss()
    }


}