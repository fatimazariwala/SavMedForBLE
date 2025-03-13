package mu.location.savmed

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import mu.location.savmed.bluetooth.bluetoothLE.controls.BLEClient
//import mu.location.savmed.bluetooth.bluetoothLE.controls.BLEControllerFactory
import mu.location.savmed.bluetooth.bluetoothLE.controls.BLEServer
import mu.location.savmed.models.CoreContext
import mu.location.savmed.models.CorePreferences
import mu.location.savmed.utils.ActivityMonitor
import mu.location.savmed.utils.SharedPreference
import mu.location.savmed.websocket.WsDetails
import org.linphone.core.ConsolidatedPresence
import org.linphone.core.Factory
import org.linphone.core.LogCollectionState
import org.linphone.core.LogLevel
import org.linphone.core.tools.Log

class SavMed : Application() {

    companion object {
        private const val TAG = "[SavMed Application]"

        @SuppressLint("StaticFieldLeak")
        lateinit var corePreferences: CorePreferences

        @SuppressLint("StaticFieldLeak")
        lateinit var coreContext: CoreContext

        lateinit var webSocket: WsDetails

        @SuppressLint("StaticFieldLeak")
        lateinit var bleServer: BLEServer
        @SuppressLint("StaticFieldLeak")
        lateinit var bleClient: BLEClient

        lateinit var bluetoothManager: BluetoothManager

        val bluetoothAdapter by lazy {
            bluetoothManager.adapter
        }

        fun isWebSocketInitialized(): Boolean {
            return ::webSocket.isInitialized
        }

    }

    private val activityMonitor = ActivityMonitor()

    private val mainThread = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        val context = applicationContext

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        Factory.instance().setLogCollectionPath(context.filesDir.absolutePath)
        Factory.instance().enableLogCollection(LogCollectionState.Enabled)
        SharedPreference.init(applicationContext)

        corePreferences = CorePreferences(context)
        corePreferences.copyAssetsFromPackage()

        val config = Factory.instance().createConfigWithFactory(
            corePreferences.configPath,
            corePreferences.factoryConfigPath
        )
        corePreferences.config = config

        val appName = context.getString(R.string.app_name)
        Factory.instance().setLoggerDomain(appName)
        Factory.instance().loggingService.setLogLevel(LogLevel.Message)
        Factory.instance().enableLogcatLogs(corePreferences.printLogsInLogcat)

        Log.i("$TAG Report Core preferences initialized")

        webSocket = WsDetails(context)

        bluetoothManager = getSystemService(BluetoothManager::class.java)
        bleServer = BLEServer(context)
        bleClient = BLEClient(context)
//        Compatibility.setupAppStartupListener(context)

        coreContext = CoreContext(context)
        coreContext.start()

       DynamicColors.applyToActivitiesIfAvailable(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.i(TAG,"IN Termination....")
        coreContext.core.consolidatedPresence = ConsolidatedPresence.Offline
    }

}