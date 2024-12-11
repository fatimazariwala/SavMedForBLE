package mu.location.savmed.bluetooth.bluetoothLE

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import mu.location.savmed.R
import mu.location.savmed.SavMed.Companion.bleServer
import mu.location.savmed.bluetooth.bluetoothLE.models.BluetoothLEViewModel
import mu.location.savmed.bluetooth.bluetoothLE.models.writeMessage
import mu.location.savmed.databinding.BleMessageItemLayoutBinding
import mu.location.savmed.databinding.FragmentMessageListBinding

class MessageListFragment : Fragment() {

    companion object {
        const val TAG = "[Message Fragment]"
    }

        private val messageList = mutableListOf<writeMessage>()
        lateinit var binding: FragmentMessageListBinding
        lateinit var adapterMessage: MessageAdapter

        lateinit var bleViewModel: BluetoothLEViewModel

        override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {

            binding = FragmentMessageListBinding.inflate(inflater, container, false)
            bleViewModel = ViewModelProvider(this)[BluetoothLEViewModel::class.java]

            setupRecyclerView()
            return binding.root
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            Log.i(TAG,"updating list of messages ")
            bleViewModel.state.collect { state ->
                Log.i(TAG,"updating list of messages ----- $state ")
                state.listOfMessages?.let { updateMessages(it) }
            }
        }

    }

        private fun setupRecyclerView() {
            adapterMessage = MessageAdapter(messageList)
            binding.rvRecycler.layoutManager = LinearLayoutManager(requireContext())
            binding.rvRecycler.adapter = adapterMessage
        }

        fun updateMessages(newMessages: List<writeMessage>) {
            messageList.clear()
            messageList.addAll(newMessages)
            adapterMessage.notifyDataSetChanged()
        }

}