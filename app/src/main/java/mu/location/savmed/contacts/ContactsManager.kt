package mu.location.savmed.contacts

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.media.Image
import android.net.Uri
import android.provider.ContactsContract
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.app.ActivityCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.models.Users
import mu.location.savmed.models.UsersItem
import mu.location.savmed.ui.auth.EmergencyContacts.EmergencyContact
import mu.location.savmed.ui.auth.EmergencyContacts.EmergencyContactResponse
import mu.location.savmed.ui.contacts.models.ContactAvatarModel
import mu.location.savmed.utils.AppUtils
import mu.location.savmed.utils.ImageUtils
import mu.location.savmed.utils.RetrofitInstance
import mu.location.savmed.utils.SavMedUtils
import mu.location.savmed.utils.SharedPreference
import org.linphone.core.Address
import org.linphone.core.Friend
import org.linphone.core.FriendList
import org.linphone.core.FriendList.Status
import org.linphone.core.SubscribePolicy
//import org.linphone.core.SecurityLevel
import org.linphone.core.tools.Log
import retrofit2.HttpException
import java.io.IOException

class ContactsManager  @UiThread constructor() {

    companion object {
        private const val TAG = "[Contacts Manager]"

        private const val DELAY_BEFORE_RELOADING_CONTACTS_AFTER_PRESENCE_RECEIVED = 1000L // 1 second
        private const val FRIEND_LIST_TEMPORARY_STORED_NATIVE = "TempNativeContacts"
        private const val FRIEND_LIST_TEMPORARY_STORED_REMOTE_DIRECTORY = "TempRemoteDirectoryContacts"
        const val SAVMED_ADDRESS_BOOK_FRIEND_LIST = "SavMed Contact List"
    }

    private var nativeContactsLoaded = false
    private lateinit var usersList: List<UsersItem>

    //private val listeners = arrayListOf<ContactsListener>()

    private val knownContactsAvatarMap = hashMapOf<String,ContactAvatarModel>()
    private val unknownContactsAvatarsMap = hashMapOf<String, ContactAvatarModel>()
    private val conferenceAvatarMap = hashMapOf<String, ContactAvatarModel>()

    private val unknownAndroidContactsMap = arrayListOf<String>()

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reloadContactsJob: Job? = null

    private var loadContactsOnlyFromDefaultDirectory = true
    @WorkerThread
    fun findDisplayName(address: Address): String {
        return getContactAvatarModelForAddress(address).friend.name ?: SavMedUtils.getDisplayName(
            address
        )
    }

    @WorkerThread
    fun getEmergencyContacts() {

        Log.i(TAG,"in gettttttttt")
        coroutineScope.launch {
            try {
                val data = RetrofitInstance.apiEmergencyContacts.getEmergencyContacts(SharedPreference.username)
                if (data.isSuccessful) {
                    val emrContactList = data.body() ?: emptyList()
                    Log.i(TAG,"final dat ${data.body()}")
                    for (cont in emrContactList) {
                        Log.i(TAG, "Found emergency contact: ${cont.emr_contact_name}")
                        coroutineScope.launch {
                            setEmergencyContacts(cont)
                        }
                    }
                } else {
                    Log.e(TAG,"Could not Load Emergency Contact API Failure!!")
                }
            } catch (e : HttpException) {
                Log.i(TAG,e.message().toString())
            } catch (e: IOException) {
                Log.i(TAG,e.message.toString())
            }
        }
    }

    private fun setEmergencyContacts(emrContact: EmergencyContactResponse) {
        val friendList = coreContext.core.getFriendListByName(SAVMED_ADDRESS_BOOK_FRIEND_LIST)
        if (friendList == null) {
            Log.w(TAG,"No contacts To SET")
        } else {
            val friends = friendList.friends
            for (frnd in friends) {
                if (frnd.address?.username == emrContact.emr_contact_name) {
                    frnd.edit()
                    frnd.starred = true
                    frnd.done()
                }
            }
        }
    }

