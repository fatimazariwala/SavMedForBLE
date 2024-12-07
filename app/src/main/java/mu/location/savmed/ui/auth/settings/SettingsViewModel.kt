package mu.location.savmed.ui.auth.settings

import androidx.annotation.UiThread
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.utils.Event
import org.linphone.core.Account
import org.linphone.core.NatPolicy
import org.linphone.core.TransportType
import org.linphone.core.tools.Log

class SettingsClass {

    companion object {
        private const val TAG = "[Account Settings ViewModel]"

        private lateinit var account: Account
        private lateinit var natPolicy: NatPolicy

        fun findAccountMatchingIdentity() {
            Log.i("In findAccountMacthing identity","yooo")
            coreContext.postOnCoreThread { core ->

                if (core.defaultAccount != null) {
                    account = core.defaultAccount!!

                    val params = account.params

                    natPolicy = params.natPolicy ?: core.createNatPolicy()

                    Log.i(
                        "$TAG Account Data ${account.contactAddress?.username.toString()} : 1) PushNotification: ${core.isPushNotificationAvailable} allowed ${params.pushNotificationAllowed},\n 2) LimeServer : ${params.limeServerUrl} \n 3) sipProxyServer : ${params.serverAddress?.asStringUriOnly()}\n" +
                                "4) bundleModeEnabled : ${params.isRtpBundleEnabled} \n 5) stunServer : ${natPolicy.stunServer}\n 6) iceEnabled : ${natPolicy.isIceEnabled} \n 7) outbound Proxy : ${params.isOutboundProxyEnabled} \n 8) avpfEnabled : ${account.isAvpfEnabled}"
                    )
                } else {
                    Log.i("$TAG No account found")
                }
            }
        }
    }
}