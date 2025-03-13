package mu.location.savmed.ui.chat.model

import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.utils.FileUtils
//import mu.location.savmed.utils.FileUtils
import mu.location.savmed.utils.TimestampUtils
import org.linphone.core.tools.Log

class FileModel @AnyThread constructor(
    val path: String,
    val fileName: String,
    val fileSize: Long,
    val fileCreationTimestamp: Long,
    val isEncrypted: Boolean,
    val originalPath: String,
    val isWaitingToBeDownloaded: Boolean = false,
    private val onClicked: ((model: FileModel) -> Unit)? = null
) {
    companion object {
        private const val TAG = "[File Model]"
    }

    val formattedFileSize = MutableLiveData<String>()

    val transferProgress = MutableLiveData<Int>()

    val transferProgressLabel = MutableLiveData<String>()

    val mediaPreview = MutableLiveData<String>()

    val mediaPreviewAvailable = MutableLiveData<Boolean>()

    val mimeType: FileUtils.MimeType

    val mimeTypeString: String

    val isMedia: Boolean

    val isImage: Boolean

    val isVideoPreview: Boolean

    val audioVideoDuration = MutableLiveData<String>()

    val isPdf: Boolean

    val isAudio: Boolean

    val month = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        TimestampUtils.month(fileCreationTimestamp)
    } else {
        Log.i(TAG,"File Creation TimeSTamp Conversion Failed due to Lower SDK Version")
    }

    val dateTime = TimestampUtils.toString(
        fileCreationTimestamp,
        shortDate = false,
        hideYear = false
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        mediaPreviewAvailable.postValue(false)
        updateTransferProgress(-1)
        formattedFileSize.postValue(FileUtils.bytesToDisplayableSize(fileSize))

        Log.i(TAG,"In file urlsssss init")

        if (!isWaitingToBeDownloaded) {
            val extension = FileUtils.getExtensionFromFileName(path)
            isPdf = extension == "pdf"

            val mime = FileUtils.getMimeTypeFromExtension(extension)
            mimeTypeString = mime

            mimeType = FileUtils.getMimeType(mime)
            isImage = mimeType == FileUtils.MimeType.Image
            isVideoPreview = mimeType == FileUtils.MimeType.Video
            isAudio = mimeType == FileUtils.MimeType.Audio

            if (isImage) {
                Log.i(TAG,"Yoo in media previe ${path.toString()}")
                mediaPreview.postValue(path)
                coreContext.postOnMainThread {
                    Log.i(TAG,"i am mediaPreview ${mediaPreview.value}")
                }
                mediaPreviewAvailable.postValue(true)
            } else if (isVideoPreview) {
                loadVideoPreview()
            }

            if (isVideoPreview || isAudio) {
                getDuration()
            }
            Log.d(
                "$TAG File has already been downloaded, extension is [$extension], MIME is [$mime]"
            )
        } else {
            mimeType = FileUtils.MimeType.Unknown
            mimeTypeString = "application/octet-stream"
            isPdf = false
            isImage = false
            isVideoPreview = false
            isAudio = false
        }

        isMedia = isVideoPreview || isImage
    }

    @AnyThread
    fun destroy() {
        if (isEncrypted) {
            Log.i("$TAG [VFS] Deleting plain file in cache: $path")
            scope.launch {
                FileUtils.deleteFile(path)
            }
        }
    }

    @AnyThread
    fun updateTransferProgress(percent: Int) {
        transferProgress.postValue(percent)
        if (percent < 0 || percent > 100) {
            transferProgressLabel.postValue("")
        } else {
            transferProgressLabel.postValue("$percent%")
        }
    }

    @UiThread
    fun onClick() {
        Log.i(TAG,"Yoo inveked")
        onClicked?.invoke(this)
    }

    @AnyThread
    suspend fun deleteFile() {
        Log.i("$TAG Deleting file [$path]")
        FileUtils.deleteFile(path)
    }

    @AnyThread
    private fun loadVideoPreview() {
        try {
            Log.i("$TAG Try to create an image preview of video file [$path]")
            val previewBitmap = ThumbnailUtils.createVideoThumbnail(
                path,
                MediaStore.Images.Thumbnails.MINI_KIND
            )
            if (previewBitmap != null) {
                val previewPath = FileUtils.storeBitmap(previewBitmap, fileName)
                Log.i("$TAG Preview of video file [$path] available at [$previewPath]")
                mediaPreview.postValue(previewPath)
                mediaPreviewAvailable.postValue(true)
            }
        } catch (e: Exception) {
            Log.e("$TAG Failed to get image preview for file [$path]: $e")
        }
    }

    @AnyThread
    private fun getDuration() {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(coreContext.context, Uri.parse(path))
            val durationInMs = retriever.extractMetadata(METADATA_KEY_DURATION)?.toInt() ?: 0
            val seconds = durationInMs / 1000
            val duration = TimestampUtils.durationToString(seconds)
            Log.d("$TAG Duration for file [$path] is $duration")
            audioVideoDuration.postValue(duration)
            retriever.release()
        } catch (e: Exception) {
            Log.e("$TAG Failed to get duration for file [$path]: $e")
        }
    }
}
