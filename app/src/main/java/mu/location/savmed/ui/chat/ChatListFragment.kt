package mu.location.savmed.ui.chat

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import mu.location.savmed.databinding.FragmentChatListBinding
import mu.location.savmed.ui.chat.Adapters.ConversationsListAdapter
import mu.location.savmed.ui.chat.viewModel.ConversationViewModel
import mu.location.savmed.ui.chat.viewModel.CoversationListViewModel
import org.linphone.core.tools.Log


class ChatListFragment : Fragment() {

    companion object {
        const val TAG = "[ChatList Fragment]"
    }

    lateinit var binding : FragmentChatListBinding

    lateinit var listViewModel: CoversationListViewModel

    lateinit var conversationViewModel: ConversationViewModel

    lateinit var adapter: ConversationsListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = ConversationsListAdapter()

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        binding = FragmentChatListBinding.inflate(inflater,container,false)
        return binding.root
    }

    private val dataObserver = object : RecyclerView.AdapterDataObserver() {
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            Log.i(TAG,"[$itemCount] added, scrolling to top")
            binding.conversationsList.scrollToPosition(0)
        }

        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
            Log.i("$TAG [$itemCount] moved, scrolling to top")
            binding.conversationsList.scrollToPosition(0)
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) { }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listViewModel = ViewModelProvider(this)[CoversationListViewModel::class.java]
        conversationViewModel = ViewModelProvider(this)[ConversationViewModel::class.java]

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = listViewModel

        binding.conversationsList.setHasFixedSize(true)
        binding.conversationsList.layoutManager = LinearLayoutManager(requireContext())
        binding.conversationsList.clipToOutline = true

        adapter.conversationClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                val localUserName: String = model.chatRoom.localAddress.username.toString()
                val remoteUserName: String = model.chatRoom.peerAddress.username.toString()
                conversationViewModel.chatRoom = model.chatRoom
                Log.i(TAG,"YOO conv ---- ${conversationViewModel.chatRoom.peerAddress.asStringUriOnly()}")
                //conversationViewModel.findChatOrCreateRoom(model.chatRoom,localUserName,remoteUserName)
                findNavController().navigate(
                    ChatListFragmentDirections.actionChatListFragmentToConversationFragment(
                        localUserName,
                        remoteUserName
                    )
                )
            }
        }

        listViewModel.conversations.observe(viewLifecycleOwner) {
            adapter.submitList(it)

            // Wait for adapter to have items before setting it in the RecyclerView,
            // otherwise scroll position isn't retained
           // if (binding.conversationsList.adapter != adapter) {
            binding.conversationsList.adapter = adapter
          //  }

            Log.i("$TAG Conversations list ready with [${it.size}] items")
            listViewModel.fetchInProgress.value = false
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            adapter.registerAdapterDataObserver(dataObserver)
        } catch (e: IllegalStateException) {
            Log.e("$TAG Failed to unregister data observer to adapter: $e")
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            adapter.unregisterAdapterDataObserver(dataObserver)
        } catch (e: IllegalStateException) {
            Log.e("$TAG Failed to unregister data observer to adapter: $e")
        }
    }

}