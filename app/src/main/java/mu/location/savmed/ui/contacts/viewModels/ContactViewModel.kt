package mu.location.savmed.ui.contacts.viewModels

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.SavMed.Companion.corePreferences
import mu.location.savmed.contacts.ContactsManager
import mu.location.savmed.ui.auth.EmergencyContacts.EmergencyContact
import mu.location.savmed.ui.contacts.fragments.ContactFragment
import mu.location.savmed.ui.contacts.fragments.ContactFragment.Companion
import mu.location.savmed.ui.contacts.models.ContactAvatarModel
import mu.location.savmed.ui.contacts.models.ContactEvent
import mu.location.savmed.ui.contacts.models.ResAddressOrEmailModel
import mu.location.savmed.utils.Event
import mu.location.savmed.utils.FileUtils
import mu.location.savmed.utils.RetrofitInstance
import mu.location.savmed.utils.SharedPreference
import org.linphone.core.Address
import org.linphone.core.Friend
import org.linphone.core.FriendList
import org.linphone.core.FriendList.Status
import org.linphone.core.MagicSearch
import org.linphone.core.MagicSearchListenerStub
import org.linphone.core.SearchResult
import org.linphone.core.SubscribePolicy
import retrofit2.Call
import retrofit2.Callback
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.text.Collator
import java.util.Locale

class ContactViewModel : ViewModel() {

    companion object {
        const val TAG = "[Contact ViewModel]"
        const val SAVMED_ADDRESS_BOOK_FRIEND_LIST = "SavMed Contact List"
        const val TEMP_PICTURE_NAME = "new_contact_temp_picture.jpg"
    }

    lateinit var friend: Friend

    private var previousFilter = "NotSet"
    private var domainFilter = "212.38.94.76"
    val fetchInProgress = MutableLiveData<Boolean>()

    val searchFilter = MutableLiveData<String>()
    val saveChangesEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val firstName = MutableLiveData<String>()
    val lastName = MutableLiveData<String>()
    val fullName = MutableLiveData<String>()
    val organization = MutableLiveData<String>()
    val jobTitle = MutableLiveData<String>()
    val sipUri = MutableLiveData<String>()
    val picturePath = MutableLiveData<String>()
    val refKey = MutableLiveData<String>()
    val isEmr = MutableLiveData<Boolean>()
    val notes = MutableLiveData<String>()
    val phoneNumber = MutableLiveData<String>()
    val email = ArrayList<ResAddressOrEmailModel>()
    val residenceAddress = ArrayList<ResAddressOrEmailModel>()

    private lateinit var magicSearch: MagicSearch

    val isEdit = MutableLiveData<Boolean>()

    private val _contactEvent = MutableSharedFlow<ContactEvent>()
    val contactEvent: SharedFlow<ContactEvent> get() = _contactEvent

    val addNewNumberOrAddressFieldEvent = MutableLiveData<Event<ResAddressOrEmailModel>>()
    val removeNewNumberOrAddressFieldEvent = MutableLiveData<Event<ResAddressOrEmailModel>>()

    val mrList = MutableLiveData<ArrayList<ContactAvatarModel>>()
    val listz = MutableLiveData<ArrayList<ContactAvatarModel>>()

    private val contactsListener = object : ContactsManager.ContactsListener {

        override fun onContactsLoaded() {
            Log.i(TAG,"Contacts Has Been reloaded!")
            magicSearch.resetSearchCache()
            applyFilter(
                "",
                isContactListFilter = true
            )
        }
    }

    private val magicSearchListener = object : MagicSearchListenerStub() {
        @WorkerThread
        override fun onSearchResultsReceived(magicSearch: MagicSearch) {
            Log.i(TAG,"Magic search contacts available")
            processMagicSearch(magicSearch.lastSearch)
        }
    }


    init {
        isEmr.postValue(false)
        picturePath.postValue("")
        corePreferences.showFavoriteContacts = true


        coreContext.postOnCoreThread { core ->
            magicSearch = core.createMagicSearch()
            magicSearch.limitedSearch = false
            magicSearch.addListener((magicSearchListener))
            coreContext.contactsManager.addListener(contactsListener)

            coreContext.postOnMainThread {
                applyFilter(
                    filter = "",
                    isContactListFilter = true
                )
            }
        }
    }

