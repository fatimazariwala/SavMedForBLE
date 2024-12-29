package mu.location.savmed.ui.chat.viewModel

import android.Manifest
import android.content.pm.PackageManager
import android.text.Spannable
import android.util.Log
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media.AudioFocusRequestCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.SavMed.Companion.corePreferences
import mu.location.savmed.ui.chat.model.FileModel
import mu.location.savmed.ui.chat.model.MessageModel
import mu.location.savmed.utils.AudioUtils
import mu.location.savmed.utils.Event
import mu.location.savmed.utils.FileUtils
import mu.location.savmed.utils.SavMedUtils
import org.linphone.core.ChatMessage
import org.linphone.core.ChatRoom
import org.linphone.core.Factory
import org.linphone.core.Player
import org.linphone.core.PlayerListener
import org.linphone.core.Recorder
import java.text.SimpleDateFormat
import java.util.Locale

class SendInMessageViewModel @UiThread constructor(): ViewModel() {

    companion object {
        private const val TAG = "[Send Msg in Conv VM]"
        const val MAX_FILES_TO_ATTACH = 12
    }

    val textToSend = MutableLiveData<String>()

    val isVoiceRecording = MutableLiveData<Boolean>()

    val isVoiceRecordingInProgress = MutableLiveData<Boolean>()

    val voiceRecordingDuration = MutableLiveData<Int>()

    val formattedVoiceRecordingDuration = MutableLiveData<String>()

    val isPlayingVoiceRecord = MutableLiveData<Boolean>()

    val voiceRecordPlayerPosition = MutableLiveData<Int>()

    val isFileTransferServerAvailable = MutableLiveData<Boolean>()

    val isReplying = MutableLiveData<Boolean>()

    val isReplyingTo = MutableLiveData<String>()

    val isReplyingToMessage = MutableLiveData<Spannable>()

    val isFileAttachmentsListOpen = MutableLiveData<Boolean>()

    val maxNumberOfAttachmentsReached = MutableLiveData<Boolean>()

    val isEmojiPickerOpen = MutableLiveData<Boolean>()

    val attachments = MutableLiveData<ArrayList<FileModel>>()

    val isKeyboardOpen = MutableLiveData<Boolean>()

    val askRecordAudioPermissionEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }


    val requestKeyboardHidingEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private val playerListener = PlayerListener {
        Log.i(TAG,"End of file reached")
        stopVoiceRecordPlayer()
    }

    private lateinit var voiceRecordPlayer: Player

    val isCallConversation = MutableLiveData<Boolean>()

    private lateinit var voiceMessageRecorder: Recorder

    private var voiceRecordAudioFocusRequest: AudioFocusRequestCompat? = null

    lateinit var chatRoom: ChatRoom

    private var chatMessageToReplyTo: ChatMessage? = null

    override fun onCleared() {
        super.onCleared()
        coreContext.postOnCoreThread {
            if(::chatRoom.isInitialized) {
                //chatRoom.removeListener(chatRoomListener)
                Log.i(TAG,"ChatRoom Initialized")
            }
        }
    }

    @UiThread
    fun configureChatRoom(room: ChatRoom) {
        chatRoom = room
        coreContext.postOnCoreThread {
           // computeParticipantsList()
        }
    }

