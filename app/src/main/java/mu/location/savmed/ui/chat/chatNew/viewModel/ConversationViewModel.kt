package mu.location.savmed.ui.chat.chatNew.viewModel

import android.net.Uri
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mu.location.savmed.R
import mu.location.savmed.SavMed
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.ui.chat.chatNew.model.EventLogModel
import mu.location.savmed.ui.chat.chatNew.model.FileModel
import mu.location.savmed.ui.chat.chatNew.model.MessageModel
import mu.location.savmed.ui.contacts.models.ContactAvatarModel
import mu.location.savmed.utils.AppUtils
import mu.location.savmed.utils.Event
import mu.location.savmed.utils.FileUtils
import mu.location.savmed.utils.SavMedUtils
import org.linphone.core.Address
import org.linphone.core.ChatMessage
import org.linphone.core.ChatRoom
import org.linphone.core.ChatRoomListenerStub
import org.linphone.core.EventLog
import org.linphone.core.Friend
import org.linphone.core.tools.Log

class ConversationViewModel @UiThread constructor(): AbstractConversationViewModel() {

    companion object {
        private const val TAG = "[Conversation ViewModel]"
        private const val MESSAGES_PER_PAGE = 30

        const val MAX_TIME_TO_GROUP_MESSAGES = 60 // 1 minute
        const val ITEMS_TO_LOAD_BEFORE_SEARCH_RESULT = 6
    }

    val showToastEvent = MutableLiveData<String>()

    val composingLabel = MutableLiveData<String>()

    val searchInProgress = MutableLiveData<Boolean>()

    val remoteUser = MutableLiveData<String>()

    val showBackButton = MutableLiveData<Boolean>()

    val isCallConversation = MutableLiveData<Boolean>()

    val avatarModel = MutableLiveData<ContactAvatarModel>()

    val remoteRefKey = MutableLiveData<String>()

    val itemToScrollTo = MutableLiveData<Int>()

    val isUserScrollingUp = MutableLiveData<Boolean>()

    val unreadMessagesCount = MutableLiveData<Int>()

    val openWebBrowserEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val contactToDisplayEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val fileToDisplayEvent: MutableLiveData<Event<FileModel>> by lazy {
        MutableLiveData<Event<FileModel>>()
    }


    val messageDeletedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val updateEvents: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val forwardMessageEvent: MutableLiveData<Event<MessageModel>> by lazy {
        MutableLiveData<Event<MessageModel>>()
    }

    var eventsList = arrayListOf<EventLogModel>()

    var pendingForwardMessage: MessageModel? = null

    val isEmpty = MutableLiveData<Boolean>()

    init {
        Log.i(TAG,"Conv Model Started")
        itemToScrollTo.value = -1
        isUserScrollingUp.value = false
    }

