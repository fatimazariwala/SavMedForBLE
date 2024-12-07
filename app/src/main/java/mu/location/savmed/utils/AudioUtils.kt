package mu.location.savmed.utils

import android.content.Context
import android.media.AudioManager
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import mu.location.savmed.SavMed.Companion.coreContext
import org.linphone.core.AudioDevice
import org.linphone.core.tools.Log

class AudioUtils {
    companion object {
        private const val TAG = "[Audio Utils]"

        @AnyThread
        fun acquireAudioFocusForVoiceRecordingOrPlayback(context: Context): AudioFocusRequestCompat {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val audioAttrs = AudioAttributesCompat.Builder()
                .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
                .build()

            val request =
                AudioFocusRequestCompat.Builder(
                    AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
                    .setAudioAttributes(audioAttrs)
                    .setOnAudioFocusChangeListener { }
                    .build()
            when (AudioManagerCompat.requestAudioFocus(audioManager, request)) {
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                    Log.i("$TAG Voice recording/playback audio focus request granted")
                }
                AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
                    Log.w("$TAG Voice recording/playback audio focus request failed")
                }
                AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                    Log.w("$TAG Voice recording/playback audio focus request delayed")
                }
            }
            return request
        }

        fun releaseAudioFocusForVoiceRecordingOrPlayback(
            context: Context,
            request: AudioFocusRequestCompat
        ) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            AudioManagerCompat.abandonAudioFocusRequest(audioManager, request)
            Log.i("$TAG Voice recording/playback audio focus request abandoned")
        }

        @AnyThread
        fun isMediaVolumeLow(context: Context): Boolean {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            Log.i("$TAG Current media volume value is $currentVolume, max value is $maxVolume")
            return currentVolume <= maxVolume * 0.5
        }

        @WorkerThread
        fun getAudioPlayBackDeviceIdForCallRecordingOrVoiceMessage(): String? {
            var headphonesCard: String? = null
            var bluetoothCard: String? = null
            var speakerCard: String? = null
            var earpieceCard: String? = null
            for (device in coreContext.core.audioDevices) {
                if (device.hasCapability(AudioDevice.Capabilities.CapabilityPlay)) {
                    when (device.type) {
                        AudioDevice.Type.Headphones,AudioDevice.Type.Headset -> {
                            headphonesCard = device.id
                        }
                        AudioDevice.Type.Bluetooth, AudioDevice.Type.HearingAid -> {
                            bluetoothCard = device.id
                        }
                        AudioDevice.Type.Speaker -> {
                            speakerCard = device.id
                        }
                        AudioDevice.Type.Earpiece -> {
                            earpieceCard = device.id
                        }
                        else -> {}
                    }
                }
            }
            Log.i(TAG,
                "Found headset/headphones/hearingAid sound card [$headphonesCard], bluetooth sound card [$bluetoothCard], speaker sound card [$speakerCard] and earpiece sound card [$earpieceCard]"
            )
            return if (coreContext.isConnectedToAndroidAuto) {
                Log.w(TAG,
                    "Device seems to be connected to Android Auto, do not use bluetooth sound card, priority order is headphone > speaker > earpiece"
                )
                headphonesCard ?: speakerCard ?: earpieceCard
            } else {
                Log.i(TAG,
                    "Device doesn't seem to be connected to Android Auto, use headphone > bluetooth > speaker > earpiece sound card in priority order"
                )
                headphonesCard ?: bluetoothCard ?: speakerCard ?: earpieceCard
            }
        }


        @WorkerThread
        fun getAudioRecordingDeviceIdForVoiceMessage(): AudioDevice? {
            // In case no headset/hearing aid/bluetooth is connected, use microphone sound card
            // If none are available, default one will be used
            var headsetCard: AudioDevice? = null
            var bluetoothCard: AudioDevice? = null
            var microphoneCard: AudioDevice? = null
            for (device in coreContext.core.audioDevices) {
                if (device.hasCapability(AudioDevice.Capabilities.CapabilityRecord)) {
                    when (device.type) {
                        AudioDevice.Type.Headphones, AudioDevice.Type.Headset -> {
                            headsetCard = device
                        }
                        AudioDevice.Type.Bluetooth, AudioDevice.Type.HearingAid -> {
                            bluetoothCard = device
                        }
                        AudioDevice.Type.Microphone -> {
                            microphoneCard = device
                        }
                        else -> {}
                    }
                }
            }
            Log.i(
                "$TAG Found headset/headphones/hearingAid sound card [$headsetCard], bluetooth sound card [$bluetoothCard] and microphone card [$microphoneCard]"
            )
            return headsetCard ?: bluetoothCard ?: microphoneCard
        }

        @WorkerThread
        fun getAudioPlaybackDeviceIdForCallRecordingOrVoiceMessage(): String? {
            // In case no headphones/headset/hearing aid/bluetooth is connected, use speaker sound card to play recordings, otherwise use earpiece
            // If none are available, default one will be used
            var headphonesCard: String? = null
            var bluetoothCard: String? = null
            var speakerCard: String? = null
            var earpieceCard: String? = null
            for (device in coreContext.core.audioDevices) {
                if (device.hasCapability(AudioDevice.Capabilities.CapabilityPlay)) {
                    when (device.type) {
                        AudioDevice.Type.Headphones, AudioDevice.Type.Headset -> {
                            headphonesCard = device.id
                        }
                        AudioDevice.Type.Bluetooth, AudioDevice.Type.HearingAid -> {
                            bluetoothCard = device.id
                        }
                        AudioDevice.Type.Speaker -> {
                            speakerCard = device.id
                        }
                        AudioDevice.Type.Earpiece -> {
                            earpieceCard = device.id
                        }
                        else -> {}
                    }
                }
            }
            Log.i(
                "$TAG Found headset/headphones/hearingAid sound card [$headphonesCard], bluetooth sound card [$bluetoothCard], speaker sound card [$speakerCard] and earpiece sound card [$earpieceCard]"
            )
            return if (coreContext.isConnectedToAndroidAuto) {
                Log.w(
                    "$TAG Device seems to be connected to Android Auto, do not use bluetooth sound card, priority order is headphone > speaker > earpiece"
                )
                headphonesCard ?: speakerCard ?: earpieceCard
            } else {
                Log.i(
                    "$TAG Device doesn't seem to be connected to Android Auto, use headphone > bluetooth > speaker > earpiece sound card in priority order"
                )
                headphonesCard ?: bluetoothCard ?: speakerCard ?: earpieceCard
            }
        }

    }
}