package mu.location.savmed.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Bundle
import android.util.ArraySet
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.contacts.AvatarGenerator
import mu.location.savmed.contacts.getPerson
import mu.location.savmed.ui.call.CallActivity
import org.linphone.core.ChatRoom
import org.linphone.mediastream.Version

class ShortCutUtils {
    companion object {
        private const val TAG = "[ShortCut Utils]"

        @WorkerThread
        fun removeShortCutToChatRoom(chatRoom: ChatRoom) {
            val id = SavMedUtils.getChatRoomId(chatRoom)
            Log.i(TAG,"Removing ShortCut of ChatRoom with id [$id]")
            ShortcutManagerCompat.removeLongLivedShortcuts(coreContext.context, arrayListOf(id))
        }

        fun createShortcutsToChatRooms(context: Context) {
            if (ShortcutManagerCompat.isRateLimitingActive(context)) {
                Log.e(TAG,"Rate limiting active, Aborting")
                return
            }

            Log.i(TAG,"Creating dynamic shortcuts for conversations")
            val defaultAccount = coreContext.core.defaultAccount
            if (defaultAccount == null) {
                Log.w(TAG,"No default account found, skipping...")
                return
            }

            var count = 0
            for ( chatRoom in defaultAccount.chatRooms) {
//                if(defaultAccount.params.en && !chatRoom.currentParams.isEncryptionEnabled){
//                    Log.w(TAG,
//                        "Account is in secure mode, skipping not encrypted conversation [${SavMedUtils.getChatRoomId(
//                            chatRoom
//                        )}]"
//                    )
//                    continue
//                }

                if (count >= 4) {
                    Log.i(TAG,"We already created [$count] shortcuts, stopping here")
                    break
                }

                val shortcut: ShortcutInfoCompat? = createChatRoomShortcut(context, chatRoom)
                if (shortcut != null) {
                    Log.i(TAG,"Created dynamic shortcut for ${shortcut.shortLabel}")
                    try {
                        val keepGoing = ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
                        if (keepGoing) {
                            count += 1
                        } else {
                            break
                        }
                    } catch (e: Exception) {
                       Log.e(TAG ,"Failed to push dynamic shortcut for ${shortcut.shortLabel}: $e")
                    }
                }
            }
            Log.i(TAG,"Created $count dynamic shortcuts")
        }

        private fun createChatRoomShortcut(context: Context,chatRoom: ChatRoom): ShortcutInfoCompat? {
            val localAddress = chatRoom.localAddress
            val peerAddress = chatRoom.peerAddress
            val id = SavMedUtils.getChatRoomId(localAddress, peerAddress)

            try {
                val categories: ArraySet<String> = ArraySet()
                categories.add(ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION)

                val personsList = arrayListOf<Person>()
                val subject: String
                val icon: IconCompat = if (chatRoom.hasCapability(
                        ChatRoom.Capabilities.Basic.toInt()
                    )
                ) {
                    val avatarModel = coreContext.contactsManager.getContactAvatarModelForAddress(
                        peerAddress
                    )
                    val contact = avatarModel.friend
                    val person = contact.getPerson()
                    personsList.add(person)

                    subject = contact.name ?: SavMedUtils.getDisplayName(peerAddress)
                    person.icon ?: AvatarGenerator(context).setInitials(
                        AppUtils.getInitials(subject)
                    ).buildIcon()

                } else if (chatRoom.hasCapability(ChatRoom.Capabilities.OneToOne.toInt()) && chatRoom.participants.isNotEmpty()) {
                    val address = chatRoom.participants.first().address
                    val avatarModel = coreContext.contactsManager.getContactAvatarModelForAddress(
                        address
                    )
                    val contact = avatarModel.friend
                    val person = contact.getPerson()
                    personsList.add(person)

                    subject = contact.name ?: SavMedUtils.getDisplayName(address)
                    person.icon ?: AvatarGenerator(context).setInitials(
                        AppUtils.getInitials(subject)
                    ).buildIcon()
                } else {
                    subject = chatRoom.subject.orEmpty()
                    AvatarGenerator(context).setInitials(AppUtils.getInitials(subject)).buildIcon()
                }

                val persons = arrayOfNulls<Person>(personsList.size)
                personsList.toArray(persons)

                val localSipUri = localAddress.asStringUriOnly()
                val peerSipUri = peerAddress.asStringUriOnly()

                val args = Bundle()
                args.putString("RemoteSipUri",peerSipUri)
                args.putString("LocalSipUri", localSipUri)

                val intent = Intent(Intent.ACTION_MAIN)
                intent.setClass(context, CallActivity::class.java)  // Add receive Put extra in call Activity to navigate to chat Fragment
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                intent.putExtra("Chat", true)
                intent.putExtra("RemoteSipUri", peerSipUri)
                intent.putExtra("LocalSipUri", localSipUri)

                return ShortcutInfoCompat.Builder(context, id)
                    .setShortLabel(subject)
                    .setIcon(icon)
                    .setPersons(persons)
                    .setCategories(categories)
                    .setIntent(intent)
                    .setLongLived(Version.sdkAboveOrEqual(Version.API30_ANDROID_11))
                    .setLocusId(LocusIdCompat(id))
                    .build()
            } catch (e: NumberFormatException) {
                Log.e(TAG,"Create ChatRoom Shortcut for [$id] exception: $e")
            }
            return null
        }

        @WorkerThread
        fun isShortcutToChatRoomAlreadyCreated(context: Context,chatRoom: ChatRoom): Boolean {
            val id = SavMedUtils.getChatRoomId(chatRoom)
            val found = ShortcutManagerCompat.getDynamicShortcuts(context).find {
                it.id == id
            }
            return found != null
        }
    }
}