    private val chatRoomListener = object: ChatRoomListenerStub() {

        override fun onStateChanged(chatRoom: ChatRoom, newState: ChatRoom.State?) {
            super.onStateChanged(chatRoom, newState)

            Log.i(TAG,"chatRoom state --- ${newState?.name} chatRoom peers: ${chatRoom.peerAddress.username} ${chatRoom.peerAddress.domain} chat Room Local Address: ${chatRoom.localAddress.username} ${chatRoom.localAddress.domain}")
        }

        @WorkerThread
        override fun onChatRoomRead(chatRoom: ChatRoom) {
            super.onChatRoomRead(chatRoom)

            unreadMessagesCount.postValue(0)

            for (eventLog in eventsList.reversed()) {
                if (eventLog.model is MessageModel) {
                    if (!eventLog.model.isRead) {
                        eventLog.model.isRead = true
                    } else {
                        break
                    }
                }
            }
            Log.i("$TAG Conversation was marked as read")
        }

        override fun onChatMessageSending(chatRoom: ChatRoom, eventLog: EventLog) {
            super.onChatMessageSending(chatRoom, eventLog)
            val message = eventLog.chatMessage
            Log.i(TAG,"Message [$message] is being sent, marking conversation as read")
            chatRoom.markAsRead()
            addEvents(arrayOf(eventLog))
        }

        override fun onChatMessageReceived(chatRoom: ChatRoom, eventLog: EventLog) {
            super.onChatMessageReceived(chatRoom, eventLog)
            Log.i(TAG,"sSingle message received Received [${eventLog}] new message(s)")
            computeComposingLabel()
//
            unreadMessagesCount.postValue(chatRoom.unreadMessagesCount)
            addEvents(arrayOf(eventLog))
        }

        override fun onChatMessagesReceived(chatRoom: ChatRoom, eventLogs: Array<EventLog>) {
            super.onChatMessagesReceived(chatRoom, eventLogs)

            Log.i(TAG,"Received -------[${eventLogs.size}] new message(s)")
            computeComposingLabel()

            unreadMessagesCount.postValue(chatRoom.unreadMessagesCount)
            addEvents(eventLogs)
        }

        override fun onIsComposingReceived(
            chatRoom: ChatRoom,
            remoteAddress: Address,
            isComposing: Boolean
        ) {
            super.onIsComposingReceived(chatRoom, remoteAddress, isComposing)
            Log.i(TAG,"${remoteAddress.username} is composing")
            computeComposingLabel()
        }
    }

    @WorkerThread
    private fun computeParticipantsInfo() {
        val friends = arrayListOf<Friend>()
        val address = if (chatRoom.hasCapability(ChatRoom.Capabilities.Basic.toInt())) {
            chatRoom.peerAddress
        } else {
            Log.e(TAG,"ChatRoom Cpapability Not Supported")
            null
        }

        val avatar = if (SavMedUtils.isChatRoomAGroup(chatRoom)) {
            Log.e(TAG,"Group Chat Not Supported")
            null
        } else {
            coreContext.contactsManager.getContactAvatarModelForAddress(address)
        }

        if (avatar != null) {
            avatarModel.postValue(avatar!!)
        } else {
            Log.i(TAG,"Not avartar provided")
        }
    }

    @UiThread
    fun markAsRead() {
        coreContext.postOnCoreThread {
            if (chatRoom.unreadMessagesCount == 0) return@postOnCoreThread
            Log.i("$TAG Marking chat room as read")
            chatRoom.markAsRead()
        }
    }

    fun loadMoreData(totalItemsCount: Int) {
        coreContext.postOnCoreThread {
            val maxSize = chatRoom.historyEventsSize
            Log.i(TAG,"Loading Data Currently Item Count: $totalItemsCount MaxSize of Data: $maxSize")

            if (totalItemsCount < maxSize) {
                var upperBound = totalItemsCount + MESSAGES_PER_PAGE
                if (upperBound > maxSize) {
                    upperBound = maxSize
                }

                val history = chatRoom.getHistoryRangeEvents(totalItemsCount,upperBound)
                val list = getEventsListFromHistory(history)

                val lastItemOfList = list.lastOrNull()
                val newEvent = eventsList.firstOrNull()
                if (lastItemOfList != null && lastItemOfList.model is MessageModel && newEvent != null && newEvent.model is MessageModel && shouldWeGroupTwoEvents(
                        newEvent.eventLog,
                        lastItemOfList.eventLog
                    )
                ) {
                    lastItemOfList.model.groupedWithNextMessage.postValue(true)
                    newEvent.model.groupedWithPreviousMessage.postValue(true)
                }

                Log.i(TAG,"More data loaded, adding it to conversation events list")
                list.addAll(eventsList)
                eventsList = list
                updateEvents.postValue(Event(true))
                isEmpty.postValue(eventsList.isEmpty())
            }
        }
    }

    @UiThread
    fun updateUnreadMessageCount() {
        coreContext.postOnCoreThread {
            unreadMessagesCount.postValue(chatRoom.unreadMessagesCount)
        }
    }

    @UiThread
    fun applyFilter() {
        coreContext.postOnCoreThread {
            computeEvents()
        }
    }

