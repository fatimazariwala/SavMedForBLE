package mu.location.savmed.ui.contacts.viewModels

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.annotation.AnyThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.ui.contacts.fragments.ContactFragment
import mu.location.savmed.ui.contacts.fragments.ContactFragment.Companion
import mu.location.savmed.ui.contacts.models.ContactAvatarModel
import mu.location.savmed.ui.contacts.models.ContactEvent
import mu.location.savmed.utils.FileUtils
import org.linphone.core.Friend
import org.linphone.core.FriendList
import org.linphone.core.SubscribePolicy

class ContactViewModel : ViewModel() {

    companion object {
        const val TAG = "[Contact ViewModel]"
        const val SAVMED_ADDRESS_BOOK_FRIEND_LIST = "SavMed Contact List"
        const val TEMP_PICTURE_NAME = "new_contact_temp_picture.jpg"
    }

    var friend: Friend ?= null

    val searchFilter = MutableLiveData<String>()

    val firstName = MutableLiveData<String>()
    val lastName = MutableLiveData<String>()
    val fullName = MutableLiveData<String>()
    val organization = MutableLiveData<String>()
    val jobTitle = MutableLiveData<String>()
    val sipUri = MutableLiveData<String>()
    val picturePath = MutableLiveData<String>()
    val refKey = MutableLiveData<String>()
    val isEmr = MutableLiveData<Boolean>()


    var isEdit = false

    private val _contactEvent = MutableSharedFlow<ContactEvent>()
    val contactEvent: SharedFlow<ContactEvent> get() = _contactEvent

    val mrList = MutableLiveData<java.util.ArrayList<ContactAvatarModel>>()
    val listz = MutableLiveData<java.util.ArrayList<ContactAvatarModel>>()

    init {
        isEmr.postValue(false)
        picturePath.postValue("")
    }

