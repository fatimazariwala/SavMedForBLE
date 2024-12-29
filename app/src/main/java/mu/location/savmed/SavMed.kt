package mu.location.savmed

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.os.Handler
import android.os.Looper
import com.google.android.material.color.DynamicColors
import mu.location.savmed.bluetooth.bluetoothClassic.BluetoothController
import mu.location.savmed.bluetooth.bluetoothLE.BluetoothLEController
import mu.location.savmed.bluetooth.bluetoothLE.controls.BLEClient
//import mu.location.savmed.bluetooth.bluetoothLE.controls.BLEControllerFactory
import mu.location.savmed.bluetooth.bluetoothLE.controls.BLEServer
import mu.location.savmed.models.CoreContext
import mu.location.savmed.models.CorePreferences
import mu.location.savmed.utils.ActivityMonitor
import mu.location.savmed.utils.SharedPreference
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

//        lateinit var bluetoothController: BluetoothController
//        lateinit var bluetoothLEController: BluetoothLEController

        @SuppressLint("StaticFieldLeak")
        lateinit var bleServer: BLEServer
        @SuppressLint("StaticFieldLeak")
        lateinit var bleClient: BLEClient

        lateinit var bluetoothManager: BluetoothManager

        val bluetoothAdapter by lazy {
            bluetoothManager.adapter
        }

       // lateinit var sharedMainViewModel: SharedMainViewModel
    }

    private val activityMonitor = ActivityMonitor()

    private val mainThread = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        val context = applicationContext

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

        bluetoothManager = getSystemService(BluetoothManager::class.java)
        bleServer = BLEServer(context)
        bleClient = BLEClient(context)
//        Compatibility.setupAppStartupListener(context)
//
//        bluetoothController = AndroidBluetoothController(applicationContext)

        //Below lin eis comented out on 24/11/2024 at 5:22
        //Error was getBluetoothLeAdvertiser(...) must not be null
       // bluetoothLEController = BLEControllerFactory.createBluetoothController(applicationContext)
        coreContext = CoreContext(context)
        coreContext.start()

        DynamicColors.applyToActivitiesIfAvailable(this)
    }
//    val coreContext: CoreContext by lazy {
//        ViewModelProvider.AndroidViewModelFactory.getInstance(this)
//            .create(CoreContext::class.java)
//    }

    override fun onTerminate() {
        super.onTerminate()
        Log.i(TAG,"IN Termination....")
        coreContext.core.consolidatedPresence = ConsolidatedPresence.Offline
    }


}