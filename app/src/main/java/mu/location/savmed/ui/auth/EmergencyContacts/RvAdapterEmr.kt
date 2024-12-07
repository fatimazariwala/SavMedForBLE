package mu.location.savmed.ui.call

import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import android.view.LayoutInflater
import mu.location.savmed.databinding.ItemLayoutBinding
import mu.location.savmed.models.UsersItem
import mu.location.savmed.ui.auth.EmergencyContacts.EmergencyContact
import mu.location.savmed.ui.auth.EmergencyContacts.EmergencyContactsFragment

class RvAdapterEmr(private val userList: List<EmergencyContact>, private val clickListener: EmergencyContactsFragment): RecyclerView.Adapter<RvAdapterEmr.ViewHolder>() {

    inner class ViewHolder(val binding: ItemLayoutBinding) :
        RecyclerView.ViewHolder(binding.root) {

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemLayoutBinding.inflate(LayoutInflater.from(parent.context),parent,false)
        )
    }

    override fun getItemCount(): Int {
        return userList.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        Log.i("I am hhhh","from rcycker")
        val currentItem = userList[position]
        holder.binding.apply{
            tvFullName.text = currentItem.contact
           // Log.i("from activity",currentItem.lastName)
            tvUserName.text = currentItem.category
            callBtn.setOnClickListener() {
                //clickListener.outgoingCall(currentItem.contact)
            }
        }
    }
}