    fun addFriendToList() {

        Log.i(TAG,"Vlause of is Edit ${isEdit}")

        if (isEdit == false) {

            friend = null
            val fn = firstName.value.orEmpty().trim()
            val ln = lastName.value.orEmpty().trim()
            val organizationx = organization.value.orEmpty().trim()
            val sipUrix = sipUri.value.orEmpty().trim()
            val isEmrContact = isEmr.value

            Log.i(TAG, "DADA : ${fn} ${firstName.value} ${ln} ${organization} ${sipUri}")

            if (fn.isEmpty() && ln.isEmpty() && organizationx.isEmpty() && sipUrix.isEmpty()) {
                Log.i(TAG, "Mandatory fields Empty!")
                viewModelScope.launch {
                    _contactEvent.emit(ContactEvent.EmptyField)
                }
                return
            }

            val sipAddress = when {
                sipUrix.contains('@') && sipUrix.startsWith("sip") -> {
                    coreContext.core.createAddress("${sipUrix}@212.38.94.76")
                }

                sipUrix.contains('@') && !sipUrix.startsWith("sip") -> {
                    coreContext.core.createAddress("sip:$sipUrix")
                }

                else -> {
                    coreContext.core.createAddress("sip:$sipUrix@212.38.94.76")
                }
            }

            var existing = -1
            if (coreContext.core.getFriendListByName(SAVMED_ADDRESS_BOOK_FRIEND_LIST) != null) {
                existing =
                    coreContext.core.getFriendListByName(SAVMED_ADDRESS_BOOK_FRIEND_LIST)?.friends?.indexOfFirst {
                        it.address?.asStringUriOnly() == sipAddress?.asStringUriOnly()
                    } ?: -1
            }

            if (existing == -1) {
                Log.i(TAG,"Existing is -1 $existing")
                coreContext.postOnCoreThread { core ->

                    if (friend == null) {
                        friend = core.createFriend()
                    }
                    val name = if (fn.isNotEmpty() && ln.isNotEmpty()) {
                        "$fn $ln"
                    } else if (fn.isNotEmpty()) {
                        fn
                    } else if (ln.isNotEmpty()) {
                        ln
                    } else if (organizationx.isNotEmpty()) {
                        organizationx
                    } else {
                        "<Unknown>"
                    }

                    friend?.edit()
                    friend?.name = name

                    val vCard = friend?.vcard
                    if (vCard != null) {
                        vCard.givenName = fn
                        vCard.familyName = ln
                    }

                    if (isEmrContact == true) {
                        friend!!.starred = true
                        Log.i(TAG, "Friend is emrrrrrr")
                    } else {
                        Log.i(TAG, "Friend is Not emrrrrr")
                    }

                    friend?.organization = organizationx
                    friend?.jobTitle = jobTitle.value.orEmpty().trim()

                    friend?.address = sipAddress

                    if (friend?.vcard?.generateUniqueId() == true) {
                        friend?.refKey = friend?.vcard?.uid
                        refKey.postValue(friend?.refKey)
                        Log.i(
                            TAG,
                            "Newly created friend will have generated ref key [${friend?.refKey}]"
                        )
                    } else {
                        Log.e(TAG, "Failed to generate a ref key using vCard's generateUniqueId()")
                    }

                    val picture = picturePath.value.orEmpty()
                    if (picture.isNotEmpty()) {
                        if (picture.contains(TEMP_PICTURE_NAME)) {
                            val newFile = FileUtils.getFileStoragePath(
                                getPictureFileName(),
                                isImage = true,
                                overrideExisting = true
                            )
                            val oldFile = Uri.parse(FileUtils.getProperFilePath(picture))
                            viewModelScope.launch {
                                FileUtils.copyFile(oldFile, newFile)
                            }
                            val newPicture = FileUtils.getProperFilePath(newFile.absolutePath)
                            org.linphone.core.tools.Log.i("$TAG Temporary picture [$picture] copied to [$newPicture]")
                            friend?.photo = newPicture
                        } else {
                            friend?.photo = FileUtils.getProperFilePath(picture)
                        }
                    } else {
                        friend?.photo = null
                    }

                    friend?.isSubscribesEnabled = false
                    // Disable peer to peer short term presence
                    friend?.incSubscribePolicy = SubscribePolicy.SPDeny

                    friend?.done()

                    val fl = core.getFriendListByName(SAVMED_ADDRESS_BOOK_FRIEND_LIST)
                        ?: core.createFriendList()

                    if (fl.displayName.isNullOrEmpty()) {
                        Log.i(
                            TAG,
                            "Locally saved friend list [$SAVMED_ADDRESS_BOOK_FRIEND_LIST] didn't exist yet, let's create it"
                        )
                        fl.isDatabaseStorageEnabled = true
                        fl.displayName = SAVMED_ADDRESS_BOOK_FRIEND_LIST
                        core.addFriendList(fl)
                    }
                    val status = friend?.let { fl.addFriend(it) }

                    if (status?.name == "OK") {
                        viewModelScope.launch {
                            _contactEvent.emit(ContactEvent.ContactCreated)
                        }
                        fl.updateSubscriptions()
                        if (fl.type == FriendList.Type.CardDAV) {
                            Log.i(TAG,
                                "Contact successfully created into CardDAV friend list, synchronizing it"
                            )
                            fl.synchronizeFriendsFromServer()
                        }
                      //  getContactList()
                    } else {
                        viewModelScope.launch {
                            _contactEvent.emit(ContactEvent.ContactError("Create Contact Error: ${status?.name}"))
                        }
                    }
                }
            } else {
//                viewModelScope.launch {
//                    _contactEvent.emit(ContactEvent.ContactError("existing_found"))
//                }
                Log.i(TAG,"Outaaaaaaaaaaa $existing ${sipAddress?.asStringUriOnly()}")

                friend = sipAddress?.let { coreContext.core.findFriend(it) }

                Log.i(TAG,"Y friend refkry ${friend?.refKey}")
                //isEdit = true
                friend?.refKey?.let { displayPreviouslyAddedContact(it,true) }
            }
        } else {
            EditUser()
        }
    }