    @UiThread
    fun findFriendByRefKey(refKeyFetched: String?) {
        reset()

        coreContext.postOnCoreThread { core ->
            friend = if (refKeyFetched.isNullOrEmpty()) {
                core.createFriend()
            } else {
                coreContext.contactsManager.findContactById(refKeyFetched) ?: core.createFriend()
            }

            val exists = !friend.refKey.isNullOrEmpty()
            isEdit.postValue(exists)

            if (exists) {
                Log.i(TAG,"Found Friend with refKey [${friend.refKey}]")
                val vCard = friend.vcard
                if (vCard != null) {
                    firstName.postValue(vCard.givenName)
                    lastName.postValue(vCard.familyName)
                    fullName.postValue("${vCard.givenName} ${vCard.familyName}")
                } else {
                    fullName.postValue(friend.name)
                }
                refKey.postValue(friend.refKey ?: friend.vcard?.uid)

                val photo = friend.photo.orEmpty()
                if (photo.isNotEmpty()) {
                    picturePath.postValue(photo)
                }

                sipUri.postValue(friend.address?.username)
                phoneNumber.postValue(friend.phoneNumbers.firstOrNull())
                organization.postValue(friend.organization)
                jobTitle.postValue(friend.jobTitle)

                for (emailAddress in friend.vcard?.getExtendedPropertiesValuesByName("emailAddress")?.toList() ?: emptyList()) {
                    addAddressField(emailAddress, isEmail = true)
                }
                for (resAddress in friend.vcard?.getExtendedPropertiesValuesByName("resAddress")?.toList() ?: emptyList()) {
                    addAddressField(resAddress, isEmail = false)
                }

                viewModelScope.launch {
                    _contactEvent.emit(ContactEvent.ContactEditFound)
                }
            }
        }
    }

    fun addAddressField(address: String = "",requestFiendTobeAdded: Boolean = false,isEmail: Boolean) {
        val newModel = ResAddressOrEmailModel(
            defaultValue = address,
            isEmail = isEmail,
            onValueNoLongerEmpty = {
                if (address.isEmpty()) {
                    addAddressField(requestFiendTobeAdded= true, isEmail = isEmail)
                }
            },
            onRemove = { model ->
                removeModel(model)
            }
        )

        if (isEmail) {
            email.add(newModel)
        } else {
            residenceAddress.add(newModel)
        }

        if (requestFiendTobeAdded) {
            addNewNumberOrAddressFieldEvent.postValue(Event(newModel))
        }
    }

    @UiThread
    private fun removeModel(model: ResAddressOrEmailModel) {
        if (model.isEmail) {
            email.remove(model)
        } else {
            residenceAddress.remove(model)
        }
        removeNewNumberOrAddressFieldEvent.value = Event(model)
    }

    @UiThread
    fun deleteContact(contactModel: ContactAvatarModel) {
        coreContext.postOnCoreThread {
            Log.w(TAG,"Removing Friend [${contactModel.contactName}]")
            coreContext.contactsManager.contactRemoved(contactModel.friend)
            contactModel.friend.remove()
            coreContext.contactsManager.notifyContactsListChanged()

            viewModelScope.launch {
                _contactEvent.emit(ContactEvent.ContactRemoved)
            }
        }
    }

    @WorkerThread
    private fun updateAddresses(addressList: ArrayList<ResAddressOrEmailModel>,isEmail: Boolean) {

        var toKeep = arrayListOf<String>()
        for (address in addressList) {
            val data = address.valueOfField.value.orEmpty().trim()
            if (data.isNotEmpty()) {
                toKeep.add(data)
            }
        }
        val toRemove = arrayListOf<String>()
        val toAdd = arrayListOf<String>()

        val dataFilter = if (isEmail) "emailAddress" else "resAddress"
        val storedList = friend.vcard?.getExtendedPropertiesValuesByName(dataFilter)?.toList() ?: emptyList()

        if (storedList.isNotEmpty()) {
            for (newAddress in toKeep) {
                var found = false
                for (oldAddress in storedList) {
                    if (oldAddress.equals(newAddress)) {
                        found = true
                        break
                    }
                }
                if (!found) {
                    Log.i(
                        TAG,
                        "Address [${newAddress}] doesn't exist yet in friend, adding it"
                    )
                    toAdd.add(newAddress)
                }
            }

            for (oldAddress in storedList) {
                var found = false
                for (newAddress in toKeep) {
                    if (oldAddress.equals(newAddress)) {
                        found = true
                        break
                    }
                }
                if (!found) {
                    Log.i(
                        TAG,
                        "Address [${oldAddress}] no longer exists, removing it"
                    )
                    toRemove.add(oldAddress)
                }
            }
            friend.vcard?.removeExtentedPropertiesByName(dataFilter)
        }
        friend.vcard?.addExtendedProperty(dataFilter,toAdd.toString())

    }