    @WorkerThread
    fun getInstituteContactsFromEndpoint() {

        usersList = listOf()

        coroutineScope.launch{
            val response = try {
                RetrofitInstance.apiContacts.getcallUsers()
            } catch (e: IOException) {
                //Toast.makeText(applicationContext,"app error ${e.message}",Toast.LENGTH_LONG).show()
                Log.i(TAG, "API IO Exception: ${e.message}")
                emptyList<Users>()
                return@launch
            } catch (e: HttpException) {
                // Toast.makeText(applicationContext,"http error ${e.message}",Toast.LENGTH_LONG).show()
                Log.i(TAG, "API HTTP Exception: ${e.message}")
                emptyList<Users>()
                return@launch
            }

            if (response.isSuccessful && response.body() != null) {
                withContext(Dispatchers.Main) {
                    usersList = response.body()!!
                    Log.i(TAG,"Got user last ${usersList.firstOrNull()?.lastName}")

                    var friendList = coreContext.core.getFriendListByName(SAVMED_ADDRESS_BOOK_FRIEND_LIST)
                    if (friendList == null) {
                        val fl = coreContext.core.createFriendList()
                        fl.isDatabaseStorageEnabled = true // We do want to store friends created in app in DB
                        fl.displayName = SAVMED_ADDRESS_BOOK_FRIEND_LIST
                        coreContext.core.addFriendList(fl)
                        friendList = fl
                    }
                    val friendListFriend = friendList.friends

                    for (data in usersList) {
                        Log.i(TAG,"In the userList Lopp ${data.firstName}")

                        Log.i(TAG, "Going in for checking friends...")
                        val existing = friendListFriend.indexOfFirst { friend ->
                            friend.address?.asStringUriOnly() == "sip:${data.firstName}@212.38.94.76"
                        }
                        if (existing == -1) {
                            Log.i(TAG, "Existing not found")
                            // Launch a new coroutine for creating the friend
                            coroutineScope.launch {
                                createFriendForAPI(data.firstName, data.lastName)
                            }
                        } else {
                            Log.i(TAG, "Existing Friend ${data.firstName}")
                            continue
                        }

                    }
                }
            }
        }
    }

    fun createFriendForAPI(fn: String,ln: String) {

        val core = coreContext.core
        val friend = core.createFriend()

        friend.edit()
        friend.name = ln
        friend.address = core.createAddress("sip:${fn}@212.38.94.76")

        friend.vcard?.generateUniqueId()
        friend.refKey = friend.vcard?.uid
        Log.i(TAG,
            "Newly created friend will have generated ref key [${friend.refKey}]"
        )

        friend.isSubscribesEnabled = false
        // Disable peer to peer short term presence
        friend.incSubscribePolicy = SubscribePolicy.SPDeny

        friend.done()

        val friendList = core.getFriendListByName(
            SAVMED_ADDRESS_BOOK_FRIEND_LIST
        )
        val fl = friendList ?: core.createFriendList()
        if (friendList == null) {
            fl.isDatabaseStorageEnabled = true // We do want to store friends created in app in DB
            fl.displayName = SAVMED_ADDRESS_BOOK_FRIEND_LIST
            core.addFriendList(fl)
        }
        var status = fl.addFriend(friend)
        if (status == Status.OK) {
            Log.i("$TAG Contact successfully created, updating subscriptions")
            fl.updateSubscriptions()

            if (fl.type == FriendList.Type.CardDAV) {
                Log.i(
                    "$TAG Contact successfully created into CardDAV friend list, synchronizing it"
                )
                fl.synchronizeFriendsFromServer()
            }
        } else {
            Log.e("$TAG Failed to add contact to friend list [${fl.displayName}]!")
        }
    }

    @WorkerThread
    fun getContactAvatarModelForAddress(address: Address?): ContactAvatarModel {
        if (address == null) {
            Log.w("$TAG Address is null, generic model will be used")
            val fakeFriend = coreContext.core.createFriend()
            return ContactAvatarModel(fakeFriend)
        }

        val clone = address.clone()
        clone.clean()
        val key = clone.asStringUriOnly()

        val foundInMap = getAvatarModelFromCache(key)
        if (foundInMap != null) {
            Log.d("$TAG Avatar model found in map for SIP URI [$key]")
            return foundInMap
        }

        val localAccount = coreContext.core.accountList.find {
            it.params.identityAddress?.weakEqual(clone) == true
        }
        val avatar = if (localAccount != null) {
            Log.d("$TAG [$key] SIP URI matches one of the local account")
            val fakeFriend = coreContext.core.createFriend()
            fakeFriend.address = clone
            fakeFriend.name = SavMedUtils.getDisplayName(localAccount.params.identityAddress)
            fakeFriend.photo = localAccount.params.pictureUri
            val model = ContactAvatarModel(fakeFriend)
//            model.trust.postValue(SecurityLevel.EndToEndEncryptedAndVerified)
            unknownContactsAvatarsMap[key] = model
            model
        } else {
            Log.d("$TAG Looking for friend matching SIP URI [$key]")
            val friend = coreContext.contactsManager.findContactByAddress(clone)
            if (friend != null) {
                Log.d("$TAG Matching friend [${friend.name}] found for SIP URI [$key]")
                val model = ContactAvatarModel(friend, address)
                knownContactsAvatarMap[key] = model
                model
            } else {
                Log.d("$TAG No matching friend found for SIP URI [$key]...")
                val fakeFriend = coreContext.core.createFriend()
                fakeFriend.name = SavMedUtils.getDisplayName(address)
                fakeFriend.address = clone
                val model = ContactAvatarModel(fakeFriend)
                unknownContactsAvatarsMap[key] = model
                model
            }
        }

        return avatar
    }

