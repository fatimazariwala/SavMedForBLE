package mu.location.savmed.utils

import mu.location.savmed.ui.auth.LoginRequest
import mu.location.savmed.ui.auth.registrationDetails
import mu.location.savmed.ui.medical.MedicalInfo
import mu.location.savmed.models.Users
import mu.location.savmed.ui.auth.EmergencyContacts.EmergencyContact
import mu.location.savmed.ui.auth.EmergencyContacts.EmergencyContactResponse
import mu.location.savmed.ui.locationing.liveLocationData
import mu.location.savmed.ui.locationing.locationData
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

interface RetroFit {
    @Headers("Content-Type: application/json")
    @POST("post")
    fun postData(@Body userData: registrationDetails?): Call<registrationDetails?>?

    @Headers("Content-Type: application/json")
    @POST("post")
    fun postLiveLocationData(@Body liveLocationData: liveLocationData?) : Call<liveLocationData?>?

    @Headers("Content-Type: application/json")
    @POST("posts")
    fun postLocationData(@Body locationData: locationData?) : Call<locationData?>?

    @Headers("Content-Type: application/json")
    @POST("post")
    fun postMedicalData(@Body MedicalInfo : MedicalInfo?) : Call<MedicalInfo?>?

    @Headers("Content-Type: application/json")
    @POST("post")
    fun postLoginRequest(@Body LoginRequest : LoginRequest?) : Call<LoginRequest?>?

    @Headers("Content-Type: application/json")
    @POST("post")
    fun postEmergencyContacts(@Body EmergencyContacts: EmergencyContact): Call<EmergencyContact?>?

    @Headers("Content-Type: application/json")
    @POST("delete")
    fun deleteEmergencyContacts(@Body EmergencyContacts: EmergencyContact): Call<EmergencyContact?>?

    @GET("get")
    suspend fun getcallUsers(): Response<Users>

    @GET("get")
    suspend fun getEmergencyContacts(@Query("userName") userName: String): Response<List<EmergencyContactResponse>>
}