    fun getContactList() {
        val friendList = coreContext.core.getFriendListByName(
            ContactFragment.SAVMED_ADDRESS_BOOK_FRIEND_LIST
        )?.friends

        val emrList: ArrayList<ContactAvatarModel> = ArrayList()
        val list: ArrayList<ContactAvatarModel> = ArrayList()

        if (friendList != null) {
            for (contact in friendList) {
                if (contact.address != null) {
                    if (contact.starred) {
                        Log.i(
                            ContactFragment.TAG,
                            "In emt list---- ${contact.starred} ${contact.name}"
                        )
                        emrList.add(
                            coreContext.contactsManager.getContactAvatarModelForAddress(
                                contact.address
                            )
                        )
                    } else {
                        Log.i(
                            ContactFragment.TAG,
                            "In on;uuu list---- ${contact.starred} ${contact.name}"
                        )
                        Log.i(ContactFragment.TAG, "in list------")
                        list.add(coreContext.contactsManager.getContactAvatarModelForAddress(contact.address))
                    }
                } else {
                    Log.i(TAG,"Contact Null with ${contact.refKey}")
                }
            }
        } else {
            Log.i(ContactFragment.TAG,"Contact List EMpty")
        }
        mrList.postValue(emrList)
        listz.postValue(list)
    }

    fun displayPreviouslyAddedContact(refKey: String,enableEdit: Boolean) {
        Log.i(TAG,"In displayPrev ${refKey} is Edit $enableEdit")
        val edtFriend = coreContext.core.getFriendListByName(SAVMED_ADDRESS_BOOK_FRIEND_LIST)?.findFriendByRefKey(refKey)
        if (edtFriend != null) {
            Log.i(TAG,"Friend Found!")

            viewModelScope.launch {
                _contactEvent.emit(ContactEvent.ContactEditFound)
            }

            firstName.postValue(edtFriend.vcard?.givenName ?: edtFriend.name)
            lastName.postValue(edtFriend.vcard?.familyName)
            sipUri.postValue(edtFriend.address?.username)
            organization.postValue(edtFriend.organization)
            jobTitle.postValue(edtFriend.jobTitle)

            val photo = edtFriend.photo.orEmpty()

            Log.i(TAG,"Friend Photot ${photo}")
            if (photo.isNotEmpty()) {
                picturePath.postValue(photo)
            }

            fullName.postValue("${edtFriend.vcard?.givenName} ${edtFriend.vcard?.familyName}")
            Log.i(TAG,"Friends Details ${edtFriend.vcard?.givenName} ${lastName.value} ${edtFriend.vcard?.familyName}")

            friend = edtFriend

            isEdit = enableEdit
            Log.i(TAG,"i ma ---edit $isEdit")
         //   edtFriend.remove()
        } else {
            viewModelScope.launch {
                _contactEvent.emit( ContactEvent.ContactNotFound("Editable Contact Not Found"))
            }
        }
    }

    fun EditUser() {
        Log.i(TAG," To Edit-----------------------------------------------------------")
        isEdit = false
        if(friend == null) {
            Log.i(TAG,"Nothing To Edit")
            addFriendToList()
        } else {
            friend!!.remove()
            getContactList()
            addFriendToList()
        }
    }

    fun removeContact(refkey: String) {
        val friend = coreContext.core.getFriendListByName(SAVMED_ADDRESS_BOOK_FRIEND_LIST)?.findFriendByRefKey(refkey)
        if (friend != null) {
            friend.remove()
            viewModelScope.launch {
                _contactEvent.emit(ContactEvent.ContactRemoved)
            }
        } else {
            viewModelScope.launch {
                _contactEvent.emit(ContactEvent.ContactNotFound("Removable Contact Not Found"))
            }
        }
    }

    @AnyThread
    fun getPictureFileName(): String {
        val name = refKey.value?.replace(" ", "_") ?: "${firstName.value.orEmpty().trim()}_${lastName.value.orEmpty().trim()}"
        return "$name.jpg"
    }
}