    fun applyFilter(
        filter: String,
        domain: String = "212.38.94.76",
        isContactListFilter: Boolean
    ) {
        Log.i(TAG,"in apply filetr...")

        if (listz.value.orEmpty().isEmpty()) {
            fetchInProgress.postValue(true)
        }

            if ( previousFilter.isNotEmpty() && (
                    previousFilter.length > filter.length ||
                            (previousFilter.length == filter.length && previousFilter != filter)
                    )
            ) {
                magicSearch.resetSearchCache()
            }
            Log.i(TAG,
                "Getting Matching contact for filter [$filter] && domain [$domain]")

            if (isContactListFilter) {
                magicSearch.getContactsListAsync(
                    filter,
                    domain,
                    MagicSearch.Source.Friends.toInt(),
                    MagicSearch.Aggregation.Friend
                )
            } else {
                magicSearch.getContactsListAsync(
                    filter,
                    domain,
                    MagicSearch.Source.All.toInt(),
                    MagicSearch.Aggregation.None
                )
            }

    }

    fun saveChanges() {
        val fn = firstName.value.orEmpty().trim()
        val ln = lastName.value.orEmpty().trim()
        val organizationField = organization.value.orEmpty().trim()
        val sipAddressValue = sipUri.value.orEmpty().trim()
        val isEmr = isEmr.value

        if (fn.isEmpty() && ln.isEmpty() && sipAddressValue.isEmpty()) {
            Log.e(TAG,"One of the Mandatory Field is Empty!")
            viewModelScope.launch {
                _contactEvent.emit(ContactEvent.EmptyField)
            }
            return
        }

        val sipAddress = coreContext.contactsManager.getAddressFromString(sipAddressValue)

        coreContext.postOnCoreThread { core ->
            var status = Status.OK

            if (!::friend.isInitialized) {
                friend = core.createFriend()
            }
            val name = "$fn $ln"

            friend.edit()
            friend.name = name

            val vCard = friend.vcard
            if (vCard != null) {
                vCard.givenName = fn
                vCard.familyName = ln

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
                            FileUtils.copyFile(oldFile,newFile)
                        }
                        val newPicture = FileUtils.getProperFilePath(newFile.absolutePath)
                        Log.i(TAG,"Temporary picture [$picture] copied to [$newPicture]")
                        friend.photo = newPicture
                    } else {
                        friend.photo = FileUtils.getProperFilePath(picture)
                    }
                } else {
                    friend.photo = null
                }

                friend.organization = organizationField
                friend.jobTitle = jobTitle.value.orEmpty().trim()
                friend.address = sipAddress
                friend.vcard?.addExtendedProperty("notes",notes.value.orEmpty().trim())
                friend.addPhoneNumber(phoneNumber.value.orEmpty().trim())

                if (isEmr == true) {
                    friend.starred = true
                    friend.address?.username?.let { createOrDeleteEmrContact(it,true) }
                } else {
                    friend.starred = false
                    friend.address?.username?.let { createOrDeleteEmrContact(it,false) }
                }

                updateAddresses(email,true)
                updateAddresses(residenceAddress,false)

                if (isEdit.value == false) {
                    friend.vcard?.generateUniqueId()
                    friend.refKey = friend.vcard?.uid
                    Log.i(TAG,"RefKey of New Friend [${friend.refKey}]")

                    friend.isSubscribesEnabled = true
                    friend.incSubscribePolicy = SubscribePolicy.SPAccept
                    friend.done()

                    val friendList = core.getFriendListByName(SAVMED_ADDRESS_BOOK_FRIEND_LIST)
                    if (friendList != null) {
                        status = friendList.addFriend(friend)
                        if (status == Status.OK) {
                            Log.i(TAG,"Contact Created Updating Subscriptions")
                            friendList.updateSubscriptions()

                            if (friendList.type == FriendList.Type.CardDAV) {
                                Log.i(TAG,"CardDav Friend Found")
                                friendList.synchronizeFriendsFromServer()
                            }
                        } else {
                            Log.e(TAG,"Failed to add Contact to Friend List")
                        }
                    }
                } else {
                    Log.i(TAG,"Finished applying changes to existing Friend")
                    friend.done()
                }