    @WorkerThread
    fun findContactByAddress(address: Address): Friend? {
        val sipUri = SavMedUtils.getAddressAsCleanStringUriOnly(address)
        Log.d(TAG,"Looking for Friend with SIP URI [$sipUri]")

        val username = address.username
        Log.i(TAG,"Username from Contacts MAnager $username")
        val found = coreContext.core.findFriend(address)
        if (found != null) {
            Log.d(TAG,"Friend [${found.name}] was found using SIP URI [${sipUri}]")
            return found
        }
        val sipAddress = if (sipUri.startsWith("sip:")) {
            sipUri.substring("sip:".length)
        } else if (sipUri.startsWith("sips:")) {
            sipUri.substring("sips:".length)
        } else {
            sipUri
        }

        return if (!username.isNullOrEmpty() && username.startsWith('+')) {
            Log.d(TAG,"Looking for Friend with Phone NUmber [$username]")
            val foundUsingPhoneNumber = coreContext.core.findFriendByPhoneNumber(username)
            if (foundUsingPhoneNumber != null) {
                Log.d(
                    TAG, "Friend [${foundUsingPhoneNumber.name}] was found using phone number [$username]"
                )
                foundUsingPhoneNumber
            } else {
                Log.d(
                    "$TAG Friend wasn't found using phone number [$username], looking in native address book directly"
                )
                findNativeContact(sipAddress, username, true)
            }
        } else {
            Log.d(
                "$TAG Friend wasn't found using SIP address [$sipAddress] and username [$username] isn't a phone number, looking in native address book directly"
            )
            findNativeContact(sipAddress, username.orEmpty(), false)
        }
    }

    @WorkerThread
    fun findNativeContact(address: String,username: String,searchAsPhoneNumber: Boolean): Friend? {
        if (nativeContactsLoaded) {  // Check Where is native cotacts Loaded
            Log.d(
                "$TAG Native contacts already loaded, no need to search further, no native contact matches address [$address]"
            )
            return null
        }
        if (unknownAndroidContactsMap.contains(address)) {
            Log.d(
                "$TAG Address [$address] already looked in Android native contacts and not found, do not do it again"
            )
            return null
        }
        val context = coreContext.context
        val core = coreContext.core
        if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_CONTACTS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
            Log.d(
                "$TAG Looking for native contact with address [$address] ${if (searchAsPhoneNumber) "or phone number [$username]" else ""}"
            )

            val temporaryFriendList = getTemporaryFriendList(native = true)
            try {
                val selection = if (searchAsPhoneNumber) {
                    "${ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER} LIKE ? OR"
                } else {
                    "${ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS} LIKE ? OR ${ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS} LIKE ? OR ${ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS} LIKE ?"
                }

                Log.i(TAG, "In am WHICH part of query [$selection]")

                val selectionParams = if (searchAsPhoneNumber) {
                    arrayOf(username, address, "sip:$address", username)
                } else {
                    arrayOf(address, "sip:$address", username)
                }

                Log.i(TAG, "In am WHICH part PARAMETERS of query [$selectionParams]")

                val cursor: Cursor? = context.contentResolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    arrayOf(
                        ContactsContract.Data.CONTACT_ID,
                        ContactsContract.Contacts.LOOKUP_KEY,
                        ContactsContract.Data.DISPLAY_NAME_PRIMARY
                    ),
                    selection,
                    selectionParams,
                    null
                )

                if (cursor != null && cursor.moveToNext()) {
                    val friend = coreContext.core.createFriend()
                    friend.edit()

                    val parsedAddress = core.interpretUrl(address, false)
                    if (parsedAddress != null) {
                        friend.address = parsedAddress
                    } else {
                        Log.e("$TAG Failed to parse [$address] as Address!")
                    }

                    do {
                        val id: String =
                            cursor.getString(
                                cursor.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
                            )
                        friend.refKey = id

                        if (friend.name.isNullOrEmpty()) {
                            val displayName: String? =
                                cursor.getString(
                                    cursor.getColumnIndexOrThrow(
                                        ContactsContract.Data.DISPLAY_NAME_PRIMARY
                                    )
                                )
                            friend.name = displayName
                        }

                        if (friend.photo.isNullOrEmpty()) {
                            val uri = friend.getNativeContactPictureUri()
                            if (uri != null) {
                                friend.photo = uri.toString()
                            }
                        }

                        if (friend.nativeUri.isNullOrEmpty()) {
                            val lookupKey =
                                cursor.getString(
                                    cursor.getColumnIndexOrThrow(
                                        ContactsContract.Contacts.LOOKUP_KEY
                                    )
                                )
                            friend.nativeUri =
                                "${ContactsContract.Contacts.CONTENT_LOOKUP_URI}/$lookupKey"
                        }
                    } while (cursor.moveToNext())

                    friend.done()
                    temporaryFriendList.addLocalFriend(friend)

                    Log.d("$TAG Found native contact [${friend.name}] with address [$address]")
                    cursor.close()
                    return friend
                }

                Log.w("$TAG Failed to find native contact with address [$address]")
                unknownAndroidContactsMap.add(address)
                return null
            } catch (e: IllegalArgumentException) {
                Log.e("$TAG Failed to search for native contact with address [$address]: $e")
            }
        } else {
            Log.w("$TAG READ_CONTACTS permission not granted, can't check native address book")
        }
        return null
    }

    @WorkerThread
    fun getTemporaryFriendList(native: Boolean): FriendList {
        val core = coreContext.core
        val name = if (native) FRIEND_LIST_TEMPORARY_STORED_NATIVE else FRIEND_LIST_TEMPORARY_STORED_REMOTE_DIRECTORY
        val temporaryFriendList = core.getFriendListByName(name) ?: core.createFriendList()
        if (temporaryFriendList.displayName.isNullOrEmpty()) {
            temporaryFriendList.isDatabaseStorageEnabled = false
            temporaryFriendList.displayName = name
            core.addFriendList(temporaryFriendList)
            Log.i(
                "$TAG Created temporary friend list with name [$name]"
            )
        }
        return temporaryFriendList
    }

    @WorkerThread
    fun getMePerson(localAddress: Address): Person {
        val account = coreContext.core.accountList.find {
            it.params.identityAddress?.weakEqual(localAddress) == true
        }
        val name = account?.params?.identityAddress?.displayName ?: SavMedUtils.getDisplayName(localAddress)
        val personBuilder = Person.Builder().setName(name)

        val photo = account?.params?.pictureUri.orEmpty()
        val bm = ImageUtils.getBitmap(coreContext.context,photo)
        personBuilder.setIcon(
            if (bm == null) {
                AvatarGenerator(coreContext.context).setInitials(AppUtils.getInitials(name)).buildIcon()
            } else {
                IconCompat.createWithAdaptiveBitmap(bm)
            }
        )

        val identity = account?.params?.identityAddress?.asStringUriOnly() ?: localAddress.asStringUriOnly()
        personBuilder.setKey(identity)
        personBuilder.setImportant(true)
        return personBuilder.build()
    }

    @WorkerThread
    private fun getAvatarModelFromCache(key: String): ContactAvatarModel? {
        return knownContactsAvatarMap[key] ?: unknownContactsAvatarsMap[key]
    }

}

