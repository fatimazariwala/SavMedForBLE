package mu.location.savmed.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import mu.location.savmed.contacts.AvatarGenerator
import org.linphone.core.tools.Log
import java.io.FileNotFoundException

class ImageUtils {
    companion object {
        private const val TAG = "[Image Utils]"

        @AnyThread
        fun getGeneratedAvatar(context: Context, size: Int = 0, textSize: Int = 0, initials: String): BitmapDrawable {
            val builder = AvatarGenerator(context)
            builder.setInitials(initials)
            if (size > 0) {
                builder.setAvatarSize(
                    AppUtils.getDimension(size).toInt()
                )
            }
            if (textSize > 0) {
                builder.setTextSize(AppUtils.getDimension(textSize))
            }
            return builder.buildDrawable()
        }

        @SuppressLint("NewApi")
        @WorkerThread
        fun getBitmap(
            context: Context,
            path: String?,
            round: Boolean = false
        ): Bitmap? {
            Log.d("$TAG Trying to create Bitmap from path [$path]")
            if (path != null) {
                try {
                    val fromPictureUri = Uri.parse(path)
                    if (fromPictureUri == null) {
                        Log.e("$TAG Failed to parse path [$path] as URI")
                        return null
                    }

                    // We make a copy to ensure Bitmap will be Software and not Hardware, required for shortcuts
                    val bitmap = ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(context.contentResolver, fromPictureUri)
                    ).copy(
                        Bitmap.Config.ARGB_8888,
                        true
                    )
                    return if (round) {
                        getRoundBitmap(bitmap)
                    } else {
                        bitmap
                    }
                } catch (fnfe: FileNotFoundException) {
                    Log.e("$TAG File [$path] not found: $fnfe")
                    return null
                } catch (e: Exception) {
                    Log.e("$TAG Failed to get bitmap using path [$path]: $e")
                    return null
                }
            }

            Log.e("$TAG Can't get bitmap from null URI")
            return null
        }

        @AnyThread
        private fun getRoundBitmap(bitmap: Bitmap): Bitmap {
            val output =
                Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val color = -0xbdbdbe
            val paint = Paint()
            val rect =
                Rect(0, 0, bitmap.width, bitmap.height)
            paint.isAntiAlias = true
            canvas.drawARGB(0, 0, 0, 0)
            paint.color = color
            canvas.drawCircle(
                bitmap.width / 2.toFloat(),
                bitmap.height / 2.toFloat(),
                bitmap.width / 2.toFloat(),
                paint
            )
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bitmap, rect, rect, paint)
            return output
        }
    }
}
