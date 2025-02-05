package mu.location.savmed.ui.chat

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.UiThread
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mu.location.savmed.BuildConfig
import mu.location.savmed.R
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.databinding.FragmentConversationBinding
import mu.location.savmed.ui.chat.Adapters.ConversationEventAdapter
import mu.location.savmed.ui.chat.model.FileModel
import mu.location.savmed.ui.chat.model.MessageModel
import mu.location.savmed.ui.chat.view.RichEditText
import mu.location.savmed.ui.chat.viewModel.ConversationViewModel
import mu.location.savmed.ui.chat.viewModel.SendInMessageViewModel
import mu.location.savmed.ui.main.SharedMainViewModel
import mu.location.savmed.utils.Event
import mu.location.savmed.utils.FileUtils
import mu.location.savmed.utils.TimestampUtils
import org.linphone.core.tools.Log
import java.io.File
import java.util.Objects


class ConversationFragment : Fragment() {

    companion object {
        const val TAG = "[Conv Fragment]"
        private const val EXPORT_FILE_AS_DOCUMENT = 10
    }

    lateinit var binding: FragmentConversationBinding

    protected lateinit var convViewModel: ConversationViewModel
    protected lateinit var sendInMessageViewModel: SendInMessageViewModel
    private lateinit var adapter: ConversationEventAdapter

    private lateinit var scrollListener: ConversationScrollListener

    private lateinit var localeSipUri: String
    private lateinit var remoteSipUri: String

    var refKey = ""

    private lateinit var sharedMainViewModel: SharedMainViewModel

    private val args: ConversationFragmentArgs by navArgs()

    private var filePathToExport: String? = null

