package mu.location.savmed.bluetooth.bluetoothLE

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import mu.location.savmed.R
import mu.location.savmed.bluetooth.bluetoothLE.models.writeMessage

class MessageAdapter(private val messages: List<writeMessage>) :
    RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val fromText: TextView = itemView.findViewById(R.id.tv_from)
        val messageText: TextView = itemView.findViewById(R.id.tv_message)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.ble_message_item_layout, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.fromText.text = "${message.From}"
        holder.messageText.text = "SOS Indication approx ${message.dist}m away"
    }

    override fun getItemCount(): Int = messages.size
}