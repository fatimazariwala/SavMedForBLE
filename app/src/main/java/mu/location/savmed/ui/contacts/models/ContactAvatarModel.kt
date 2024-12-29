package mu.location.savmed.ui.contacts.models

import android.net.Uri
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import mu.location.savmed.R
import mu.location.savmed.contacts.AbstractAvatarModel
import mu.location.savmed.contacts.getNativeContactPictureUri
import mu.location.savmed.utils.AppUtils
import mu.location.savmed.utils.TimestampUtils
import org.linphone.core.Address
import org.linphone.core.ConsolidatedPresence
import org.linphone.core.Friend
import org.linphone.core.FriendListenerStub

class ContactAvatarModel @WorkerThread constructor(val friend: Friend,val address: Address? = null): AbstractAvatarModel() {
    companion object {
        private const val TAG = "[Contact Avatar Model]"
    }

    val id = friend.refKey ?: friend.name

    val contactName = friend.name

    val isStored = friend.inList()

    val isEmrContact = MutableLiveData<Boolean>()

    val lastPresenceInfo = MutableLiveData<String>()

    val name = MutableLiveData<String>()

    val firstLetter: String = AppUtils.getFirstLetter(friend.name.orEmpty())

    private val friendListener = object: FriendListenerStub() {

        @WorkerThread
        override fun onPresenceReceived(friend: Friend) {
            super.onPresenceReceived(friend)
            Log.d(TAG,"Presence Received for [${friend.name}]{ [${friend.consolidatedPresence.name}]")
            computePresence()
        }
    }

    init {
        presenceStatus.postValue(ConsolidatedPresence.Offline)
        isEmrContact.postValue(false)

        if (friend.addresses.isNotEmpty()) {
            friend.addListener(friendListener)
        }

        update(address)
    }

    @WorkerThread
    fun destroy() {
        if (friend.addresses.isNotEmpty()) {
            friend.removeListener(friendListener)
        }
    }

    fun update(address: Address?) {

     //   Log.i(TAG,"In update... ")

        isEmrContact.postValue(friend.starred)
      //  Log.i(TAG,"I ma isEmr ${isEmrContact.value} ${friend.starred}")
        initials.postValue(AppUtils.getInitials(friend.name.orEmpty()))
        showTrust.postValue(true)
        picturePath.postValue(getAvatarUri(friend).toString())

        name.postValue(friend.name)
    //    Log.i(TAG,"in am name ${name.value} ${friend.name}")
        computePresence(address)
    }

    @WorkerThread
    private fun getAvatarUri(friend: Friend): Uri? {
        val picturePath = friend.photo
        if (!picturePath.isNullOrEmpty()) {
            return Uri.parse(picturePath)
        }

        val refKey = friend.refKey
        if (refKey != null) {
            try {
                return friend.getNativeContactPictureUri()
            } catch (e: NumberFormatException) {
                Log.e(TAG,"Exception: $e")
            }
        }

        return null
    }

    @WorkerThread
    private fun computePresence(address: Address?= null) {
        val presence = if (address == null) {
            friend.consolidatedPresence
        } else {
            friend.getPresenceModelForUriOrTel(address.asStringUriOnly())?.consolidatedPresence ?: friend.consolidatedPresence
        }
        Log.d(TAG,"Friend [${friend.name}] presence status is [$presence]")
        presenceStatus.postValue(presence)

        val presenceString = when (presence) {
            ConsolidatedPresence.Online -> {
                "Online"
            }
            ConsolidatedPresence.Busy -> {
                val timestamp = friend.presenceModel?.latestActivityTimestamp ?: -1L
                if (timestamp != -1L) {
                    when {
                        TimestampUtils.isToday(timestamp) -> {
                            val time = TimestampUtils.timeToString(
                                timestamp,
                                timestampInSecs = true
                            )
                            AppUtils.getFormattedString(
                                R.string.contact_presence_status_was_online_today_at,
                                time
                            )
                        }
                        TimestampUtils.isYesterday(timestamp) -> {
                            val time = TimestampUtils.timeToString(
                                timestamp,
                                timestampInSecs = true
                            )
                            AppUtils.getFormattedString(
                                R.string.contact_presence_status_was_online_yesterday_at,
                                time
                            )
                        }
                        else -> {
                            val date = TimestampUtils.toString(
                                timestamp,
                                onlyDate = true,
                                shortDate = false,
                                hideYear = true
                            )
                            AppUtils.getFormattedString(
                                R.string.contact_presence_status_was_online_on,
                                date
                            )
                        }
                    }
                } else {
                    R.string.contact_presence_status_away
                }
            }
            ConsolidatedPresence.DoNotDisturb -> {
                R.string.contact_presence_status_do_not_disturb
            }
            else -> ""
        }
        lastPresenceInfo.postValue(presenceString.toString())
    }

}