//    @WorkerThread
//    private fun computeParticipantsList() {
//        val
//    }

    @UiThread
    fun replyToMessage(model: MessageModel) {
        coreContext.postOnCoreThread {
            val message = model.chatMessage
            Log.i(TAG,"Pending reply to message [${message.messageId}]")
            chatMessageToReplyTo = message
            isReplyingTo.postValue(model.remoteUser.value)
            isReplyingToMessage.postValue(SavMedUtils.getFormattedTextDescribingMessage(message))
            isReplying.postValue(true)
        }
    }

    fun sendMessage() {
        coreContext.postOnCoreThread {
            val messageToReplyTo = chatMessageToReplyTo
            val message = if(messageToReplyTo != null) {
                Log.i(TAG,"Sending message as reply to [${messageToReplyTo.messageId}]")
                chatRoom.createReplyMessage(messageToReplyTo)
            } else {
                chatRoom.createEmptyMessage()
            }

            Log.i(TAG,"Sending message to chat Roo Local: ${chatRoom.localAddress.asStringUriOnly()} peer ${chatRoom.peerAddress.asStringUriOnly()}, Message local: ${message.localAddress.asStringUriOnly()}, peer: ")

            val toSend = textToSend.value.orEmpty().trim()
            if (toSend.isNotEmpty()) {
                message.addUtf8TextContent(toSend)
                // Log.i(TAG,"Sending ----message to chat Roo Local: ${chatRoom.localAddress} peer ${chatRoom.peerAddress}, Message local: ${message.localAddress}, peer: ${message.peerAddress}")
            }

            if (isVoiceRecording.value == true && voiceMessageRecorder.file != null) {
                stopVoiceRecorder()
                val content = voiceMessageRecorder.createContent()
                if (content != null) {
                    Log.i(TAG,"Voice Recording Content Created file name is ${content.name} and duration is ${content.fileDuration}")
                    message.addContent(content)
                } else {
                    Log.e(TAG,"Voice Recording Content couldn't be created!")
                }
            } else {
                for (attachment in attachments.value.orEmpty()) {
                    val content = Factory.instance().createContent()
                    Log.i(TAG,"My mime type is ${attachment.mimeType} ${attachment.fileName} ${attachment.path}")
                    content.type = when (attachment.mimeType) {
                        FileUtils.MimeType.Image -> "image"
                        FileUtils.MimeType.Audio -> "audio"
                        FileUtils.MimeType.Video -> "video"
                        FileUtils.MimeType.Pdf -> "application"
                        FileUtils.MimeType.PlainText -> "text"
                        else -> "file"
                    }
                    content.subtype = if (attachment.mimeType == FileUtils.MimeType.PlainText) {
                        "plain"
                    } else {
                        FileUtils.getExtensionFromFileName(attachment.fileName)
                    }
                    content.name = attachment.fileName
                    content.filePath = attachment.path
                    message.addFileContent(content)
                }
            }

            if (message.contents.isNotEmpty()) {
                Log.i(TAG,"Sending Message")
                message.send()
            }

            Log.i(TAG,"Message sent, re-setting defaults")
            textToSend.postValue("")
            isReplying.postValue(false)
            isFileAttachmentsListOpen.postValue(false)
            isEmojiPickerOpen.postValue(false)

            if(::voiceMessageRecorder.isInitialized) {
                stopVoiceRecorder()
            }
            isVoiceRecording.postValue(false)

            // Warning: do not delete files
            val attachmentsList = arrayListOf<FileModel>()
            attachments.postValue(attachmentsList)

            chatMessageToReplyTo = null
        }

    }

    init {
        coreContext.postOnCoreThread { core ->
            isFileTransferServerAvailable.postValue(!core.fileTransferServer.isNullOrEmpty())
        }

        isEmojiPickerOpen.value = false
        isPlayingVoiceRecord.value = false
        isCallConversation.value = false
        maxNumberOfAttachmentsReached.value = false
    }

    @UiThread
    fun closeFileAttachmentsList() {
        viewModelScope.launch {
            for (file in attachments.value.orEmpty()) {
                file.deleteFile()
            }
        }
        val list = arrayListOf<FileModel>()
        attachments.value = list
        maxNumberOfAttachmentsReached.value = false

        isFileAttachmentsListOpen.value = false
    }

    @UiThread
    fun addAttachment(file: String) {
        Log.i(TAG, "Path sent by picker $file")
        if (attachments.value.orEmpty().size >= MAX_FILES_TO_ATTACH) {
            Log.w(
                TAG,
                "Max number of attachments [$MAX_FILES_TO_ATTACH] reached, file [$file] won't be attached"
            )
            // Add event trigger for toast
            viewModelScope.launch {
                Log.i(TAG,"Deleting temporary file [$file]")
                FileUtils.deleteFile(file)
            }
            return
        }

        val list = arrayListOf<FileModel>()
        list.addAll(attachments.value.orEmpty())

        for (l in list) {
            Log.i(TAG, "For file in list ${l.path} ${l.fileName}")
        }

        val fileName = FileUtils.getNameFromFilePath(file)
        val timestamp = System.currentTimeMillis() / 1000
        Log.i(TAG,"init file model")
        val model = FileModel(file, fileName, 0, timestamp, false, file) { model ->
            removeAttachment(model.path)
        }

        list.add(model)
        attachments.value = list
        maxNumberOfAttachmentsReached.value = list.size >= MAX_FILES_TO_ATTACH

        if (attachments.value != null) {
            for (att in attachments.value!!)
                Log.i(TAG, "Attachments bolbolblkjrlj ${att.path}")
        } else {
            Log.i(TAG, "empty attachments")
        }

        if (list.isNotEmpty()) {
            isFileAttachmentsListOpen.value = true
            Log.i(TAG,"[${list.size}] attachment(s) added ${isFileAttachmentsListOpen.value}")
        } else {
            //isFileAttachmentsListOpen.value = false
            Log.w(TAG, "No attachment to display!")
        }
    }

    @UiThread
    fun removeAttachment(file: String, delete: Boolean = true) {
        val list = arrayListOf<FileModel>()
        list.addAll(attachments.value.orEmpty())
        val found = list.find {
            it.path == file
        }
        if (found != null) {
            if (delete) {
                viewModelScope.launch {
                    found.deleteFile()
                }
            }
            list.remove(found)
        } else {
            Log.w(TAG,"Failed to find file attachment matching [$file]")
        }
        attachments.value = list
        maxNumberOfAttachmentsReached.value = list.size >= MAX_FILES_TO_ATTACH

        if (list.isEmpty()) {
            isFileAttachmentsListOpen.value = false
        }
    }


    @UiThread
    fun startVoiceMessageRecording() {
        if (ActivityCompat.checkSelfPermission(
                coreContext.context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(
                TAG,"Can't start voice message recording, RECORD_AUDIO permission wasn't granted yet"
            )
            askRecordAudioPermissionEvent.postValue(Event(true))
            return
        }

        coreContext.postOnCoreThread {
            requestKeyboardHidingEvent.postValue(Event(true))
            isVoiceRecording.postValue(true)
            initVoiceRecorder()

            isVoiceRecordingInProgress.postValue(true)
            startVoiceRecorder()
        }
    }

    @UiThread
    fun stopVoiceMessageRecording() {
        coreContext.postOnCoreThread {
            stopVoiceRecorder()
        }
    }

    @UiThread
    fun cancelVoiceMessageRecording() {
        coreContext.postOnCoreThread {
            stopVoiceRecorder()

            val path = voiceMessageRecorder.file
            if (path != null) {
                viewModelScope.launch {
                    Log.i(TAG ,"Deleting voice recording file: $path")
                    FileUtils.deleteFile(path)
                }
            }

            isVoiceRecording.postValue(false)
        }
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

    @WorkerThread
    private fun initVoiceRecorder() {
        val core = coreContext.core
        Log.i(TAG,"Creating voice message recorder")
        val recorderParams = core.createRecorderParams()
        recorderParams.fileFormat = Recorder.FileFormat.Mkv

        val recordingAudioDevice = AudioUtils.getAudioRecordingDeviceIdForVoiceMessage()
        recorderParams.audioDevice = recordingAudioDevice
        Log.i(
            TAG," Using device ${recorderParams.audioDevice?.id} to make the voice message recording"
        )

        voiceMessageRecorder = core.createRecorder(recorderParams)
        Log.i(TAG," Voice message recorder created")
    }

    @WorkerThread
    private fun startVoiceRecorder() {
        if (voiceRecordAudioFocusRequest == null) {
            Log.i(TAG," Requesting audio focus for voice message recording")
            voiceRecordAudioFocusRequest = AudioUtils.acquireAudioFocusForVoiceRecordingOrPlayback(
                coreContext.context
            )
        }

        when (voiceMessageRecorder.state) {
            Recorder.State.Running -> Log.w(TAG ,"Recorder is already recording")
            Recorder.State.Paused -> {
                Log.w(TAG ,"Recorder is paused, resuming recording")
                voiceMessageRecorder.start()
            }
            Recorder.State.Closed -> {
                val extension = when (voiceMessageRecorder.params.fileFormat) {
                    Recorder.FileFormat.Mkv -> "mkv"
                    else -> "wav"
                }
                val tempFileName = "voice-recording-${System.currentTimeMillis()}.$extension"
                val file = FileUtils.getFileStoragePath(tempFileName)
                Log.w(
                    TAG ,"G Recorder is closed, starting recording in ${file.absoluteFile}"
                )
                voiceMessageRecorder.open(file.absolutePath)
                voiceMessageRecorder.start()
            }
            else -> {}
        }

        val duration = voiceMessageRecorder.duration
        val formattedDuration = SimpleDateFormat("mm:ss", Locale.getDefault()).format(duration) // duration is in ms
        formattedVoiceRecordingDuration.postValue(formattedDuration)

        val maxVoiceRecordDuration = corePreferences.voiceRecordingMaxDuration
        recorderTickerFlow().onEach {
            coreContext.postOnCoreThread {
                val formattedDuration = SimpleDateFormat("mm:ss", Locale.getDefault()).format(
                    voiceMessageRecorder.duration
                ) // duration is in ms
                formattedVoiceRecordingDuration.postValue(formattedDuration)

                if (duration >= maxVoiceRecordDuration) {
                    Log.w(
                        TAG,"Max duration for voice recording exceeded (${maxVoiceRecordDuration}ms), stopping."
                    )
                    stopVoiceRecorder()
                }
            }
        }.launchIn(viewModelScope)
    }

    @WorkerThread
    private fun stopVoiceRecorder() {
        Log.i(TAG,"In stopppp ${voiceMessageRecorder.state.name} ")
        if (voiceMessageRecorder.state == Recorder.State.Running) {
            Log.i(TAG, "Closing voice recorder")
            voiceMessageRecorder.pause()
            voiceMessageRecorder.close()
        }

        val request = voiceRecordAudioFocusRequest
        if (request != null) {
            Log.i(TAG, "Releasing voice recording audio focus request")
            AudioUtils.releaseAudioFocusForVoiceRecordingOrPlayback(
                coreContext.context,
                request
            )
            voiceRecordAudioFocusRequest = null
        }

        isVoiceRecordingInProgress.postValue(false)
    }

    @WorkerThread
    private fun initVoiceRecordPlayer() {
        Log.i(TAG,"Creating player for voice record")

        val playbackSoundCard = AudioUtils.getAudioPlaybackDeviceIdForCallRecordingOrVoiceMessage()
        Log.i(
            TAG," Using device $playbackSoundCard to make the voice message playback"
        )

        val localPlayer = coreContext.core.createLocalPlayer(playbackSoundCard, null, null)
        if (localPlayer != null) {
            voiceRecordPlayer = localPlayer
        } else {
            Log.e(TAG, "Couldn't create local player!")
            return
        }
        voiceRecordPlayer.addListener(playerListener)
        Log.i(TAG,"Voice record player created")

        val path = voiceMessageRecorder.file
        if (path != null) {
            Log.i(TAG,"Opening voice record file [$path]")
            voiceRecordPlayer.open(path)
            voiceRecordingDuration.postValue(voiceRecordPlayer.duration)
        }
    }

    @WorkerThread
    private fun startVoiceRecordPlayer() {
        if (isPlayerClosed()) {
            Log.w(TAG,"Player closed, let's open it first")
            initVoiceRecordPlayer()
        }

        val context = coreContext.context
        val lowMediaVolume = AudioUtils.isMediaVolumeLow(context)
        if (lowMediaVolume) {
            Log.w(TAG," Media volume is low, notifying user as they may not hear voice message")
        }

        if (voiceRecordAudioFocusRequest == null) {
            voiceRecordAudioFocusRequest = AudioUtils.acquireAudioFocusForVoiceRecordingOrPlayback(
                context
            )
        }

        Log.i(TAG,"Playing voice record")
        voiceRecordPlayer.start()
        isPlayingVoiceRecord.postValue(true)

        playerTickerFlow().onEach {
            coreContext.postOnCoreThread {
                voiceRecordPlayerPosition.postValue(voiceRecordPlayer.currentPosition)
            }
        }.launchIn(viewModelScope)
    }

    @WorkerThread
    private fun pauseVoiceRecordPlayer() {
        if (!isPlayerClosed()) {
            Log.i(TAG," Pausing voice record")
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

    @WorkerThread
    private fun stopVoiceRecordPlayer() {
        if (!isPlayerClosed()) {
            Log.i(TAG, "Stopping voice record")
            voiceRecordPlayer.pause()
            voiceRecordPlayer.seek(0)
            voiceRecordPlayerPosition.postValue(0)
            voiceRecordPlayer.close()
        }

        voiceRecordPlayerPosition.postValue(0)
        isPlayingVoiceRecord.postValue(false)

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
    private fun isPlayerClosed(): Boolean {
        return !::voiceRecordPlayer.isInitialized || voiceRecordPlayer.state == Player.State.Closed
    }

    private fun recorderTickerFlow() = flow {
        while (isVoiceRecordingInProgress.value == true) {
            emit(Unit)
            delay(500)
        }
    }

    private fun playerTickerFlow() = flow {
        while (isPlayingVoiceRecord.value == true) {
            emit(Unit)
            delay(10)
        }
    }

}