    private val pickMedia = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(
            maxItems = 12
        )
    ) { list ->
        if (list.isNotEmpty()) {
            for (uri in list) {
                Log.i(TAG,"Back from picker ${uri.path}")
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        val path = FileUtils.getFilePath(requireContext(), uri, false)
                        Log.i(TAG,"Picked file [$uri] matching path is [$path]")
                        if (path != null) {
                            withContext(Dispatchers.Main) {
                                sendInMessageViewModel.addAttachment(path)
                            }
                        }
                    }
                }
            }
        } else {
            Log.w("$TAG No file picked")
        }
    }

    private var pendingImageCaptureFile: File? = null

    private val startCameraCapture = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { captured ->
        val path = pendingImageCaptureFile?.absolutePath
        if (path != null) {
            if (captured) {
                Log.i("$TAG Image was captured and saved in [$path]")
                sendInMessageViewModel.addAttachment(path)
            } else {
                Log.w("$TAG Image capture was aborted")
                lifecycleScope.launch {
                    FileUtils.deleteFile(path)
                }
            }
            pendingImageCaptureFile = null
        } else {
            Log.e("$TAG No pending captured image file!")
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i("$TAG CAMERA permission has been granted")
        } else {
            Log.e("$TAG CAMERA permission has been denied")
        }
    }

    private val requestRecordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i("$TAG RECORD_AUDIO permission has been granted, starting voice message recording")
            sendInMessageViewModel.startVoiceMessageRecording()
        } else {
            Log.e("$TAG RECORD_AUDIO permission has been denied")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == EXPORT_FILE_AS_DOCUMENT) {
            if (resultCode == Activity.RESULT_OK) {
                val filePath = filePathToExport
                if (filePath != null) {
                    data?.data?.also { documentUri ->
                        Log.i(
                            "$TAG Exported file [$filePath] should be stored in URI [$documentUri]"
                        )
                        convViewModel.copyFileToUri(filePath, documentUri)
                        filePathToExport = null
                    }
                } else {
                    Log.e("$TAG No file path waiting to be exported!")
                }
            } else {
                Log.w("$TAG Export file activity result is [$resultCode], aborting")
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activity?.onBackPressedDispatcher?.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {

                findNavController().navigate(R.id.action_conversationFragment_to_chatListFragment)
            }
        })


        adapter = ConversationEventAdapter()

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentConversationBinding.inflate(inflater,container,false)

        binding.lifecycleOwner = viewLifecycleOwner

        // The following prevents re-computing conversation history
        // when going back from a sub-fragment such as media grid or info
        if (!::convViewModel.isInitialized) {
            convViewModel = ViewModelProvider(this)[ConversationViewModel::class.java]
        }
        binding.viewModel = convViewModel

        sendInMessageViewModel = ViewModelProvider(this)[SendInMessageViewModel::class.java]
        binding.sendMessageViewModel = sendInMessageViewModel

        binding.eventsList.setHasFixedSize(true)
        val layoutManager = LinearLayoutManager(requireContext())
        layoutManager.stackFromEnd = true
        binding.eventsList.layoutManager = layoutManager

        convViewModel.remoteRefKey.observe(viewLifecycleOwner) { key ->
           // Toast.makeText(requireContext(),"I got the key $key",Toast.LENGTH_SHORT).show()
            refKey = key
        }

        binding.title.setOnClickListener() {

            if (refKey.isNotEmpty()) {
                findNavController().navigate(
                    ConversationFragmentDirections.actionConversationFragmentToContactProfilePage(
                        refKey
                    )
                )
            } else {
                Toast.makeText(requireContext(),"User Not In Your Contacts!",Toast.LENGTH_SHORT).show()
            }

        }

        convViewModel.remoteUser.observe(viewLifecycleOwner) {name->
           // Toast.makeText(requireContext(),"I am remote ${name} ----",Toast.LENGTH_SHORT).show()
        }

        sharedMainViewModel = run {
            ViewModelProvider(this)[SharedMainViewModel::class.java]
        }

        localeSipUri = args.localSipUri
        remoteSipUri = args.remoteSipUri


        convViewModel.findChatOrCreateRoom(
            room = null,
            localSipUri = localeSipUri,
            remoteSipUri = remoteSipUri
        )
       // Log.i(TAG,"outaaa ----")

        sendInMessageViewModel.textToSend.observe(viewLifecycleOwner) { text ->
            //Log.i(TAG,"In can see yaa $text")
        }

        convViewModel.chatRoomFoundEvent.observe(viewLifecycleOwner) {
            it.consume { found ->
                if (found) {
                    Log.i(TAG,"Found ChatRoom Observed! Configuring send Message ViewModel")
                    sendInMessageViewModel.configureChatRoom(convViewModel.chatRoom)
                }
            }
        }

        convViewModel.chatRoomCreatedEvent.observe(viewLifecycleOwner) {
            it.consume { create ->
                if (create) {
                    Log.i(TAG,"Create ChatRoom Observed! Configuring send Message ViewModel")
                    sendInMessageViewModel.configureChatRoom(convViewModel.chatRoom)
                }
            }
        }

        convViewModel.updateEvents.observe(viewLifecycleOwner) {
            Log.i(TAG,"i am in update...........")
            it.consume {
                val items = convViewModel.eventsList
                Log.i(TAG,"Events (messages) list submitted, contains [${items.size}] items")
                adapter.submitList(items)

                if (binding.eventsList.adapter != adapter) {
                    binding.eventsList.adapter = adapter
                }
            }
        }

        convViewModel.itemToScrollTo.observe(viewLifecycleOwner) { position ->
            if (position >= 0) {
                Log.i("$TAG Scrolling to message/event at position [$position]")
                val recyclerView = binding.eventsList
                recyclerView.scrollToPosition(position)
            }
        }

        sendInMessageViewModel.askRecordAudioPermissionEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.w("$TAG Asking for RECORD_AUDIO permission")
                requestRecordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        convViewModel.openWebBrowserEvent.observe(viewLifecycleOwner) {
            it.consume { url ->
//                if (messageLongPressViewModel.visible.value == true) return@consume
                Log.i("$TAG Requesting to open web browser on page [$url]")
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(browserIntent)
                } catch (e: Exception) {
                    Log.e(
                        "$TAG Can't start ACTION_VIEW intent for URL [$url]: $e"
                    )
                }
            }
        }


        scrollListener = object : ConversationScrollListener(layoutManager) {
            @UiThread
            override fun onLoadMore(totalItemsCount: Int) {
                //if (convViewModel.searchInProgress.value == false) {
                    convViewModel.loadMoreData(totalItemsCount)
               // }
            }

            @UiThread
            override fun onScrolledUp() {
                convViewModel.isUserScrollingUp.value = true
            }

            @UiThread
            override fun onScrolledToEnd() {
                if (convViewModel.isUserScrollingUp.value == true) {
                    convViewModel.isUserScrollingUp.value = false
                    Log.i("$TAG Last message is visible, considering conversation as read")
                    convViewModel.markAsRead()
                }
            }
        }

        convViewModel.fileToDisplayEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                Log.i(TAG,"User clicked on file [${model.path}], let's display it in file viewer")
                goToFileViewer(model)
            }
        }

        binding.setScrollToBottomClickListener {
            scrollToFirstUnreadMessageOrBottom()
        }

        binding.setBackClickListener {
            findNavController().popBackStack()
        }

        binding.setOpenFilePickerClickListener {
            Log.i(TAG,"$ Opening media picker")
            pickMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
        }

        binding.sendArea.messageToSend.setControlEnterListener(object :
            RichEditText.RichEditTextSendListener {
            override fun onControlEnterPressedAndReleased() {
                Log.i("$TAG Detected left control + enter key presses, sending message")
                sendInMessageViewModel.sendMessage()
            }
        })

        binding.setOpenCameraClickListener {
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG,"Asking for CAMERA permission")
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            } else {
                Log.i(TAG,"In camera else")
                val timeStamp = TimestampUtils.toFullString(
                    System.currentTimeMillis(),
                    timestampInSecs = false
                )
                val tempFileName = "$timeStamp.jpg"
                Log.i(
                    TAG,
                    "Opening camera to take a picture, will be stored in file [$tempFileName]"
                )
                val file = FileUtils.getFileStoragePath(tempFileName)
                try {
                    val publicUri = FileProvider.getUriForFile(
                        Objects.requireNonNull(requireContext()),
                        BuildConfig.APPLICATION_ID + ".fileprovider",
                        file
                    )
                    pendingImageCaptureFile = file
                    startCameraCapture.launch(publicUri)
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "Failed to get public URI for file in which to store captured image: $e"
                    )
                }
            }
        }

        convViewModel.contactToDisplayEvent.observe(viewLifecycleOwner) {
            it.consume { friendRefKey ->
                findNavController().navigate(
                    ConversationFragmentDirections.actionConversationFragmentToContactProfilePage(
                        friendRefKey
                    )
                )
            }
        }

        convViewModel.showToastEvent.observe(viewLifecycleOwner) { msg ->
            Toast.makeText(requireContext(),msg,Toast.LENGTH_LONG).show()
        }

        // Inflate the layout for this fragment
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedMainViewModel.displayFileEvent.observe(viewLifecycleOwner) {
            it.consume { bundle ->
                Log.i(TAG, "in conv list fragggg")
                    val path = bundle.getString("path", "")
                    val isMedia = bundle.getBoolean("isMedia", false)
                    if (path.isEmpty()) {
                        Log.e("$TAG Can't navigate to file viewer for empty path!")
                        return@consume
                    }

                    Log.i(
                        "$TAG Navigating to [${if (isMedia) "media" else "file"}] viewer fragment with path [$path]"
                    )

                    val file = File(path) // Assuming path is the full path to the file
                    val uri = FileProvider.getUriForFile(
                        requireContext(),
                        "mu.location.savmed.fileprovider", // Replace with your app's file provider authority
                        file
                    )
                    val intent = Intent()
                    intent.setAction(Intent.ACTION_VIEW)
                    intent.setDataAndType(uri, "image/*")
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context?.startActivity(intent)
//                    if (isMedia) {
//                        val intent = Intent(requireActivity(), MediaViewerActivity::class.java)
//                        intent.putExtras(bundle)
//                        startActivity(intent)
//                    } else {
//                        val intent = Intent(requireActivity(), FileViewerActivity::class.java)
//                        intent.putExtras(bundle)
//                        startActivity(intent)
//                    }

            }
        }
    }

    private fun goToFileViewer(fileModel: FileModel) {
        val path = fileModel.path
        Log.i("$TAG Navigating to file viewer fragment with path [$path]")
        val extension = FileUtils.getExtensionFromFileName(path)
        val mime = FileUtils.getMimeTypeFromExtension(extension)

        val bundle = Bundle()
        bundle.apply {
            putString("localSipUri", convViewModel.localSipUri)
            putString("remoteSipUri", convViewModel.remoteSipUri)
            putString("path", path)
            putBoolean("isEncrypted", fileModel.isEncrypted)
            putLong("timestamp", fileModel.fileCreationTimestamp)
            putString("originalPath", fileModel.originalPath)
        }
        when (FileUtils.getMimeType(mime)) {
            FileUtils.MimeType.Image, FileUtils.MimeType.Video, FileUtils.MimeType.Audio -> {
                bundle.putBoolean("isMedia", true)
                sharedMainViewModel.displayFileEvent.value = Event(bundle)
            }
            FileUtils.MimeType.Pdf, FileUtils.MimeType.PlainText -> {
                bundle.putBoolean("isMedia", false)
                sharedMainViewModel.displayFileEvent.value = Event(bundle)
            }
            else -> {
                Log.i(TAG,"Cannot Open file here! Export it to Another Application")
                //showOpenOrExportFileDialog(path, mime)
            }
        }
    }



