package mu.location.savmed.ui.call.Adapters

import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import android.view.LayoutInflater
import mu.location.savmed.databinding.ItemLayoutBinding
import mu.location.savmed.models.UsersItem
import mu.location.savmed.ui.contacts.fragments.ContactFragment

class RvAdapter(private val userList: List<UsersItem>, private val clickListener: ContactFragment): RecyclerView.Adapter<RvAdapter.ViewHolder>() {

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
            tvFullName.text = currentItem.lastName
           // Log.i("from activity",currentItem.lastName)
            tvUserName.text = currentItem.firstName
            callBtn.setOnClickListener() {
               // clickListener.outgoingCall(currentItem.firstName)
            }
            chatBtn.setOnClickListener() {
               // clickListener.startChat(currentItem.firstName)
            }
        }
    }
}