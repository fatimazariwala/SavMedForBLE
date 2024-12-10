//package mu.location.savmed.ui.auth.EmergencyContacts
//
//import android.util.Log
//import androidx.lifecycle.LiveData
//import androidx.lifecycle.MutableLiveData
//import androidx.lifecycle.ViewModel
//import androidx.lifecycle.viewModelScope
//import kotlinx.coroutines.launch
//import mu.location.savmed.SavMed.Companion.coreContext
//import mu.location.savmed.ui.auth.EmergencyContacts.EmergencyContactsFragment.Companion
//import mu.location.savmed.utils.RetrofitInstance
//import retrofit2.Call
//import retrofit2.Callback
//import retrofit2.HttpException
//import retrofit2.Response
//import java.io.IOException
//
//class EmergencyContactsViewModel: ViewModel() {
//
//    companion object {
//        const val TAG = "[EMR ViewModel]"
//    }
//
//    private val _contactsList = MutableLiveData<List<EmergencyContact>>()
//    val contactsList: LiveData<List<EmergencyContact>> = _contactsList
//
//    private val _postContactsStatus = MutableLiveData<Boolean>()
//    val postContactsStatus: LiveData<Boolean> = _postContactsStatus
//
//    fun getEmergencyContacts(userName: String) {
//        Log.i(TAG,"$userName in Er")
//        viewModelScope.launch {
//            try {
//                val data = RetrofitInstance.apiEmergencyContacts.getEmergencyContacts(userName)
//                if (data.isSuccessful) {
//                    _contactsList.value = data.body()!!
//                } else {
//                    _contactsList.value = emptyList()
//                }
//            } catch (e : HttpException) {
//                Log.i(TAG,e.message().toString())
//                _contactsList.value = emptyList()
//            } catch (e: IOException) {
//                Log.i(TAG,e.message.toString())
//                _contactsList.value = emptyList()
//            }
//        }
//    }
//
//    fun postEmergencyContacts(emergencyContacts: EmergencyContacts) {
//        viewModelScope.launch {
//            val call: Call<EmergencyContacts?>? = try {
//                for (con in emergencyContacts.emergencyContacts) {
//                    Log.i(TAG,"from yoo ${con.contact}")
//                }
//                RetrofitInstance.apiEmergencyContacts.postEmergencyContacts(emergencyContacts)
//            } catch (e: IOException) {
//                Log.i(TAG, e.message.toString())
//                return@launch
//            } catch (e: HttpException) {
//                Log.i(TAG, e.message.toString())
//                return@launch
//            }
//
//            call?.enqueue(object: Callback<EmergencyContacts?>{
//                override fun onResponse(
//                    call: Call<EmergencyContacts?>,
//                    response: Response<EmergencyContacts?>
//                ) {
//                    val res = response.body()
//                    _postContactsStatus.value = true
//                   Log.i(TAG,"Post Response: $res")
//                }
//                override fun onFailure(call: Call<EmergencyContacts?>, t: Throwable) {
//                    _postContactsStatus.value = false
//                    Log.i(TAG,"Post Response Failure: ${t.message}")
//                }
//            })
//        }
//    }
//
//}