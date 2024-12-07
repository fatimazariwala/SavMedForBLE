package mu.location.savmed.ui.chat

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.enableEdgeToEdge
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import mu.location.savmed.MainActivity
import mu.location.savmed.R
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.databinding.ActivityCallBinding
import mu.location.savmed.databinding.ActivityChatBinding
import mu.location.savmed.ui.call.CallActivity
import mu.location.savmed.utils.Event
import mu.location.savmed.utils.SavMedUtils
import org.linphone.core.Account
import org.linphone.core.Call
import org.linphone.core.ChatMessage
import org.linphone.core.ChatMessageListenerStub
import org.linphone.core.ChatRoom
import org.linphone.core.Content
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.RegistrationState
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class ChatActivity : AppCompatActivity() {

    lateinit var binding : ActivityChatBinding
    var getRemoteAddress : String? = null

    val core = coreContext.core

    val REQUEST_IMAGE_PICK = 0

    private var chatRoom: ChatRoom? = null

    private val coreListener = object: CoreListenerStub() {

        override fun onMessageReceived(core: Core, chatRoom: ChatRoom, message: ChatMessage) {
            // We will be called in this when a message is received
            // If the chat room wasn't existing, it is automatically created by the library
            // If we already sent a chat message, the chatRoom variable will be the same as the one we already have
            Log.i("meaase","received")
            if (this@ChatActivity.chatRoom == null) {
                if (chatRoom.hasCapability(ChatRoom.Capabilities.Basic.toInt())) {
                    // Keep the chatRoom object to use it to send messages if it hasn't been created yet
                    this@ChatActivity.chatRoom = chatRoom
                    binding.remoteAddress.setText(chatRoom.peerAddress.username)
                }
            }

            // We will notify the sender the message has been read by us
            chatRoom.markAsRead()

            coreContext.postOnMainThread { addMessageToHistory(message) }
        }
    }

    private val chatMessageListener = object: ChatMessageListenerStub() {
        override fun onMsgStateChanged(message: ChatMessage, state: ChatMessage.State?) {

            coreContext.postOnMainThread {

                val messageView = message.userData as? View
                Log.i("State",state?.name.toString())
                when (state) {
                    ChatMessage.State.InProgress -> {
                        messageView?.setBackgroundColor(getColor(R.color.purple_200))
                    }
                    ChatMessage.State.Delivered -> {
                        // The proxy server has ackn
                        //
                        //
                        //
                        //
                        // owledged the message with a 200 OK
                        messageView?.setBackgroundColor(getColor(R.color.purple_main_100))
                    }
                    ChatMessage.State.DeliveredToUser -> {
                        // User as received it
                        messageView?.setBackgroundColor(getColor(R.color.blue))
                    }
                    ChatMessage.State.Displayed -> {
                        // User as read it (client called chatRoom.markAsRead()
                        messageView?.setBackgroundColor(getColor(R.color.blue_info_500))
                    }
                    ChatMessage.State.NotDelivered -> {
                        // User might be invalid or not registered
                        messageView?.setBackgroundColor(getColor(R.color.red_danger_500_night))
                    }
                    ChatMessage.State.FileTransferDone -> {
                        // We finished uploading/downloading the file
                        if (!message.isOutgoing) {
                            binding.messages.removeView(messageView)
                            addMessageToHistory(message)
                        }
                    }
                    else -> { }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        core.addListener(coreListener)

        getRemoteAddress = intent.getStringExtra("remoteAddress")
        binding.remoteAddress.setText(getRemoteAddress)

        if (getRemoteAddress != null) {
            createBasicChatRoom()
        }

        binding.sendMessage.setOnClickListener {
            sendMessage()
        }

        binding.sendImage.setOnClickListener {
            sendImage()
        }
    }

    private fun createBasicChatRoom() {

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
            val remoteAddress = Factory.instance().createAddress("sip:${binding.remoteAddress.text.trim()}@212.38.94.76")

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
                        Log.e("Chat Activity","Failed to create a chatRoom with [${binding.remoteAddress.text.trim()}]")
                    }
                } else {
                    Log.w("Chat Activity","Conversation with ${binding.remoteAddress.text.trim()} found!")
                    chatRoom = existingChatRoom
                    var messageHistory = chatRoom!!.getHistory(0)

                    Log.i("Chat room data","${chatRoom!!.historySize.toString()}\n" )

                    for (m in messageHistory) {

                        Log.i("Chat Activity","Messages : ${m.messageId}")
                        addMessageToHistory(m)
                        for (c in m.contents) {
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

    private fun sendMessage() {
        if (chatRoom == null) {
            // We need a ChatRoom object to send chat messages in it, so let's create it if it hasn't been done yet
            createBasicChatRoom()
        }

        val message = binding.message.text.toString()
        // We need to create a ChatMessage object using the ChatRoom
        val chatMessage = chatRoom!!.createMessageFromUtf8(message)

        // Then we can send it, progress will be notified using the onMsgStateChanged callback
        chatMessage.addListener(chatMessageListener)

        addMessageToHistory(chatMessage)

        // Send the message
        chatMessage.send()

        // Clear the message input field
        binding.message.text.clear()
    }

    private fun sendImage() {
        if (chatRoom == null) {
            // We need a ChatRoom object to send chat messages in it, so let's create it if it hasn't been done yet
            createBasicChatRoom()
        }

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
        val messageView = TextView(this)
        val layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        layoutParams.gravity = if (chatMessage.isOutgoing) Gravity.RIGHT else Gravity.LEFT
        Log.i("IS type",chatMessage.isOutgoing.toString())
        messageView.layoutParams = layoutParams

        // Content is of type plain/text, we can get the text in the content
        messageView.text = content.utf8Text

        if (chatMessage.isOutgoing) {
            messageView.setBackgroundColor(getColor(R.color.white))
        } else {
            messageView.setBackgroundColor(getColor(R.color.purple_200))
        }

        chatMessage.userData = messageView

        findViewById<LinearLayout>(R.id.messages).addView(messageView)
        findViewById<ScrollView>(R.id.scroll).fullScroll(ScrollView.FOCUS_DOWN)
    }

    private fun addDownloadButtonToHistory(chatMessage: ChatMessage, content: Content) {
        val buttonView = Button(this)
        val layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        layoutParams.gravity = if (chatMessage.isOutgoing) Gravity.RIGHT else Gravity.LEFT
        buttonView.layoutParams = layoutParams
        buttonView.text = "Download"

        chatMessage.userData = buttonView
        buttonView.setOnClickListener {
            buttonView.isEnabled = false
            // Set the path to where we want the file to be stored
            // Here we will use the app private storage
            content.filePath = "${filesDir.absolutePath}/$content.name}"

            // Start the download
            chatMessage.downloadContent(content)

            // Download progress will be notified through onMsgStateChanged callback,
            // so we need to add a listener if not done yet
            if (!chatMessage.isOutgoing) {
                chatMessage.addListener(chatMessageListener)
            }
        }

        binding.messages.addView(buttonView)
        binding.scroll.fullScroll(ScrollView.FOCUS_DOWN)
    }

    private fun addImageMessageToHistory(chatMessage: ChatMessage, content: Content) {
        val imageView = ImageView(this)
        val layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        layoutParams.gravity = if (chatMessage.isOutgoing) Gravity.RIGHT else Gravity.LEFT
        imageView.layoutParams = layoutParams

        // As we downloaded the file to the content.filePath, we can now use it to display the image
        imageView.setImageBitmap(BitmapFactory.decodeFile(content.filePath))

        chatMessage.userData = imageView

        binding.messages.addView(imageView)
        binding.scroll.fullScroll(ScrollView.FOCUS_DOWN)
    }

    private fun copy(from: String, to: String) {
        // Used to copy a file from the assets to the app directory
        val sourceFile = File(from)
        val outFile = File(to)
        Log.i("copyyyy","from: ${from},To: ${to}")
//        if (outFile.exists()) {   // Perform a better checking mechanism
//            Log.i("n re","returnnnnnnong")  // these lines were commented die to inability to shar file which were previously shared in the chat session
//            return
//        }

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

//        val outStream = FileOutputStream(outFile)
//        val inFile = assets.open(from)
//        val buffer = ByteArray(1024)
//        var length: Int = inFile.read(buffer)
//
//        while (length > 0) {
//            outStream.write(buffer, 0, length)
//            length = inFile.read(buffer)
//        }
//
//        inFile.close()
//        outStream.flush()
//        outStream.close()
    }

    override fun onDestroy() {
        super.onDestroy()
        core.removeListener(coreListener)
    }

    init {
        onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true){
            override fun handleOnBackPressed() {
                val i = Intent(this@ChatActivity,CallActivity::class.java)
                startActivity(i)
                finish()
            }
        })
    }
}