                coreContext.contactsManager.newContactAdded(friend)
                saveChangesEvent.postValue(
                    Event(if (status == Status.OK) friend.refKey.orEmpty() else "")
                )
            }
        }
    }

    private fun createOrDeleteEmrContact(username: String,isCreate:Boolean) {
        Log.i(TAG,"GOT da userName outside VS $username")
        viewModelScope.launch {
            val call: Call<EmergencyContact?>? = try {

                Log.i(TAG,"GOT da userName $username")
                val emergencyContact = EmergencyContact (
                    userName = SharedPreference.username,
                    emergencyContact = username
                )

                Log.i(TAG,"EMR CONT ${emergencyContact.emergencyContact} ${emergencyContact.userName}")

                if (isCreate == true) {
                    RetrofitInstance.apiEmergencyContacts.postEmergencyContacts(emergencyContact)
                } else {
                    Log.i(TAG,"in delet")
                    RetrofitInstance.apiEmergencyContacts.deleteEmergencyContacts(emergencyContact)
                }
            } catch (e: IOException) {
                Log.i(TAG, "EEROR: ${e.message.toString()}")
                return@launch
            } catch (e: HttpException) {
                Log.i(TAG, "ERROR ${e.message.toString()}")
                return@launch
            }

            call?.enqueue(object: Callback<EmergencyContact?> {
                override fun onResponse(
                    call: Call<EmergencyContact?>,
                    response: Response<EmergencyContact?>
                ) {
                    val res = response.body()
                    Log.i(TAG,"Post Response: $res")
                }
                override fun onFailure(call: Call<EmergencyContact?>, t: Throwable) {
                    Log.i(TAG,"Post Response Failure: ${t.message}")
                    viewModelScope.launch {
                        _contactEvent.emit(
                            ContactEvent.ContactError("starred_error")
                        )
                    }

                }
            })
        }
    }

    fun processMagicSearch(magicSearchResult: Array<SearchResult>) {

        Log.i(TAG,"Processing [${magicSearchResult.size}] results")

        mrList.value.orEmpty().forEach(ContactAvatarModel::destroy)
        listz.value.orEmpty().forEach(ContactAvatarModel::destroy)

        val list = arrayListOf<ContactAvatarModel>()
        val favouritesList = arrayListOf<ContactAvatarModel>()
        var count = 0

        for (result in magicSearchResult) {
            val friend = result.friend
            if (friend != null && friend.refKey.orEmpty().isEmpty()) {
                if (friend.vcard != null) {
                    friend.vcard?.generateUniqueId()
                    friend.refKey = friend.vcard?.uid
                } else {
                    org.linphone.core.tools.Log.w(
                        "$TAG Friend [${friend.name}] found in SearchResults doesn't have a refKey, using name instead"
                    )
                    friend.refKey = friend.name
                }
            }

            val model = if (friend != null) {
                coreContext.contactsManager.getContactAvatarModelForFriend(friend)
            } else {
                coreContext.contactsManager.getContactAvatarModelForAddress(result.address)
            }

            list.add(model)
            count += 1

            val starred = friend?.starred == true
            model.isEmrContact.postValue(starred)

            if (starred) {
                favouritesList.add(model)
            }
//
//            if (count == 20) {
//                listz.postValue(list)
//            }
        }

        val collator = Collator.getInstance(Locale.getDefault())
        favouritesList.sortWith { model1, model2 ->
            collator.compare(model1.friend.name, model2.friend.name)
        }
        list.sortWith { model1, model2 ->
            collator.compare(model1.friend.name, model2.friend.name)
        }

        listz.postValue(list)
        mrList.postValue(favouritesList)

        Log.i(TAG,"Processed [${magicSearchResult.size}] results")
       // firstLoad = false
    }

    @AnyThread
    fun getPictureFileName(): String {
        val name = refKey.value?.replace(" ", "_") ?: "${firstName.value.orEmpty().trim()}_${lastName.value.orEmpty().trim()}"
        return "$name.jpg"
    }

    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread {
            listz.value.orEmpty().forEach(ContactAvatarModel::destroy)
            mrList.value.orEmpty().forEach(ContactAvatarModel::destroy)

            magicSearch.removeListener(magicSearchListener)
            coreContext.contactsManager.removeListener(contactsListener)
        }
    }

    @UiThread
    private fun reset() {
        firstName.value = ""
        lastName.value = ""
        fullName.value = ""
        organization.value = ""
        jobTitle.value = ""
        sipUri.value = ""
        picturePath.value = ""
        refKey.value = ""
        isEmr.value = false
        notes.value = ""
        phoneNumber.value = ""
        email.clear()
        residenceAddress.clear()
    }
}