package mu.location.savmed.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager

import androidx.core.content.ContextCompat

object SettingsManager {

    fun hasPermission(permission: String, context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}

object SharedPreference {

    lateinit var username: String
    lateinit var priKey: String
    lateinit var sharedPreferences: SharedPreferences

    fun init(context: Context) {
        sharedPreferences = context.getSharedPreferences("shared_prefs", Context.MODE_PRIVATE)
        username = sharedPreferences.getString("username_key", "").orEmpty()
        priKey = sharedPreferences.getString("pri_key","").orEmpty()
    }

    fun clear() {
        sharedPreferences.edit().clear().apply()
    }
}