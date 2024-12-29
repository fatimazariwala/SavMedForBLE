package mu.location.savmed.ui.contacts.models

import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData

class ResAddressOrEmailModel @WorkerThread constructor(
    defaultValue: String,
    val isEmail: Boolean,
    private val onValueNoLongerEmpty: (() -> Unit)? = null,
    private val onRemove: ((model: ResAddressOrEmailModel) -> Unit)? = null
) {
    val valueOfField = MutableLiveData<String>()

    val showRemoveButton = MutableLiveData<Boolean>()

    init {
        valueOfField.postValue(defaultValue)
        showRemoveButton.postValue(defaultValue.isNotEmpty())
    }

    fun onValueChanged(newValue: String) {
        if (newValue.isNotEmpty() && showRemoveButton.value == false) {
            onValueNoLongerEmpty?.invoke()
            showRemoveButton.value = true
        }
    }

    fun remove() {
        onRemove?.invoke(this)
    }
}