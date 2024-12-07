/*
 * Copyright (c) 2010-2024 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package mu.location.savmed.ui.main.recordings.model

import android.os.Build
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.utils.SavMedUtils
import mu.location.savmed.utils.TimestampUtils
import java.text.SimpleDateFormat
import java.util.Locale
import org.linphone.core.Factory
import org.linphone.core.tools.Log


class RecordingModel @WorkerThread constructor(
    val filePath: String,
    val fileName: String,
    isLegacy: Boolean = false
) {
    companion object {
        private const val TAG = "[Recording Model]"
    }

    val sipUri: String

    val displayName: String

    val timestamp: Long

    lateinit var month: String

    val dateTime: String

    val formattedDuration: String

    val duration: Int

    init {
        if (isLegacy) {
            val username = fileName.split("_")[0]
            val sipAddress = coreContext.core.interpretUrl(username, false)
            sipUri = sipAddress?.asStringUriOnly() ?: username
            displayName = if (sipAddress != null) {
                //val contact = coreContext.contactsManager.findContactByAddress(sipAddress)
                val contact = coreContext.core.findFriend(sipAddress)
                contact?.name ?: SavMedUtils.getDisplayName(sipAddress)
            } else {
                sipUri
            }

            val parsedDate = fileName.split("_")[1]
            var parsedTimestamp = 0L
            try {
                val date = SimpleDateFormat("dd-MM-yyyy-HH-mm-ss", Locale.getDefault()).parse(
                    parsedDate
                )
                parsedTimestamp = date?.time ?: 0L
            } catch (e: Exception) {
                Log.e("$TAG Failed to parse legacy timestamp [$parsedDate]")
            }
            timestamp = parsedTimestamp
        } else {
            val withoutHeader = fileName.substring(SavMedUtils.RECORDING_FILE_NAME_HEADER.length)
            val indexOfSeparator = withoutHeader.indexOf(
                SavMedUtils.RECORDING_FILE_NAME_URI_TIMESTAMP_SEPARATOR
            )
            sipUri = withoutHeader.substring(0, indexOfSeparator)
            val sipAddress = Factory.instance().createAddress(sipUri)
            displayName = if (sipAddress != null) {
//                val contact = coreContext.contactsManager.findContactByAddress(sipAddress)
                val contact = coreContext.core.findFriend(sipAddress)
                contact?.name ?: SavMedUtils.getDisplayName(sipAddress)
            } else {
                sipUri
            }

            val parsedTimestamp = withoutHeader.substring(
                indexOfSeparator + SavMedUtils.RECORDING_FILE_NAME_URI_TIMESTAMP_SEPARATOR.length,
                withoutHeader.lastIndexOf(".")
            )
            Log.i(
                "$TAG Extract SIP URI [$sipUri] and timestamp [$parsedTimestamp] from file [$fileName]"
            )
            timestamp = parsedTimestamp.toLong()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            month = TimestampUtils.month(timestamp, timestampInSecs = false)
        } else {
            Log.i(TAG,"Could Not Convert Time Stamp requires API VerSION O=26")
        }
        val date = TimestampUtils.toString(
            timestamp,
            timestampInSecs = false,
            onlyDate = true,
            shortDate = false
        )
        val time = TimestampUtils.timeToString(timestamp, timestampInSecs = false)
        dateTime = "$date - $time"

        val audioPlayer = coreContext.core.createLocalPlayer(null, null, null)
        if (audioPlayer != null) {
            audioPlayer.open(filePath)
            duration = audioPlayer.duration
            formattedDuration = SimpleDateFormat("mm:ss", Locale.getDefault()).format(duration)
        } else {
            duration = 0
            formattedDuration = "??:??"
        }
    }

    @UiThread
    suspend fun delete() {
        Log.i("$TAG Deleting call recording [$filePath]")
        mu.location.savmed.utils.FileUtils.deleteFile(filePath)
    }
}
