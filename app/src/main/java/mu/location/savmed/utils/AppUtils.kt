package mu.location.savmed.utils

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.annotation.AnyThread
import androidx.annotation.DimenRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import mu.location.savmed.SavMed.Companion.coreContext
import org.linphone.core.tools.Log
import java.util.Locale

class AppUtils {

    companion object {

        private const val TAG = "[App Utils]"

        @AnyThread
        fun getDimension(@DimenRes id: Int): Float {
            return coreContext.context.resources.getDimension(id)
        }

        @AnyThread
        fun getStringWithPlural(@PluralsRes id: Int, count: Int, value: String): String {
            return coreContext.context.resources.getQuantityString(id, count, value)
        }

        @AnyThread
        fun getString(@StringRes id: Int): String {
            return coreContext.context.getString(id)
        }

        @AnyThread
        fun getFormattedString(@StringRes id: Int, arg: Any): String {
            return coreContext.context.getString(id, arg)
        }

        @AnyThread
        fun getFormattedString(@StringRes id: Int, arg1: Any, arg2: Any): String {
            return coreContext.context.getString(id, arg1, arg2)
        }


        @AnyThread
        fun getDeviceName(context: Context): String {
            var name = Settings.Global.getString(
                context.contentResolver,
                Settings.Global.DEVICE_NAME
            )
            if (name == null) {
                name = Settings.Secure.getString(
                    context.contentResolver,
                    "bluetooth_name"
                )
            }
            if (name == null) {
                name = Build.MANUFACTURER + " " + Build.MODEL
            }
            return name
        }

        @AnyThread
        fun getFirstLetter(displayName: String): String {
            return getInitials(displayName, 1)
        }

        @AnyThread
        fun getInitials(displayName: String, limit: Int = 2): String {
            if (displayName.isEmpty()) return ""

            val split = displayName.uppercase(Locale.getDefault()).split(" ")
            var initials = ""
            var characters = 0

            for (i in split.indices) {
                val split = split[i]
                if (split.isNotEmpty()) {
                    try {
                        val symbol = extractFirstSymbol(split)
                        initials += symbol
                        if (symbol.length > 1) {
                            break
                        }
                    } catch (e: Exception) {
                        Log.e(TAG,"Failed to extract first symbol if any: $e")
                        initials += split[0]
                    }

                    characters += 1
                    if (characters >= limit) break
                }
            }
            return initials
        }

        @AnyThread
        private fun extractFirstSymbol(text: String): String {
            val sequence = StringBuilder(text.length)
            var isInJoin = false
            var codePoint: Int

            var i = 0
            while (i < text.length) {
                codePoint = text.codePointAt(i)
                if (codePoint == 0x200D) {
                    isInJoin = true
                    if (sequence.isEmpty()) {
                        i = text.offsetByCodePoints(i, 1)
                        continue
                    }
                } else {
                    if (sequence.isNotEmpty() && !isInJoin) break
                    isInJoin = false
                }
                sequence.appendCodePoint(codePoint)
                i = text.offsetByCodePoints(i, 1)
            }

            if (isInJoin) {
                for (i in sequence.length - 1 downTo 0) {
                    if (sequence[i].code == 0x200D) sequence.deleteCharAt(i) else break
                }
            }

            return sequence.toString()
        }
    }
}