package mu.location.savmed.ui.chat.viewModel

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.utils.Event
import mu.location.savmed.utils.SavMedUtils
import org.linphone.core.Address
import org.linphone.core.ChatRoom
import org.linphone.core.Factory

abstract class AbstractConversationViewModel: ViewModel() {
    companion object{
        const val TAG = "[Abstract Conversation ViewModel]"
    }

    val chatRoomFoundEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val chatRoomCreatedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    lateinit var chatRoom: ChatRoom
    lateinit var localSipUri: String
    lateinit var remoteSipUri: String

    fun isChatRoomInitialized(): Boolean {
        return  ::chatRoom.isInitialized
    }

    //Skipping Conference Scheduler List

    open fun beforeNotifyingChatRoomFound(sameOne: Boolean) {

    }

    open fun afterNotifyingChatRoomFound(sameOne: Boolean) {

    }

    fun findChatOrCreateRoom(room: ChatRoom?,localSipUri: String, remoteSipUri: String) {
        this.localSipUri = localSipUri
        this.remoteSipUri = remoteSipUri

        Log.i(TAG,"LocalUri : $localSipUri remoteuri --- $remoteSipUri")
     //   Log.i(TAG,"In am chat llll${chatRoom.peerAddress.asStringUriOnly()}")
        coreContext.postOnCoreThread { core ->

            var remoteAddress: Address? = null
            var localAddress: Address? = null

            if (localSipUri == "") {
                localAddress = core.defaultAccount?.params?.identityAddress
            } else {
                if (!localSipUri.contains("@")) {
                    localAddress =
                        Factory.instance().createAddress("sip:${localSipUri}@212.38.94.76")
                } else {
                    localAddress =  Factory.instance().createAddress(localSipUri)
                }
            }


            if (!remoteSipUri.contains("@")) {
                remoteAddress =
                    Factory.instance().createAddress("sip:${remoteSipUri}@212.38.94.76")
            } else {
                remoteAddress =  Factory.instance().createAddress(remoteSipUri)
            }

            Log.i(TAG,"values of Room = ${room} ")
            if (room != null && (!::chatRoom.isInitialized || chatRoom != room)) {
                if (localAddress?.weakEqual(room.localAddress) == true && remoteAddress?.weakEqual(
                        room.peerAddress
                    ) == true
                ) {
                    Log.i(TAG,"Conversation object available in sharedViewModel, using it")
                    chatRoom = room

                    beforeNotifyingChatRoomFound(sameOne = false)
                    chatRoomFoundEvent.postValue(Event(true))
                    afterNotifyingChatRoomFound(sameOne = false)

                    return@postOnCoreThread
                }
            } else if (::chatRoom.isInitialized) {
                Log.i(TAG,"In Chat Room Init")
                beforeNotifyingChatRoomFound(sameOne = false)
                chatRoomFoundEvent.postValue(Event(true))
                afterNotifyingChatRoomFound(sameOne = false)
            }


            val params = coreContext.core.createDefaultChatRoomParams()
            params.backend = ChatRoom.Backend.Basic
            params.isGroupEnabled = false
            params.subject = "One_to_one_chatROom"

            if (localAddress != null && remoteAddress != null) {
                org.linphone.core.tools.Log.i("$TAG Searching for conversation in Core using local ${localAddress.username} ${localAddress.domain} & peer SIP addresses ${remoteAddress.username} ${remoteAddress.domain}")
                val participants = arrayOf(remoteAddress)
                val found = core.searchChatRoom(
                    params,
                    localAddress,null,
                    participants
                )
                if (found != null) {
                    Log.i(TAG, "In foun dnot null oooooooooo")
                    if (::chatRoom.isInitialized && chatRoom == found) {
                        org.linphone.core.tools.Log.i("$TAG Conversation object already in memory, keeping it")
                        beforeNotifyingChatRoomFound(sameOne = true)
                        chatRoomFoundEvent.postValue(Event(true))
                        afterNotifyingChatRoomFound(sameOne = true)
                    } else {
                        chatRoom = found
                        org.linphone.core.tools.Log.i(TAG,"Found conversation in Core, using it")

                        beforeNotifyingChatRoomFound(sameOne = false)
                        chatRoomFoundEvent.postValue(Event(true))
                        afterNotifyingChatRoomFound(sameOne = false)
                    }
                } else {
                    Log.e(TAG,"Failed to find ChatRoom Creating New ChatRoom!")
                    chatRoomFoundEvent.postValue(Event(false))

                    val newChatRoom = core.createChatRoom(
                        params,
                        localAddress,
                        arrayOf(remoteAddress)
                    )

                    if (newChatRoom != null) {
                        chatRoom = newChatRoom
                        val id = SavMedUtils.getChatRoomId(chatRoom)
                        Log.i("Chat Activity","Conversation Successfully Created with id [$id]")
                        chatRoomCreatedEvent.postValue(Event(true))
                        beforeNotifyingChatRoomFound(sameOne = false)
                    } else {
                        Log.e("Chat Activity","Failed to create a chatRoom with [${remoteSipUri}]")
                        chatRoomFoundEvent.postValue(Event(false))
                        chatRoomCreatedEvent.postValue(Event(false))
                    }
                }
            } else {
                Log.e(TAG,"Failed to parse local or remote SIP URI as Address!")
                chatRoomFoundEvent.postValue(Event(false))
                chatRoomCreatedEvent.postValue(Event(false))
            }
        }
    }
}