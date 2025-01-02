package mu.location.savmed.ui.contacts.adapter

import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import mu.location.savmed.R
import mu.location.savmed.databinding.ChatEmergencyConatctConnectAvatarBarCellBinding
import mu.location.savmed.databinding.ContactItemLayoutBinding
import mu.location.savmed.databinding.EmergencyContactItemsBinding
import mu.location.savmed.ui.contacts.models.ContactAvatarModel
import org.linphone.core.Friend


class ContactAdapter(
    private val favourite: Boolean = false,
    private val onCallClick: (String) -> Unit,
    private val onChatClick: (String) -> Unit,
    private val onInfoClick: (Friend,String) -> Unit,
    private val onRemoveClick: (ContactAvatarModel) -> Unit
) : ListAdapter<ContactAvatarModel, RecyclerView.ViewHolder>(ContactDiffCallback()) {

    companion object {
        const val TAG = "[Contact Adapter]"
    }

    init {
        Log.i(TAG,"in on innn   crearteeee bolder")
    }

    inner class ContactsViewHolder(
        private val binding: ContactItemLayoutBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(contactModel: ContactAvatarModel) {

            with(binding) {
                model = contactModel

                Log.i(TAG,"contact viewmodelr ${contactModel.name}")

                chatButton.setOnClickListener() {
                    contactModel.friend.address?.username?.let { it1 -> onChatClick(it1) }
                }

                deleteButton.setOnClickListener() {
                    Log.i(TAG,"Delete clicked...")
                    onRemoveClick(contactModel)
                }

                infoButton.setOnClickListener() {
                    Log.i(TAG,"I am rfekey .... ${contactModel.friend.refKey}")
                    contactModel.friend.refKey?.let { it1 -> onInfoClick(contactModel.friend,it1)}
                }

                callButton.setOnClickListener() {
                    contactModel.friend.address?.username?.let { it1 -> onCallClick(it1) }
                }

                frameLayout.setOnClickListener() {
                    if (expandableView.visibility == View.GONE) {
                        expandableView.visibility = View.VISIBLE
                    } else {
                        expandableView.visibility = View.GONE
                    }
                }

                executePendingBindings()
            }
        }
    }

    inner class EmergencyContactViewHolder(
        private val binding: ChatEmergencyConatctConnectAvatarBarCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(emrModel: ContactAvatarModel) {

            with(binding) {
                model = emrModel

                Log.i(TAG,"emr viewmodelr ${emrModel.name}")
                root.setOnClickListener() {
                    emrModel.friend.refKey?.let { it1 -> onInfoClick(emrModel.friend,it1)}
                }

                root.setOnLongClickListener() {
                    emrModel.friend.address?.username?.let { it1 -> onCallClick(it1) }
                    true
                }

                executePendingBindings()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        Log.i(TAG,"in on crearteeee bolder")

        if (favourite) {
            Log.i(TAG,"Faviourtite found...")
            val binding: ChatEmergencyConatctConnectAvatarBarCellBinding = DataBindingUtil.inflate(
                LayoutInflater.from(parent.context),
                R.layout.chat_emergency_conatct_connect_avatar_bar_cell,
                parent,
                false
            )
            val emrViewHolder = EmergencyContactViewHolder(binding)

            binding.apply {
                lifecycleOwner = parent.findViewTreeLifecycleOwner()
            }
            return emrViewHolder
        } else {

            val binding: ContactItemLayoutBinding = DataBindingUtil.inflate(
                LayoutInflater.from(parent.context),
                R.layout.contact_item_layout,
                parent,
                false
            )
            val viewHolder = ContactsViewHolder(binding)

            binding.apply {
                lifecycleOwner = parent.findViewTreeLifecycleOwner()
            }
            return viewHolder
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
      //  Log.i(TAG,"in on bind bolder")
        if (favourite) {
            (holder as EmergencyContactViewHolder).bind(getItem(position))
        } else {
            (holder as ContactsViewHolder).bind(getItem(position))
        }

    }

    interface ButtonClickListeners {
        fun removeContact(refKey: String)
    }


    private class ContactDiffCallback : DiffUtil.ItemCallback<ContactAvatarModel>() {
        override fun areItemsTheSame(
            oldItem: ContactAvatarModel,
            newItem: ContactAvatarModel
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: ContactAvatarModel,
            newItem: ContactAvatarModel
        ): Boolean {
            return false // oldItem & newItem are always the same because fetched from cache, so return false to force refresh
        }
    }
}



