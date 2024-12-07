package mu.location.savmed.ui.chat

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import mu.location.savmed.R.id
import mu.location.savmed.R.layout
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.ui.call.CallActivity
import mu.location.savmed.ui.chat.model.ChatMapper
import mu.location.savmed.ui.chat.model.MessageListAdapter
import mu.location.savmed.ui.chat.model.downloadClickListener
import org.linphone.core.ChatMessage
import org.linphone.core.ChatMessageListenerStub
import org.linphone.core.ChatRoom
import org.linphone.core.Content
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class ChatTestActivity : AppCompatActivity(),downloadClickListener{

    companion object {
        const val TAG = "[CHatTest ACtivity]"
    }

    val core = coreContext.core

    val chatMapperList = mutableListOf<ChatMapper>()
    lateinit var getRemoteAddress: String
    lateinit var chatRoom: ChatRoom

    private lateinit var mMessageRecycler: RecyclerView
    private lateinit var mMessageAdapter: MessageListAdapter

    lateinit var imageToDownload: ChatMessage

    val REQUEST_IMAGE_PICK = 0

    var remoteUri = MutableLiveData<String>()
    var messageState = MutableLiveData<String>()

    private val coreListener = object: CoreListenerStub() {

        override fun onMessageReceived(core: Core, chatRoom: ChatRoom, message: ChatMessage) {
            // We will be called in this when a message is received
            // If the chat room wasn't existing, it is automatically created by the library
            // If we already sent a chat message, the chatRoom variable will be the same as the one we already have
            Log.i("meaase","received")
            if (this@ChatTestActivity.chatRoom == null) {
                if (chatRoom.hasCapability(ChatRoom.Capabilities.Basic.toInt())) {
                    // Keep the chatRoom object to use it to send messages if it hasn't been created yet
                    this@ChatTestActivity.chatRoom = chatRoom

                    findViewById<TextView>(id.remoteUri).setText(chatRoom.peerAddress.username)
                }
            }

            // We will notify the sender the message has been read by us
            chatRoom.markAsRead()
            coreContext.postOnMainThread { addMessageToHistory(message)  }
        }
    }

    private val chatMessageListener = object: ChatMessageListenerStub() {
        override fun onMsgStateChanged(message: ChatMessage, state: ChatMessage.State?) {

            coreContext.postOnMainThread {

                Log.i("State",state?.name.toString())
                when (state) {
                    ChatMessage.State.InProgress -> {

                        val existingMessage = chatMapperList.indexOfFirst { chatMessage ->
                            Log.i(TAG,"History Message IDs: ${chatMessage.id} Message id: ${message.messageId}")
                            chatMessage.id == message.messageId
                        }
                        if (existingMessage != -1) {
                            chatMapperList[existingMessage].state = state.name
                            Log.i(TAG,"yooo boy ${chatMapperList[existingMessage].state} ")
                        } else {
                            Log.i(TAG,"chat not found ")
                        }
                        mMessageAdapter.notifyDataSetChanged()
                    }
                    ChatMessage.State.Delivered -> {

                        val existingMessage = chatMapperList.indexOfFirst { chatMessage ->
                            chatMessage.id == message.messageId
                        }
                        if (existingMessage != -1) {
                            chatMapperList[existingMessage].state = state.name
                            Log.i(TAG,"yooo boy ${chatMapperList[existingMessage].state} ")
                        } else {
                            Log.i(TAG,"chat not found ")
                        }
                        mMessageAdapter.notifyDataSetChanged()
                    }
                    ChatMessage.State.DeliveredToUser -> {
                        val existingMessage = chatMapperList.indexOfFirst { chatMessage ->
                            chatMessage.id == message.messageId
                        }
                        if (existingMessage != -1) {
                            chatMapperList[existingMessage].state = state.name
                            Log.i(TAG,"yooo boy ${chatMapperList[existingMessage].state} ")
                        } else {
                            Log.i(TAG,"chat not found ")
                        }
                        mMessageAdapter.notifyDataSetChanged()
                    }
                    ChatMessage.State.Displayed -> {
                        val existingMessage = chatMapperList.indexOfFirst { chatMessage ->
                            chatMessage.id == message.messageId
                        }
                        if (existingMessage != -1) {
                            chatMapperList[existingMessage].state = state.name
                            Log.i(TAG,"yooo boy ${chatMapperList[existingMessage].state} ")
                        } else {
                            Log.i(TAG,"chat not found ")
                        }
                        mMessageAdapter.notifyDataSetChanged()
                    }
                    ChatMessage.State.NotDelivered -> {
                        // User might be invalid or not registered
                        val existingMessage = chatMapperList.indexOfFirst { chatMessage ->
                            chatMessage.id == message.messageId
                        }
                        if (existingMessage != -1) {
                            chatMapperList[existingMessage].state = state.name
                            Log.i(TAG,"yooo boy ${chatMapperList[existingMessage].state} ")
                        } else {
                            Log.i(TAG,"chat not found ")
                        }

                        mMessageAdapter.notifyDataSetChanged()
                    }
                    ChatMessage.State.FileTransferDone -> {
                        // We finished uploading/downloading the file
                        messageState.value = "File Transfer Completed"
                        if (!message.isOutgoing) {
//                            binding.messages.removeView(messageView)
                            addMessageToHistory(message)
                        } else {
                            Log.i(TAG,"chat not found ")
                        }
                    }
                    else -> { }
                }

            }
        }
    }

    private fun addMessageToHistory(chatMessage: ChatMessage) {
        // To display a chat message, iterate over it's contents list
        for (content in chatMessage.contents) {
            when {
                content.isText -> {
                    // Content is of type plain/text
                    addTextMessageToHistory(chatMessage, content)
                }
                content.isFile -> {
                    // Content represents a file we received and downloaded or a file we sent
                    // Here we assume it's an image
                    if (content.name?.endsWith(".jpeg") == true ||
                        content.name?.endsWith(".jpg") == true ||
                        content.name?.endsWith(".png") == true) {
                        addImageMessageToHistory(chatMessage, content)
                    }
                }
                content.isFileTransfer -> {
                    // Content represents a received file we didn't download yet
                    addDownloadButtonToHistory(chatMessage, content)
                }
            }
        }
    }

    private fun addTextMessageToHistory(chatMessage: ChatMessage, content: Content) {

        val time = convertUnixTimestampToDateTime(chatMessage.time)
        chatMapperList.add(
            ChatMapper(
                chatMessage.messageId,
                getRemoteAddress,
                chatMessage.isOutgoing,
                null,
                null,
                content,
                time
            )
        )
        Log.i(TAG,"Created Message ID ${chatMessage.messageId}")
        mMessageAdapter.notifyDataSetChanged()
    }

    private fun addDownloadButtonToHistory(chatMessage: ChatMessage, content: Content) {

        imageToDownload = chatMessage
        val time = convertUnixTimestampToDateTime(chatMessage.time)

        chatMapperList.add(
            ChatMapper(
                chatMessage.messageId,
                getRemoteAddress,
                chatMessage.isOutgoing,
                true,
                null,
                content,
                time
            )
        )
        mMessageAdapter.notifyDataSetChanged()

        if (!chatMessage.isOutgoing) {
            chatMessage.addListener(chatMessageListener)
        }
    }

    private fun addImageMessageToHistory(chatMessage: ChatMessage, content: Content) {

        val time = convertUnixTimestampToDateTime(chatMessage.time)

        chatMapperList.add(
            ChatMapper(
                chatMessage.messageId,
                getRemoteAddress,
                chatMessage.isOutgoing,
                false,
                null,
                content,
                time
            )
        )
        Log.i("addImage",content.filePath.toString())
        mMessageAdapter.notifyDataSetChanged()
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(layout.activity_chat_test)

        core.addListener(coreListener)

        getRemoteAddress = intent.getStringExtra("remoteAddress").toString()
        Log.i("remoteadddd",getRemoteAddress)
        createBasicChatRoom()

        mMessageRecycler = findViewById<View>(id.recycler_gchat) as RecyclerView
        mMessageAdapter = MessageListAdapter(this, chatMapperList,this@ChatTestActivity)
        mMessageRecycler.setLayoutManager(LinearLayoutManager(this))
        mMessageRecycler.setAdapter(mMessageAdapter)

        findViewById<TextView>(id.remoteUri).text = getRemoteAddress

        findViewById<Button>(id.button_gchat_send).setOnClickListener() {
            sendMessage()
        }
        findViewById<Button>(id.upload_Image).setOnClickListener() {
            sendImage()
        }

        mMessageRecycler.scrollToPosition(mMessageAdapter.itemCount -1)
    }

    private fun sendImage() {
        // We need a ChatRoom object to send chat messages in it, so let's create it if it hasn't been done yet
        createBasicChatRoom()

        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        "image/*".also { intent.type = it }

        startActivityForResult(intent, REQUEST_IMAGE_PICK)
    }

    @Deprecated("This method has been deprecated in favor of using the Activity Result API\n      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n      contracts for common intents available in\n      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n      testing, and allow receiving results in separate, testable classes independent from your\n      activity. Use\n      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)}\n      with the appropriate {@link ActivityResultContract} and handling the result in the\n      {@link ActivityResultCallback#onActivityResult(Object) callback}.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_PICK && resultCode == Activity.RESULT_OK) {

            val imageUri = data?.data
            val filePath = getRealPathFromUri(imageUri)
            val fileName = filePath?.let { File(it).name }
            val finalFilePath = filesDir.absolutePath + File.separator + fileName

            Log.i("Chat file","filePath = $filePath, fileName = $fileName, finalFilePath = $finalFilePath")

            val content = Factory.instance().createContent()
            // Every content needs a content type & subtype
            content.type = "image"
            content.subtype = "png"
            //content.filePath = filePath

            if (filePath != null) {
                copy(filePath, finalFilePath)
            } else {
                Log.e("Empty File Path","File Path = ${filePath}")
                return
            }

            content.filePath = finalFilePath
            // We need to create a ChatMessage object using the ChatRoom
            val chatMessage = chatRoom!!.createFileTransferMessage(content)

            // Then we can send it, progress will be notified using the onMsgStateChanged callback
            chatMessage.addListener(chatMessageListener)

            // Ensure a file sharing server URL is correctly set in the Core
            coreContext.core.fileTransferServer = "https://www.linphone.org:444/lft.php"

            addMessageToHistory(chatMessage)
            // Send the message
            chatMessage.send()
        }
    }

    private fun getRealPathFromUri(imageUri: Uri?): String? {
        if (imageUri == null) return null

        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val cursor = contentResolver.query(imageUri,projection,null,null,null)
        if (cursor == null) return null

        val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
        cursor.moveToFirst()
        val path = cursor.getString(columnIndex)
        cursor.close()

        return path
    }

    private fun sendMessage() {
        if (chatRoom == null) {
            // We need a ChatRoom object to send chat messages in it, so let's create it if it hasn't been done yet
            createBasicChatRoom()
        }

        val message = findViewById<EditText>(id.edit_gchat_message).text.toString()
        // We need to create a ChatMessage object using the ChatRoom
        val chatMessage = chatRoom.createMessageFromUtf8(message)

        // Then we can send it, progress will be notified using the onMsgStateChanged callback
        chatMessage.addListener(chatMessageListener)

        addMessageToHistory(chatMessage)

        // Send the message
        chatMessage.send()

        // Clear the message input field
        findViewById<EditText>(id.edit_gchat_message).text.clear()
    }

    private fun copy(from: String, to: String) {
        // Used to copy a file from the assets to the app directory
        val sourceFile = File(from)
        val outFile = File(to)
        Log.i("copyyyy","from: ${from},To: ${to}")

        try {
            val inputStream = FileInputStream(sourceFile)
            val outputStream = FileOutputStream(outFile)
            val buffer = ByteArray(1024)
            var length: Int = inputStream.read(buffer)

            while (length > 0) {
                outputStream.write(buffer, 0, length)
                length = inputStream.read(buffer)
            }

            inputStream.close()
            outputStream.flush()
            outputStream.close()
            Log.i("copyyyy", "File copied successfully")
        } catch (e: IOException) {
            Log.e("copyyyy", "Error copying file", e)
        }
    }


    private fun createBasicChatRoom() {

        Log.i("Chat Activity","in create....")
        val account = core.defaultAccount
        if(account == null) {
            Log.e(
                "Chat Activity","No default account found"
            )
            return
        }
        //operationInProgress.postValue(true)

        val params = core.createDefaultChatRoomParams()
        params.backend = ChatRoom.Backend.Basic
        params.isEncryptionEnabled = false
        params.isGroupEnabled = false
        params.subject = "One-to-One CHat room"

        if (params.isValid) {
            val remoteAddress = Factory.instance().createAddress("sip:${getRemoteAddress}@212.38.94.76")

            if (remoteAddress != null) {

                val localAddress = core.defaultAccount?.params?.identityAddress

                val existingChatRoom = core.searchChatRoom(params,localAddress,null, arrayOf(remoteAddress))
                if (existingChatRoom == null) {
                    org.linphone.core.tools.Log.i("No existing ChatRoom Found. Creating a New One")
                    val newChatRoom = core.createChatRoom(params,localAddress, arrayOf(remoteAddress))
                    if (newChatRoom != null) {
                        chatRoom = newChatRoom
//                        val id = SavMedUtils.getChatRoomId(chatRoom)
                        Log.i("Chat Activity","Conversation Successfully Created]")
                    } else {
                        Log.e("Chat Activity","Failed to create a chatRoom with [${getRemoteAddress}]")
                    }
                } else {
                    Log.w("Chat Activity","Conversation with ${getRemoteAddress} found!")
                    chatRoom = existingChatRoom
                    var messageHistory = chatRoom!!.getHistory(0)

                    Log.i("Chat room data","${chatRoom!!.historySize.toString()}\n" )

                    for ( m in messageHistory) {

                        Log.i("Chat Activity","Messages : ${m.messageId}")

                        for (c in m.contents) {

                            val simpleDate = SimpleDateFormat("dd/M/yyyy hh:mm:ss")
                            val currentDate = simpleDate.format(Date(m.time))

                            val chatMapper = ChatMapper(
                                userName = getRemoteAddress,
                                isOutgoing = m.isOutgoing,
                                content = c,
                                time = currentDate
                            )
                            chatMapperList.add(chatMapper)
                            if (c.isFile) {
                                Log.i("Chat Content",c.filePath.toString())
                            } else {
                                Log.i("Chat Content",c.utf8Text.toString())
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        core.removeListener(coreListener)
    }

    override fun downloadImage(message: ChatMapper) {
        message.content?.filePath = "${filesDir.absolutePath}/${message.content?.name}}"
        // Start the download
        message.content?.let { imageToDownload.downloadContent(it) }
    }

    fun convertUnixTimestampToDateTime(unixTimestamp: Long): String {

        val date = Date(unixTimestamp * 1000) // Convert seconds to milliseconds
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return formatter.format(date)
    }

    override fun openImageViewer(context: Context, imageUri: Uri) {
        val intent = Intent()
        intent.setAction(Intent.ACTION_VIEW)
        intent.setDataAndType(imageUri, "image/*")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    }

    init {
        onBackPressedDispatcher.addCallback(this,object: OnBackPressedCallback(true){
            override fun handleOnBackPressed() {
                val i = Intent(this@ChatTestActivity, CallActivity::class.java)
                startActivity(i)
                finish()
            }
        })
    }

}