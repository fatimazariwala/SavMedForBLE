package mu.location.savmed.models

import android.content.Context
import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import mu.location.savmed.R
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.contacts.ContactsManager.Companion.SAVMED_ADDRESS_BOOK_FRIEND_LIST
import org.linphone.core.Config
import org.linphone.core.BuildConfig
import java.io.File
import java.io.FileOutputStream

class CorePreferences @UiThread constructor(private val context: Context){

    companion object {
        private const val TAG = "[Preferences]"

        const val CONFIG_FILE_NAME = ".savMedrc"
    }

    private var _config: Config? = null

    @get:WorkerThread
    @set:WorkerThread
    var config: Config
        get() = _config ?: coreContext.core.config
        set(value) {
            _config = value
        }

    @get:WorkerThread
    @set:WorkerThread
    var printLogsInLogcat: Boolean
        get() = config.getBool("app", "debug", BuildConfig.DEBUG)
        set(value) {
            config.setBool("app", "debug", value)
        }

    @get:WorkerThread @set:WorkerThread
    var firstLaunch: Boolean
        get() = config.getBool("app", "first_6.0_launch", true)
        set(value) {
            config.setBool("app", "first_6.0_launch", value)
        }

    @get:WorkerThread @set:WorkerThread
    var publishPresence: Boolean
        get() = config.getBool("app", "publish_presence", true)
        set(value) {
            config.setBool("app", "publish_presence", value)
        }

    @get:WorkerThread @set:WorkerThread
    var showFavoriteContacts: Boolean
        get() = config.getBool("ui", "show_favorites_contacts", true)
        set(value) {
            config.setBool("ui", "show_favorites_contacts", true)
        }

    @get:WorkerThread @set:WorkerThread
    var contactsFilter: String
        get() = config.getString("ui", "contacts_filter", "")!! // Default value must be empty!
        set(value) {
            config.setString("ui", "contacts_filter", value)
        }

    @get:WorkerThread @set:WorkerThread
    var friendListInWhichStoreNewlyCreatedFriends: String
        get() = config.getString(
            "app",
            "friend_list_to_store_newly_created_contacts",
            SAVMED_ADDRESS_BOOK_FRIEND_LIST
        )!!
        set(value) {
            config.setString("app", "friend_list_to_store_newly_created_contacts", value)
        }

    @get:WorkerThread @set:WorkerThread
    var darkMode: Int
        get() {
            if (!darkModeAllowed) return 0
            return config.getInt("app", "dark_mode", -1)
        }
        set(value) {
            config.setInt("app", "dark_mode", value)
        }

    @get:WorkerThread
    val darkModeAllowed: Boolean
        get() = config.getBool("ui", "dark_mode_allowed", true)

    @get:WorkerThread @set:WorkerThread
    var themeMainColor: String
        get() = config.getString("ui", "theme_main_color", "blue")!!
        set(value) {
            config.setString("ui", "theme_main_color", value)
        }


    @get:WorkerThread @set:WorkerThread
    var keepServiceAlive: Boolean
        get() = config.getBool("app", "keep_service_alive", false)
        set(value) {
            config.setBool("app", "keep_service_alive", value)
        }

    @get:WorkerThread @set:WorkerThread
    var voiceRecordingMaxDuration: Int
        get() = config.getInt("app", "voice_recording_max_duration", 600000) // in ms
        set(value) = config.setInt("app", "voice_recording_max_duration", value)

    @get:AnyThread
    val configPath: String
        get() = context.filesDir.absolutePath + "/" + CONFIG_FILE_NAME

    @get:AnyThread
    val factoryConfigPath: String
        get() = context.filesDir.absolutePath + "/savMedrc"

    @get:AnyThread
    val savMedDefaultValuesPath: String
        get() = context.filesDir.absolutePath + "/savMedrc_default_values"

    @UiThread
    fun copyAssetsFromPackage() {
        copy("savMedrc_default", configPath)
        copy("savMedrc_factory", factoryConfigPath, true)
        copy("savMed_default_values", savMedDefaultValuesPath, true)
    }

    @AnyThread
    private fun copy(from: String, to: String, overrideIfExists: Boolean = false) {
        val outFile = File(to)
        if (outFile.exists()) {
            if (!overrideIfExists) {
                android.util.Log.i(
                    context.getString(R.string.app_name),
                    "$TAG File $to already exists"
                )
                return
            }
        }
        android.util.Log.i(
            context.getString(R.string.app_name),
            "$TAG Overriding $to by $from asset"
        )

        val outStream = FileOutputStream(outFile)
        val inFile = context.assets.open(from)
        val buffer = ByteArray(1024)
        var length: Int = inFile.read(buffer)

        while (length > 0) {
            outStream.write(buffer, 0, length)
            length = inFile.read(buffer)
        }

        inFile.close()
        outStream.flush()
        outStream.close()
    }

}