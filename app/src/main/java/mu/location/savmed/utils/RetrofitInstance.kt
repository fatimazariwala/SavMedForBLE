package mu.location.savmed.utils

import com.google.gson.Gson
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create

object RetrofitInstance {
    val apiContacts : RetroFit by lazy {
        Retrofit.Builder()
            .baseUrl("https://gosaviour.com/wp-json/wdash/v7/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RetroFit::class.java)
    }

    val apiLiveLocation : RetroFit by lazy {
        Retrofit.Builder()
            .baseUrl("https://gosaviour.com/wp-json/wdash/v8/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RetroFit::class.java)
    }

    val apiLocation : RetroFit by lazy {
        Retrofit.Builder()
            .baseUrl("https://gosaviour.com/wp-json/wdash/v2/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RetroFit::class.java)
    }

    val apiEmergencyContacts: RetroFit by lazy {
        Retrofit.Builder()
            .baseUrl("https://gosaviour.com/wp-json/wdash/v9/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RetroFit::class.java)
    }

    val apiNearBy: RetroFit by lazy {
        Retrofit.Builder()
            .baseUrl("https://gosaviour.com/wp-json/wdash/vN/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RetroFit::class.java)
    }
    val apiRegistration : RetroFit by lazy {
        Retrofit.Builder()
            .baseUrl("https://gosaviour.com/wp-json/wdash/v3/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RetroFit::class.java)
    }
}