    override fun beforeNotifyingChatRoomFound(sameOne: Boolean) {
        super.beforeNotifyingChatRoomFound(sameOne)
        if (!sameOne) {
            Log.i(TAG,"Conv Found Not Not same as before!")
            chatRoom.addListener(chatRoomListener)
            configureChatRoom()
        } else {
            Log.i(TAG,"In else of conv")
            updateEvents.postValue(Event(true))  // Informed at chatFragment which will update the Adapter
        }
    }

    private fun addEvents(eventLogs: Array<EventLog>) {
        Log.i(TAG, "Adding [${eventLogs.size} events")

        val list = arrayListOf<EventLogModel>()
        list.addAll(eventsList)
        val lastEvent = list.lastOrNull()

        val eventsToAdd = arrayListOf<EventLog>()
        for (event in eventLogs) {
            if (event.chatMessage != null && event.chatMessage?.messageId.orEmpty().isNotEmpty()) {
                val found = list.find {
                    it.model is MessageModel && it.model.chatMessage.messageId == event.chatMessage?.messageId
                }
                if (found == null) {
                    eventsToAdd.add(event)
                } else {
                    Log.i(TAG,"Received Message with ID [${event.chatMessage?.messageId}] is already displayed!")
                }
            } else {
                eventsToAdd.add(event)
            }
        }
        val newList = getEventsListFromHistory(
            eventsToAdd.toTypedArray()
        )
        val newEvent = newList.firstOrNull()

        if(lastEvent != null && lastEvent.model is MessageModel && newEvent != null && newEvent.model is MessageModel && shouldWeGroupTwoEvents(
                newEvent.eventLog,
                lastEvent.eventLog
            )
        ) {
            lastEvent.model.groupedWithNextMessage.postValue(true)
            newEvent.model.groupedWithNextMessage.postValue(true)
        }

        list.addAll(newList)
        eventsList = list
        updateEvents.postValue(Event(true))
        isEmpty.postValue(eventsList.isEmpty())
    }

    private fun computeComposingLabel() {
        val composingFriends = arrayListOf<String>()
        var label = ""
        for (address in chatRoom.composingAddresses) {
            val name = SavMedUtils.getDisplayName(address)
            composingFriends.add(name)
            label += "$name, "
        }
        if (composingFriends.isNotEmpty()) {
            label = label.dropLast(2)

            val format = AppUtils.getStringWithPlural(
                R.plurals.conversation_composing_label,
                composingFriends.size,
                label
            )
            composingLabel.postValue(format)
        } else {
            composingLabel.postValue("")
        }

    }

    private fun configureChatRoom() {
        val friend = coreContext.core.findFriend(chatRoom.peerAddress)
        Log.i(TAG,"chatRoom User ${chatRoom.peerAddress.username} ${chatRoom.peerAddress.domain}")
        if (friend !=null) {
            remoteRefKey.postValue(friend.refKey.toString())

            Log.i(TAG,"I have the refKaye ${friend.refKey} ${remoteRefKey.value}")

            if (friend.vcard?.givenName != null && friend.vcard?.familyName != null) {

                remoteUser.postValue("${friend.vcard?.givenName} ${friend.vcard?.familyName}")

                Log.i(TAG,"In remote user")
            } else {
                remoteUser.postValue(friend.name)
            }
        } else {
            Log.i(TAG,"Friend Not Found")
            remoteUser.postValue(chatRoom.peerAddress.username)
        }
        unreadMessagesCount.postValue(chatRoom.unreadMessagesCount)
        computeEvents()
    }

    private fun computeEvents() {
        eventsList.forEach(EventLogModel::destroy)

        val history = chatRoom.getHistoryEvents(MESSAGES_PER_PAGE)
        for (data in history) {
            Log.i(TAG,"Data in History: ${data.chatMessage?.messageId}")
            for (cont in data.chatMessage?.contents ?: emptyArray()) {
                Log.i("CHAt cont ${cont.utf8Text}")
            }
        }
        val list =  getEventsListFromHistory(history)
        Log.i(TAG,"Extracted [${list.size} events from history")
        eventsList = list
        updateEvents.postValue(Event(true))
        isEmpty.postValue(eventsList.isEmpty())
    }

