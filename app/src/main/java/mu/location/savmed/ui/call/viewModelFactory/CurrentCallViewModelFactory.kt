package mu.location.savmed.ui.call.viewModelFactory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import mu.location.savmed.ui.contacts.models.EndSwitchCallBack
import mu.location.savmed.ui.call.viewModels.CurrentCallViewModel

class CurrentCallViewModelFactory(private val endSwitchCallBack: EndSwitchCallBack) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CurrentCallViewModel::class.java)) {
            return CurrentCallViewModel(endSwitchCallBack) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}