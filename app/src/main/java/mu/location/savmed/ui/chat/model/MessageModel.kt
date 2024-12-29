package mu.location.savmed.ui.chat.model

import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.util.Log
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.media.AudioFocusRequestCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.ui.contacts.models.ContactAvatarModel
import mu.location.savmed.utils.AudioUtils
import mu.location.savmed.utils.FileUtils
import mu.location.savmed.utils.PatternClickableSpan
import mu.location.savmed.utils.SavMedUtils
import mu.location.savmed.utils.SpannableClickedListener
import mu.location.savmed.utils.TimestampUtils
import org.linphone.core.ChatMessage
import org.linphone.core.ChatMessageListenerStub
import org.linphone.core.Content
import org.linphone.core.Player
import org.linphone.core.PlayerListener
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

class MessageModel @WorkerThread constructor(
    val chatMessage: ChatMessage,
    val isReply: Boolean,
    val replyTo: String,
    val replyText: String,
    val replyToMessageId: String?,
    val isForward: Boolean,
    isGroupedWithPreviousOne: Boolean,
    isGroupedWithNextOne: Boolean,
    private val onContentClicked: ((fileModel: FileModel) -> Unit)? = null,
    val onWebUrlClicked: ((url: String) -> Unit)? = null,
    val onContactClicked: ((friendRefKey: String) -> Unit)? = null,
    val onToastShow: ((msg: String) -> Unit)? = null
) { // Add on Content Clicked Lambda Function in data class and eventLogModel
    companion object {
        const val TAG = "[Message Model]"

        private const val SIP_URI_REGEXP = "(<?sips?:)[a-zA-Z0-9+_.\\-]+(?:@([a-zA-Z0-9+_.\\-;=~]+))+(>)?"
        private const val HTTP_LINK_REGEXP = "https?://(www\\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_+.~#?&/=]*)"
        private const val MENTION_REGEXP = "@([A-Za-z0-9._-]+)"
    }

    val id = chatMessage.messageId

    var isRead = chatMessage.isRead

    val isOutgoing = chatMessage.isOutgoing

    val isInError = chatMessage.state == ChatMessage.State.NotDelivered

    private lateinit var voiceRecordPath: String

    private var transferringFileModel: FileModel? = null

    val remoteUser = MutableLiveData<String>()

    private var voiceRecordAudioFocusRequest: AudioFocusRequestCompat? = null

    val timestamp = chatMessage.time

    val time = TimestampUtils.toString(timestamp)

    val isPlayingVoiceRecord = MutableLiveData<Boolean>()
    val voiceRecordingDuration = MutableLiveData<Int>()
    val formattedVoiceRecordDuration = MutableLiveData<String>()
    val voiceRecordPlayerPosition = MutableLiveData<Int>()

    val filesList = MutableLiveData<ArrayList<FileModel>>()
    val firstFileModel = MediatorLiveData<FileModel>()

    var allFilesDownloaded: Boolean = false
    val isVoiceRecord = MutableLiveData<Boolean>()

    val avartarModel = MutableLiveData<ContactAvatarModel>()

    val text = MutableLiveData<Spannable>()

    val statusIcon = MutableLiveData<Int>()

    val groupedWithNextMessage = MutableLiveData<Boolean>()

    val groupedWithPreviousMessage = MutableLiveData<Boolean>()

    private lateinit var voiceRecordPlayer: Player

    private val playerListener = PlayerListener {
        Log.i(TAG,"End of file reached")
        stopVoiceRecordPlayer()
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val chatMessageListener = object : ChatMessageListenerStub() {

        @WorkerThread
        override fun onMsgStateChanged(message: ChatMessage, state: ChatMessage.State?) {
            super.onMsgStateChanged(message, state)
            if (state != ChatMessage.State.FileTransferDone && state != ChatMessage.State.FileTransferInProgress) {
                statusIcon.postValue(SavMedUtils.getChatIconResId(chatMessage.state))

                if (state == ChatMessage.State.Displayed) {
                    isRead = chatMessage.isRead
                }
            } else if (state == ChatMessage.State.FileTransferDone) {
                Log.i(TAG, "FileTransFer Done!")
                transferringFileModel?.updateTransferProgress(-1)
                transferringFileModel = null
                if (!allFilesDownloaded) {
                    computeContentList()
                }

                for (content in message.contents) {
                    if(content.isVoiceRecording) {
                        Log.i(TAG,"FIle Transfer done,updating voice record info")
                        computeVoiceRecordContent(content)
                        break
                    }
                }
            }
        }

        override fun onFileTransferProgressIndication(
            message: ChatMessage,
            content: Content,
            offset: Int,
            total: Int
        ) {
            super.onFileTransferProgressIndication(message, content, offset, total)
            val percent = ((offset * 100.0) / total).toInt() // Conversion from int to double and back to int is required

            val model = transferringFileModel
            if (model == null) {
                org.linphone.core.tools.Log.w("$TAG A file is being uploaded/downloaded but no transferringFileModel set!")
                val found = filesList.value.orEmpty().find {
                    it.fileName == content.name
                }
                if (found != null) {
                    transferringFileModel = found
                    org.linphone.core.tools.Log.i("$TAG Found matching FileModel in files list using content name")
                } else {
                    org.linphone.core.tools.Log.w(
                        "$TAG Failed to find a matching FileModel in files list with content name [${content.name}]"
                    )
                }
            }
            model?.updateTransferProgress(percent)
        }
    }

    init {
        updateAvatarModel()

        groupedWithNextMessage.postValue(isGroupedWithNextOne)
        groupedWithPreviousMessage.postValue(isGroupedWithPreviousOne)
        chatMessage.addListener(chatMessageListener)
        statusIcon.postValue(SavMedUtils.getChatIconResId(chatMessage.state))
        computeContentList()

        coreContext.postOnMainThread {
            firstFileModel.addSource(filesList) {
                val first = it.firstOrNull()
                if (first != null) {
                    firstFileModel.value = first!!
                }
            }
        }
    }

    @WorkerThread
    fun updateAvatarModel() {
        val avartar = coreContext.contactsManager.getContactAvatarModelForAddress(
            chatMessage.fromAddress
        )
        avartarModel.postValue(avartar)
    }

    fun destroy() {
        scope.cancel()

        filesList.value.orEmpty().forEach(FileModel::destroy)
        if(::voiceRecordPlayer.isInitialized) {
            stopVoiceRecordPlayer()
            voiceRecordPlayer.removeListener(playerListener)
        }
        chatMessage.removeListener(chatMessageListener)
    }

    fun resend() {
        coreContext.postOnCoreThread {
            Log.i(TAG,"Resending Message!")
            chatMessage.send()
        }
    }

//    fun markAsRead() {
//        coreContext.postOnCoreThread {
//            Log.i(TAG,"Marking ChatMessage with ID [$id] as read")
//            chat.markAsRead()
//        }
//    }

    @WorkerThread
    private fun downloadContent(model: FileModel, content: Content) {
        Log.d(TAG,"Starting downloading content for file [${model.fileName}]")

        if (content.filePath.orEmpty().isEmpty()) {
            val contentName = content.name
            if (contentName != null) {
                val isImage = FileUtils.isExtensionImage(contentName)
                val file = FileUtils.getFileStoragePath(contentName, isImage)
                content.filePath = file.path
                org.linphone.core.tools.Log.i(
                    "$TAG File [$contentName] will be downloaded at [${content.filePath}]"
                )

                model.updateTransferProgress(0)
                transferringFileModel = model
                chatMessage.downloadContent(content)
            } else {
                org.linphone.core.tools.Log.e("$TAG Content name is null, can't download it!")
            }
        }
    }

    private fun computeContentList() {
        Log.i(TAG,"Computing Contents List")
        text.postValue(Spannable.Factory.getInstance().newSpannable(""))
        filesList.postValue(arrayListOf())  //init

        var displayableContentFound = false
        var filesContentCount = 0
        val filesPath = arrayListOf<FileModel>()

        val contents = chatMessage.contents
        allFilesDownloaded = true //init
        for (content in contents) {
            val isFileEncrypted = content.isFileEncrypted

            if (content.isText && !content.isFile) {

                Log.i(TAG,"Text Content...")
                computeTextContent(content,"")
                displayableContentFound = true

            } else if (content.isVoiceRecording) {

                Log.i(TAG,"Voice Content Found ....")
                isVoiceRecord.postValue(true) //init
                computeVoiceRecordContent(content)
                displayableContentFound = true

            } else {
                if (content.isFile) {
                    Log.i(TAG,"File Contant Found...")

                    filesContentCount += 1
                    checkAndRepairFilePathIfNeeded(content)

                    val originalPath = content.filePath.orEmpty()
                    val path = if (isFileEncrypted) {  // Currently we don't have Encryption
                        Log.d(
                            TAG,
                            "[VFS] Content is encrypted, requesting plain file path for file [${content.filePath}]"
                        )
                        content.exportPlainFile()
                    } else {
                        originalPath
                    }
                    val name = content.name ?: ""
                    if (path.isNotEmpty()) {
                        Log.d(
                            TAG,
                            "Found file ready to be displayed [$path] with MIME [${content.type}/${content.subtype}] for message [${chatMessage.messageId}]"
                        )

                        val fileSize = content.fileSize.toLong()
                        val timestamp = chatMessage.time
                        when (content.type) {
                            "image", "video" -> {
                                val fileModel = FileModel(
                                    path,
                                    name,
                                    fileSize,
                                    timestamp,
                                    isFileEncrypted,
                                    originalPath
                                ) { model ->
                                    onContentClicked?.invoke(model)
                                }
                                filesPath.add(fileModel)

                                displayableContentFound = true
                            }
                            else -> {
                                val fileModel = FileModel(
                                    path,
                                    name,
                                    fileSize,
                                    timestamp,
                                    isFileEncrypted,
                                    originalPath
                                ) { model ->
                                    onContentClicked?.invoke(model)
                                }
                                filesPath.add(fileModel)

                                displayableContentFound = true
                            }
                        }
                    } else {
                        Log.e(TAG, "No path found for File Content!")
                    }
                } else if (content.isFileTransfer) {
                    org.linphone.core.tools.Log.d(
                        TAG,"Found file content (not downloaded yet) with type [${content.type}/${content.subtype}] and name [${content.name}]"
                    )
                    allFilesDownloaded = false
                    filesContentCount += 1
                    val name = content.name ?: ""
                    val timestamp = chatMessage.time
                    if (name.isNotEmpty()) {
                        val fileModel = if (isOutgoing && chatMessage.isFileTransferInProgress) {
                            val path = content.filePath.orEmpty()
                            FileModel(
                                path,
                                name,
                                content.fileSize.toLong(),
                                timestamp,
                                isFileEncrypted,
                                path,
                                false
                            ) { model ->
                                onContentClicked?.invoke(model)
                            }
                        } else {
                            FileModel(
                                name,
                                name,
                                content.fileSize.toLong(),
                                timestamp,
                                isFileEncrypted,
                                name,
                                true
                            ) { model ->
                                downloadContent(model, content)
                            }
                        }
                        filesPath.add(fileModel)

                        displayableContentFound = true
                    } else {
                        Log.e(TAG,"No name found for FileTransfer Content!")
                    }
                } else {
                    Log.w(TAG,"Content [${content.name}] is not a File")
                }
            }
        }
        filesList.postValue(filesPath)
        if (!displayableContentFound) {
            val describe = SavMedUtils.getFormattedTextDescribingMessage(chatMessage)
            Log.i(TAG,"No chat content Found")
            text.postValue(describe)
        }
    }

    fun computeTextContent(content: Content, highlight: String) {
        val textContent = content.utf8Text.orEmpty().trim()
        val spannableBuilder = SpannableStringBuilder(textContent)

        val chatRoom = chatMessage.chatRoom
        val matcher = Pattern.compile(MENTION_REGEXP).matcher(textContent)
        while(matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            val source = textContent.subSequence(start + 1,end)
            Log.d(TAG,"Found Mention [$source]")

            val address = if (chatRoom.localAddress.username == source) {
                coreContext.core.accountList.find {
                    it.params.identityAddress?.username == source
                }?.params?.identityAddress
            } else if (chatRoom.peerAddress.username == source) {
                chatRoom.peerAddress
            } else {
                chatRoom.participants.find {
                    it.address.username == source
                }?.address
            }

            val friend = address?.let { coreContext.core.findFriend(it) }

            if (address != null) {
                Log.i(TAG,"ADdress of user nto nulll")
                val displayName = SavMedUtils.getDisplayName(address)
                remoteUser.postValue(displayName)

                Log.i(TAG,"ADdress of user nto ${remoteUser.value} ---")

                spannableBuilder.replace(start, end, "@$displayName")
                val span = PatternClickableSpan.StyledClickableSpan(
                    object :
                        SpannableClickedListener {
                        override fun onSpanClicked(text: String) {
                            val friendRefKey = friend?.refKey ?: ""
                            Log.i(
                                TAG,
                                "Clicked on [$text] span, matching friend ref key is [$friendRefKey]"
                            )
                            if (friendRefKey.isNotEmpty()) {
                                onContactClicked?.invoke(friendRefKey)
                            }
                        }
                    }
                )
                spannableBuilder.setSpan(
                    span,
                    start,
                    start + displayName.length + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        remoteUser.postValue(chatMessage.fromAddress.username)
        text.postValue(
            PatternClickableSpan()
                .add(
                    Pattern.compile(
                        SIP_URI_REGEXP
                    ),
                    object : SpannableClickedListener {

                        override fun onSpanClicked(text: String) {
                            coreContext.postOnCoreThread {
                                Log.i(TAG,"Clicked on SIP URI: $text")
                                val address = coreContext.core.interpretUrl(text,false)
                                if (address != null) {
                                    address.username?.let { coreContext.startCall(it) }
                                } else {
                                    Log.w(TAG,"Failed to arse [$text] as SIP URI")
                                }
                            }
                        }

                    }
                )
                .add(
                    Pattern.compile(
                        HTTP_LINK_REGEXP
                    ),
                    object: SpannableClickedListener {
                        override fun onSpanClicked(text: String) {
                            Log.i(TAG,"Clicked on web URL: $text")
                            onWebUrlClicked?.invoke(text)
                        }
                    }
                )
                .build(spannableBuilder)
        )
    }

    @UiThread
    fun togglePlayPauseVoiceRecord() {
        coreContext.postOnCoreThread {
            if (isPlayingVoiceRecord.value == false) {
                startVoiceRecordPlayer()
            } else {
                pauseVoiceRecordPlayer()
            }
        }
    }

    private fun playTickerFlow() = flow {
        when(isPlayingVoiceRecord.value == true) {
           true-> while(true) {
                emit(Unit)
                delay(10)
            }  // Kotlinx coroutines
            false -> { /**Not Needed**/ }
        }
    }

    @WorkerThread
    private fun isPlayerClosed(): Boolean {
        return !::voiceRecordPlayer.isInitialized || voiceRecordPlayer.state == Player.State.Closed
    }

    private fun startVoiceRecordPlayer() {
        if (voiceRecordAudioFocusRequest == null) {
            voiceRecordAudioFocusRequest = AudioUtils.acquireAudioFocusForVoiceRecordingOrPlayback(
                coreContext.context
            )
        }
        if (isPlayerClosed()) {
            Log.w(TAG,"Players Closed Opning it!")
            initVoiceRecordPlayer()

            if (voiceRecordPlayer.state == Player.State.Closed) {
                Log.e(TAG,"It seems the player fails to open the file, abort playback")
                onToastShow?.invoke(
                    "Player Error!"
                )
                return
            }
        }

        val lowMediaVolume = AudioUtils.isMediaVolumeLow(coreContext.context)
        if (lowMediaVolume) {
            Log.w(TAG,"Voulme is low notifing")
            onToastShow?.invoke(
                "Audio Volume Low!"
            )
        }
        Log.i(TAG,"Playing voice record")
        isPlayingVoiceRecord.postValue(true)
        voiceRecordPlayer.start()

        playTickerFlow().onEach {
            coreContext.postOnCoreThread {
                voiceRecordPlayerPosition.postValue(voiceRecordPlayer.currentPosition)
            }
        }.launchIn(scope)
    }

    @WorkerThread
    private fun initVoiceRecordPlayer() {
        if (!::voiceRecordPath.isInitialized) {
            Log.e(TAG,"No voice record path was set")
            return
        }

        Log.i(TAG,"Creating Player for Voice Record")

        val playbackSoundCard = AudioUtils.getAudioPlayBackDeviceIdForCallRecordingOrVoiceMessage()
        Log.i(TAG,"Using [$playbackSoundCard] as Audio PlayBack Device")

        val localPlayer = coreContext.core.createLocalPlayer(playbackSoundCard,null,null)
        if (localPlayer != null) {
            voiceRecordPlayer = localPlayer
        } else {
            Log.e(TAG,"Couldn't Create Local Player")
            return
        }

        val path = voiceRecordPath
        Log.i(TAG,"Opening Voice record file [$path]")
        if (voiceRecordPlayer.open(path) == 0) {
            val duration = voiceRecordPlayer.duration
            voiceRecordingDuration.postValue(duration)
            val formattedDuration = SimpleDateFormat("mm:ss",Locale.getDefault()).format(duration)
            formattedVoiceRecordDuration.postValue(formattedDuration)
        } else {
            Log.e(TAG,"Player Failed to open file at [$path]")
        }
    } // where is coreContent.isConnectedToAndroidAudio Getting initialized?

    private fun pauseVoiceRecordPlayer() {
        if (!isPlayerClosed()) {
            Log.i(TAG,"Pausing Voice Player Current State ${voiceRecordPlayer.state.name}")
            voiceRecordPlayer.pause()
        }
        val request = voiceRecordAudioFocusRequest
        if (request != null) {
            AudioUtils.releaseAudioFocusForVoiceRecordingOrPlayback(
                coreContext.context,
                request
            )
            voiceRecordAudioFocusRequest = null
        }

        isPlayingVoiceRecord.postValue(false)
    }
    private fun computeVoiceRecordContent(content:Content) {
        voiceRecordPath = content.filePath ?: ""

        val duration = content.fileDuration
        voiceRecordingDuration.postValue(duration)

        val formattedDuration = SimpleDateFormat(
            "mm:ss",
            Locale.getDefault()
        ).format(duration)
        formattedVoiceRecordDuration.postValue(formattedDuration)
        Log.i(TAG,"Found Voice record with path [$voiceRecordPath] and duration [$formattedDuration]")
    }

    private fun stopVoiceRecordPlayer() {
        if(!isPlayerClosed()) {
            Log.i(TAG,"Stopping Voice Record Player")
            voiceRecordPlayer.pause()
            voiceRecordPlayer.seek(0)
            voiceRecordPlayerPosition.postValue(0)
            voiceRecordPlayer.close()
        }

        val request = voiceRecordAudioFocusRequest
        if (request != null) {
            AudioUtils.releaseAudioFocusForVoiceRecordingOrPlayback(
                coreContext.context,
                request
            )
            voiceRecordAudioFocusRequest = null
        }

        isPlayingVoiceRecord.postValue(false)
    }

    @WorkerThread
    private fun checkAndRepairFilePathIfNeeded(content: Content): String {
        val path = content.filePath ?: ""
        if (path.isEmpty()) return ""
        val name = content.name ?: ""
        if (name.isEmpty()) return ""

        val extension = FileUtils.getExtensionFromFileName(path)
        if (extension.contains("/")) {
            org.linphone.core.tools.Log.w(
                "$TAG Weird extension [$extension] found for file [$path], trying with file name [$name]"
            )
            val fileExtension = FileUtils.getExtensionFromFileName(name)
            if (!fileExtension.contains("/")) {
                org.linphone.core.tools.Log.w("$TAG File extension [$fileExtension] seems better, renaming file")
                val newPath = FileUtils.renameFile(path, name)
                if (newPath.isNotEmpty()) {
                    content.filePath = newPath
                    org.linphone.core.tools.Log.w("$TAG File [$path] has been renamed [${content.filePath}]")
                    return newPath
                } else {
                    org.linphone.core.tools.Log.e("$TAG Failed to rename file!")
                }
            }
        }

        return ""
    }

}