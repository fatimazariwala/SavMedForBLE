package mu.location.savmed.ui.contacts.viewModels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import mu.location.savmed.SavMed.Companion.coreContext
import org.linphone.core.Friend

class ContactProfileViewModel: ViewModel() {

    companion object {
        const val TAG = "[Contact Profile ViewModel]"
    }

    val isEmr = MutableLiveData<Boolean>()
    val fullName = MutableLiveData<String>()
    val sipUserName = MutableLiveData<String>()
    val organization = MutableLiveData<String>()
    val jobTitle = MutableLiveData<String>()
    val notes = MutableLiveData<String>()
    val phoneNumber = MutableLiveData<String>()
    val picturePath = MutableLiveData<String>()

    lateinit var refKey: String
    lateinit var friend: Friend

    val contactFoundEvent = MutableLiveData<Boolean>()

    fun findContact(displayedFriend: Friend?, refKey: String) {
        this.refKey = refKey

        coreContext.postOnCoreThread {
            if (displayedFriend != null && (::friend.isInitialized && displayedFriend == friend)) {
                friend = displayedFriend
                resetContactInfo()
                contactFoundEvent.postValue(
                    true
                )
                return@postOnCoreThread
            }

            if (displayedFriend != null && (!::friend.isInitialized || friend != displayedFriend)) {
                if (displayedFriend.refKey == refKey) {
                    friend = displayedFriend
                    resetContactInfo()
                    contactFoundEvent.postValue(
                        true
                    )
                    return@postOnCoreThread
                }
            }

            val found = coreContext.contactsManager.findContactById(refKey)
            if (found != null) {
                resetContactInfo()

                friend = found

                contactFoundEvent.postValue(true)
            } else {
                contactFoundEvent.postValue(false)
            }
        }
    }

    fun resetContactInfo() {

        isEmr.postValue(friend.starred)
        fullName.postValue("${friend.vcard?.givenName ?: friend.name} ${friend.vcard?.familyName ?: ""}")
        sipUserName.postValue(friend.address?.username)

        if (friend.organization != null) {
            organization.postValue(friend.organization)
        }
        if (friend.jobTitle != null) {
            jobTitle.postValue(friend.jobTitle)
        }

        val foundNotes = friend.vcard?.getExtendedPropertiesValuesByName("notes")

        if (foundNotes != null) {
            notes.postValue(foundNotes.toString())
        }

        if (friend.phoneNumbers.size != 0) {
            phoneNumber.postValue(friend.phoneNumbers.firstOrNull())
        }

        if (!friend.photo.isNullOrEmpty()) {
            picturePath.postValue(friend.photo)
        }
    }
}