@WorkerThread
fun Friend.getNativeContactPictureUri(): Uri? {
    val contactId = refKey
    if (contactId != null) {
        try {
            val lookupUri = ContentUris.withAppendedId(
                ContactsContract.Contacts.CONTENT_URI,
                contactId.toLong()
            )

            val pictureUri = Uri.withAppendedPath(
                lookupUri,
                ContactsContract.Contacts.Photo.DISPLAY_PHOTO
            )
            // Check that the URI points to a real file
            val contentResolver = coreContext.context.contentResolver
            try {
                val fd = contentResolver.openAssetFileDescriptor(pictureUri, "r")
                if (fd != null) {
                    fd.close()
                    return pictureUri
                }
            } catch (e: Exception) {
                Log.e("[Contacts Manager] Can't open [$pictureUri] for contact [$name]: $e")
            }

            // Fallback to thumbnail
            return Uri.withAppendedPath(
                lookupUri,
                ContactsContract.Contacts.Photo.CONTENT_DIRECTORY
            )
        } catch (numberFormatException: NumberFormatException) {
            // Expected for contacts created by Linphone
        }
    }
    return null
}

@WorkerThread
fun Friend.getPerson(): Person {
    val personBuilder = Person.Builder().setName(name)

    val bm: Bitmap? = getAvatarBitmap()
    personBuilder.setIcon(
        if (bm == null) {
            Log.i("[Friend Extension]","Can't use friend [$name] picture path, generating avatar based on initials")
            AvatarGenerator(coreContext.context).setInitials(AppUtils.getInitials(name.orEmpty())).buildIcon()
        } else {
            IconCompat.createWithAdaptiveBitmap(bm)
        }
    )
    personBuilder.setKey(refKey)
    personBuilder.setUri(nativeUri)
    personBuilder.setImportant(true)
    return personBuilder.build()
}

@WorkerThread
fun Friend.getAvatarBitmap(round: Boolean = false): Bitmap? {
    try {
        return ImageUtils.getBitmap(
            coreContext.context,
            photo ?: getNativeContactPictureUri()?.toString(),
            round
        )
    } catch (e: NumberFormatException) {
        Log.i("[Friend Extension]","Error Getting Avatar BitMap: $e")
    }
    return null
}