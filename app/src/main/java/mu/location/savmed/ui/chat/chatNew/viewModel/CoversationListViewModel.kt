package mu.location.savmed.ui.chat.chatNew.viewModel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.ui.chat.chatNew.model.ConversationModel
import mu.location.savmed.utils.SavMedUtils
import org.linphone.core.ChatMessage
import org.linphone.core.ChatRoom
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.tools.Log

class CoversationListViewModel @UiThread constructor(): ViewModel() {
    companion object {
        private const val TAG = "[Conversations List ViewModel]"
    }

    val conversations = MutableLiveData<ArrayList<ConversationModel>>()

    val fetchInProgress = MutableLiveData<Boolean>()

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onChatRoomStateChanged(
            core: Core,
            chatRoom: ChatRoom,
            state: ChatRoom.State?
        ) {
            Log.i(
                "$TAG Conversation [${SavMedUtils.getChatRoomId(chatRoom)}] state changed [$state]"
            )

            when (state) {
                ChatRoom.State.Created -> addChatRoom(chatRoom)
                ChatRoom.State.Deleted -> removeChatRoom(chatRoom)
                else -> {}
            }
        }

        @WorkerThread
        override fun onMessageSent(core: Core, chatRoom: ChatRoom, message: ChatMessage) {
            reorderChatRooms()
        }

        @WorkerThread
        override fun onMessagesReceived(
            core: Core,
            chatRoom: ChatRoom,
            messages: Array<out ChatMessage>
        ) {
            reorderChatRooms()
        }
    }

    init {
        fetchInProgress.value = true

        coreContext.postOnCoreThread { core ->
            core.addListener(coreListener)

            computeChatRoomsList("")
        }
    }

    @UiThread
    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread { core ->
            conversations.value.orEmpty().forEach(ConversationModel::destroy)
            core.removeListener(coreListener)
        }
    }

    @UiThread
    fun filter() {
        coreContext.postOnCoreThread {
            computeChatRoomsList("")
        }
    }

    @WorkerThread
    private fun computeChatRoomsList(filter: String) {
        conversations.value.orEmpty().forEach(ConversationModel::destroy)

        if (conversations.value.orEmpty().isEmpty()) {
            fetchInProgress.postValue(true)
        }

        val list = arrayListOf<ConversationModel>()
        var count = 0

        val account = SavMedUtils.getDefaultAccount()
        val chatRooms = account?.chatRooms
        for (chatRoom in chatRooms.orEmpty()) {
            val model = ConversationModel(chatRoom)
            list.add(model)
            count += 1

            if (count == 15) {
                conversations.postValue(list)
            }
        }

        conversations.postValue(list)
    }

    @WorkerThread
    private fun addChatRoom(chatRoom: ChatRoom) {
        val defaultAccount = SavMedUtils.getDefaultAccount()
        if (defaultAccount == null || defaultAccount.params.identityAddress?.weakEqual(
                chatRoom.localAddress
            ) == false
        ) {
            Log.w(
                "$TAG A chat room was created but not displaying it because it doesn't belong to currently default account"
            )
            return
        }

        val currentList = conversations.value.orEmpty()
        val peerAddress = chatRoom.peerAddress
        val found = currentList.find {
            it.chatRoom.peerAddress.weakEqual(peerAddress)
        }
        if (found != null) {
            Log.w("$TAG Created chat room is already in the list, skipping")
            return
        }

        val newList = arrayListOf<ConversationModel>()
        val model = ConversationModel(chatRoom)
        newList.add(model)
        newList.addAll(currentList)
        Log.i("$TAG Adding chat room to list")
        conversations.postValue(newList)
    }

    @WorkerThread
    private fun removeChatRoom(chatRoom: ChatRoom) {
        val currentList = conversations.value.orEmpty()
        val peerAddress = chatRoom.peerAddress
        val found = currentList.find {
            it.chatRoom.peerAddress.weakEqual(peerAddress)
        }
        if (found != null) {
            val newList = arrayListOf<ConversationModel>()
            newList.addAll(currentList)
            newList.remove(found)
            found.destroy()
            Log.i("$TAG Removing chat room [${peerAddress.asStringUriOnly()}] from list")
            conversations.postValue(newList)
        } else {
            Log.w(
                "$TAG Failed to find item in list matching deleted chat room peer address [${peerAddress.asStringUriOnly()}]"
            )
        }
    }

    @WorkerThread
    private fun reorderChatRooms() {
        Log.i("$TAG Re-ordering conversations")
        val sortedList = arrayListOf<ConversationModel>()
        sortedList.addAll(conversations.value.orEmpty())
        sortedList.sortByDescending {
            it.chatRoom.lastUpdateTime
        }
        conversations.postValue(sortedList)
    }
}