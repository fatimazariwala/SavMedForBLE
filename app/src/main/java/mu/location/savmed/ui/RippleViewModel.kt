package mu.location.savmed.ui

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.ui.RippleFragment.Companion.TAG
import mu.location.savmed.ui.contacts.models.ContactAvatarModel
import org.linphone.core.Factory

class RippleViewModel: ViewModel() {

    val avatar = MutableLiveData<ContactAvatarModel>()

    fun setAvatar() {
        Log.i(TAG,":yooooooooooooo")

        if (coreContext.isCoreAvailable()) {
            if (coreContext.core.defaultAccount != null) {

                Log.i(
                    TAG,
                    "I ma default account ${coreContext.core.defaultAccount!!.params.identityAddress}"
                )
                coreContext.postOnCoreThread {
                    val model = coreContext.contactsManager.getContactAvatarModelForAddress(
                        coreContext.core.defaultAccount!!.params.identityAddress
                    )
                    avatar.postValue(model)
                }
            } else {
                Log.i(TAG, "Avataar not coming")
            }
        } else {
            Log.i(TAG,"Core Unavailable!!")
        }
    }

    fun logout() {
        coreContext.postOnCoreThread { core ->

            Log.i(TAG, "Accounts --before -${core.defaultAccount}")
            val account = core.defaultAccount
            Log.i(TAG,"Auth info amount ${core.authInfoList}")

            for (info in core.authInfoList) {
                Log.i(TAG,"One info ${info.username}")
            }
            account ?: return@postOnCoreThread
            Log.i(
                TAG,
                "Accounts --$$$-${account.findAuthInfo()?.username}"
            )

            core.removeAccount(account)
            Log.i(TAG,"Remove Account Under ${account.findAuthInfo()?.username}")
            core.clearAccounts()
            core.clearAllAuthInfo()

            if (core.defaultAccount == null) {
                Log.i(
                    TAG,
                    "Default account is null ${coreContext.core.defaultAccount} ${coreContext.core.defaultAccount?.params?.identityAddress?.asStringUriOnly()}"
                )
            } else {
                Log.i(
                    TAG,
                    "Default account is not null ${coreContext.core.defaultAccount} ${coreContext.core.defaultAccount?.params?.identityAddress?.asStringUriOnly()}"
                )
            }
        }
    }
}