    private fun processGroupedEvents(
        groupedEventLogs: ArrayList<EventLog>
    ): ArrayList<EventLogModel> {
       // val groupChatRoom = SavMedUtils.isChatRoomAGroup(chatRoom)  [Currently chatRoom is Never Grouped]
        val eventsList = arrayListOf<EventLogModel>()

        var index = 0
        for (groupedEvent in groupedEventLogs) {
            val model = EventLogModel(
                groupedEvent,
                index > 0,
                index != groupedEventLogs.size - 1,
                { fileModel ->
                    fileToDisplayEvent.postValue(Event(fileModel))
                },
                { url ->
                    openWebBrowserEvent.postValue(Event(url))
                },
                { friendRefKey ->
                    contactToDisplayEvent.postValue(Event(friendRefKey))
                },
                { Toast ->
                    showToastEvent.postValue(Toast)
                }
            )
            eventsList.add(model)

            index += 1
        }

        return eventsList
    }

    private fun getEventsListFromHistory(
        history: Array<EventLog>
    ): ArrayList<EventLogModel> {
        val eventsList = arrayListOf<EventLogModel>()
        val groupedEvents = arrayListOf<EventLog>()

        if(history.size == 1) {
            val event = history[0]
            eventsList.addAll(processGroupedEvents(arrayListOf(event)))
        } else {
            for (event in history) {
                if(groupedEvents.isEmpty()) {
                    groupedEvents.add(event)
                    continue
                }

                val prevGroupEvent = groupedEvents.last()
                val isGroupEvents = shouldWeGroupTwoEvents(event,prevGroupEvent)

                if(!isGroupEvents) {
                    eventsList.addAll(processGroupedEvents(groupedEvents))
                    groupedEvents.clear()
                }

                groupedEvents.add(event)
            }

            if(groupedEvents.isNotEmpty()) {
                eventsList.addAll(processGroupedEvents(groupedEvents))
                groupedEvents.clear()
            }
        }

        return eventsList
    }

    private fun shouldWeGroupTwoEvents(event: EventLog,prevEvent: EventLog): Boolean {
        return if (prevEvent.type == EventLog.Type.ConferenceChatMessage && event.type == EventLog.Type.ConferenceChatMessage) {
            val prevEventChatMessage = prevEvent.chatMessage!!
            val eventChatMessage = event.chatMessage!!

            prevEventChatMessage.isOutgoing == eventChatMessage.isOutgoing &&
                    prevEventChatMessage.fromAddress.weakEqual(prevEventChatMessage.fromAddress) &&
                    kotlin.math.abs(eventChatMessage.time - prevEventChatMessage.time) < MAX_TIME_TO_GROUP_MESSAGES
        } else {
            false
        }
    }

    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread {
            if (isChatRoomInitialized()) {
                chatRoom.removeListener(chatRoomListener)
            }
            eventsList.forEach(EventLogModel::destroy)
        }
    }

    @UiThread
    fun updateCurrentlyDisplayedConversation() {
        coreContext.postOnCoreThread {
            if (isChatRoomInitialized()) {
                val id = SavMedUtils.getChatRoomId(chatRoom)
                Log.i(
                    "$TAG Asking notifications manager not to notify messages for conversation [$id]"
                )
                coreContext.notificationManager.setCurrentlyDisplayedChatRoomId(id)

            }
        }
    }


    @UiThread
    fun copyFileToUri(filePath: String, dest: Uri) {
        val source = Uri.parse(FileUtils.getProperFilePath(filePath))
        Log.i("$TAG Copying file URI [$source] to [$dest]")
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val result = FileUtils.copyFile(source, dest)
                if (result) {
                    Log.i(
                        "$TAG File [$filePath] has been successfully exported to documents"
                    )

                } else {
                    Log.e("$TAG Failed to export file [$filePath] to documents!")
                }
            }
        }
    }
}