//    private fun showOpenOrExportFileDialog(path: String, mime: String) {
//        Log.i(TAG,"YOO for image")
//        val model = ConfirmationDialogModel()
//        val dialog = DialogUtils.getOpenOrExportFileDialog(
//            requireActivity(),
//            model
//        )
//
//        model.dismissEvent.observe(viewLifecycleOwner) {
//            it.consume {
//                dialog.dismiss()
//            }
//        }
//
//        model.cancelEvent.observe(viewLifecycleOwner) {
//            it.consume {
//                openFileInAnotherApp(path, mime)
//                dialog.dismiss()
//            }
//        }
//
//        model.confirmEvent.observe(viewLifecycleOwner) {
//            it.consume {
//                exportFile(path, mime)
//                dialog.dismiss()
//            }
//        }
//
//        dialog.show()
//    }

    private val globalLayoutObserver = object: ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            Log.i(TAG,"Global Layout Observer Triggered!")
            binding.eventsList.viewTreeObserver.removeOnGlobalLayoutListener(this)

            if (::scrollListener.isInitialized) {
                binding.eventsList.addOnScrollListener(scrollListener)
            }

            val unreadCount = convViewModel.unreadMessagesCount.value ?: 0
            if (unreadCount > 0) {
                Log.i(TAG,"Unread COUNT ${unreadCount}")
                scrollToFirstUnreadMessageOrBottom()
            }
        }
    }

    private val dataObserver = object : AdapterDataObserver() {
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            Log.i(TAG, "New Item Inserted.....")
            if (positionStart > 0) {
                adapter.notifyItemChanged(positionStart - 1) // For grouping purposes
            } else if (adapter.itemCount != itemCount) {
                if (convViewModel.searchInProgress.value == true) {
                    val recyclerView = binding.eventsList
                    var indexToScrollTo = convViewModel.itemToScrollTo.value ?: 0
                    if (indexToScrollTo < 0) indexToScrollTo = 0
                    Log.i(
                        "$TAG User has loaded more history to go to a specific message, scrolling to index [$indexToScrollTo]"
                    )
                    recyclerView.scrollToPosition(indexToScrollTo)
                    convViewModel.searchInProgress.postValue(false)
                }
            }

            if (convViewModel.isUserScrollingUp.value == true) {
                Log.i(
                    "$TAG [$itemCount] events have been loaded but user was scrolling up in conversation, do not scroll"
                )
                return
            }

            if (positionStart == 0 && adapter.itemCount == itemCount) {
                // First time we fill the list with messages
                Log.i("$TAG [$itemCount] events have been loaded")
                val unreadCount = convViewModel.unreadMessagesCount.value ?: 0
                if (unreadCount > 0) {
                    Log.i("$TAG [$unreadCount] unread messages, scrolling to first one")
                    scrollToFirstUnreadMessageOrBottom()
                }
            } else {
                Log.i(
                    "$TAG [$itemCount] new events have been loaded, scrolling to first unread message"
                )
                scrollToFirstUnreadMessageOrBottom()
            }
        }
    }

    private fun scrollToFirstUnreadMessageOrBottom() {
        if (adapter.itemCount == 0) {
            Log.w("$TAG No item in adapter yet, do not scroll")
            return
        }

        val recyclerView = binding.eventsList
        val firstUnreadMessagePosition = adapter.getFirstUnreadMessagePosition()
        val currentPosition = (recyclerView.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition()
        val indexToScrollTo = if (firstUnreadMessagePosition != -1 && firstUnreadMessagePosition != currentPosition) {
            firstUnreadMessagePosition
        } else {
            adapter.itemCount - 1
        }

        recyclerView.scrollToPosition(indexToScrollTo)
        val bottomReached = indexToScrollTo == adapter.itemCount -1
        convViewModel.isUserScrollingUp.value = !bottomReached
        if (bottomReached) {
            convViewModel.markAsRead()
        } else {
            val firstUnread = adapter.currentList[firstUnreadMessagePosition]
            if (firstUnread.model is MessageModel) {
                Log.i("$TAG Marking only first message (to which user scrolled to) as read")
                //firstUnread.model.markAsRead()
                convViewModel.updateUnreadMessageCount()
                sharedMainViewModel.updateUnreadMessageCountForCurrentConversationEvent.postValue(
                    Event(true)
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()

        convViewModel.updateCurrentlyDisplayedConversation()

        // Wait for items to be displayed
        binding.eventsList
            .viewTreeObserver
            .addOnGlobalLayoutListener(globalLayoutObserver)

        try {
            adapter.registerAdapterDataObserver(dataObserver)
        } catch (e: IllegalStateException) {
            Log.e("$TAG Failed to register data observer to adapter: $e")
        }

    }

    override fun onPause() {
        super.onPause()
        if (::scrollListener.isInitialized) {
            binding.eventsList.removeOnScrollListener(scrollListener)
        }
        binding.eventsList.viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutObserver)

        coreContext.postOnCoreThread {
            coreContext.notificationManager.resetCurrentlyDisplayedChatRoomId()
        }

        try {
            adapter.unregisterAdapterDataObserver(dataObserver)
        } catch (e: IllegalStateException) {
            Log.e("$TAG Failed to unregister data observer to adapter: $e")
        }
    }

    private fun openFileInAnotherApp(path: String, mime: String) {
        val intent = Intent(Intent.ACTION_VIEW)
        val contentUri: Uri =
            FileUtils.getPublicFilePath(requireContext(), path)
        intent.setDataAndType(contentUri, mime)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            Log.i("$TAG Trying to start ACTION_VIEW intent for file [$path]")
            requireContext().startActivity(intent)
        } catch (anfe: ActivityNotFoundException) {
            Log.e("$TAG Can't open file [$path] in third party app: $anfe")
            val message = getString(
                R.string.conversation_no_app_registered_to_handle_content_type_error_toast
            )
            val icon = R.drawable.file
            Toast.makeText(requireContext(),message,Toast.LENGTH_LONG).show()
        }
    }

    private fun exportFile(path: String, mime: String) {
        filePathToExport = path

        Log.i("$TAG Asking where to save file [$filePathToExport] on device")
        val name = FileUtils.getNameFromFilePath(path)
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mime
            putExtra(Intent.EXTRA_TITLE, name)
        }
        startActivityForResult(intent, EXPORT_FILE_AS_DOCUMENT)
    }

}