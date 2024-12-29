package mu.location.savmed.ui.chat.model

import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import mu.location.savmed.utils.Event

class ConfirmationDialogModel @UiThread constructor() {
    val dismissEvent = MutableLiveData<Event<Boolean>>()

    val cancelEvent = MutableLiveData<Event<Boolean>>()

    val confirmEvent = MutableLiveData<Event<Boolean>>()

    @UiThread
    fun dismiss() {
        dismissEvent.value = Event(true)
    }

    @UiThread
    fun cancel() {
        cancelEvent.value = Event(true)
    }

    @UiThread
    fun confirm() {
        confirmEvent.value = Event(true)
    }
}