package mu.location.savmed.compatibility

import android.os.Build
import android.os.Environment
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.S)
class Api31Compatibility {
    companion object {

        const val TAG = "[API 31 Compatibility]"
        fun getRecordingsDirectory(): String {
            return Environment.DIRECTORY_RECORDINGS
        }
    }
}