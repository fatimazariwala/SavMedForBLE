package mu.location.savmed.ui.contacts.fragments

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import mu.location.savmed.R
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.databinding.FragmentContactBinding
import mu.location.savmed.ui.call.Adapters.SearchResultAdapter
import mu.location.savmed.ui.call.viewModelFactory.CurrentCallViewModelFactory
import mu.location.savmed.ui.call.viewModels.CurrentCallViewModel
import mu.location.savmed.ui.chat.viewModel.ConversationViewModel
import mu.location.savmed.ui.contacts.adapter.ContactAdapter
import mu.location.savmed.ui.contacts.models.ContactEvent
import mu.location.savmed.ui.contacts.models.EndSwitchCallBack
import mu.location.savmed.ui.contacts.viewModels.ContactViewModel
import mu.location.savmed.ui.contacts.viewModels.ContactViewModel.Companion
import mu.location.savmed.ui.main.SharedMainViewModel
import org.linphone.core.Friend
import org.linphone.core.FriendList
import org.linphone.core.MagicSearch

class ContactFragment : Fragment(), EndSwitchCallBack {

    companion object{
        const val TAG = "[Contact Fragment]"
        const val SAVMED_ADDRESS_BOOK_FRIEND_LIST = "SavMed Contact List"
    }

    private var _binding : FragmentContactBinding ?= null
    private val binding get() = _binding!!
    var address = ""
    var lat = 0.0
    var lon = 0.0
    var detectContactFalseSet = false
    private lateinit var magic: MagicSearch

    var addFriendStatus : FriendList.Status? = null
    lateinit var searchResultAdapter: SearchResultAdapter
    lateinit var contactAdapter: ContactAdapter
    lateinit var emrContactAdapter: ContactAdapter

    lateinit var sharedMainViewModel: SharedMainViewModel

    private lateinit var contactCallViewModel : ContactViewModel
    private lateinit var currentCallViewModel: CurrentCallViewModel
    private val currentCallViewModelFactory: CurrentCallViewModelFactory = CurrentCallViewModelFactory(this)

    private lateinit var convViewModel: ConversationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        emrContactAdapter =  ContactAdapter(
            favourite = true,
            onCallClick = { sipUri -> context?.let { currentCallViewModel.initializeWebSocket(sipUri, it) } },
            onChatClick = { sipUri -> startChatFragment(sipUri) },
            onInfoClick = { friend,refKey -> checkOutForProfilePage(friend,refKey) },
            onRemoveClick = { model -> contactCallViewModel.deleteContact(model) }
        )

        contactAdapter = ContactAdapter(
            favourite = false,
            onCallClick = { sipUri -> context?.let { currentCallViewModel.initializeWebSocket(sipUri, it) } },
            onChatClick = { sipUri -> startChatFragment(sipUri) },
            onInfoClick = {friend,refKey -> checkOutForProfilePage(friend,refKey)},
            onRemoveClick = {model -> contactCallViewModel.deleteContact(model)}
        )

    }

    fun checkOutForProfilePage(friend: Friend, key: String) {
        sharedMainViewModel.displayedFriend = friend
        findNavController().navigate( ContactFragmentDirections.actionContactFragmentToContactProfilePage(
                key
            )
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentContactBinding.inflate(inflater,container, false)

        contactCallViewModel = requireActivity().run {
            ViewModelProvider(this)[ContactViewModel ::class.java]
        }

        currentCallViewModel = requireActivity().run {
            ViewModelProvider(this,currentCallViewModelFactory).get(CurrentCallViewModel::class.java)
        }

        sharedMainViewModel = requireActivity().run {
            ViewModelProvider(this)[SharedMainViewModel::class.java]
        }

        val friendList = coreContext.core.getFriendListByName(ContactViewModel.SAVMED_ADDRESS_BOOK_FRIEND_LIST)
        if (friendList != null) {
            Log.i(TAG,"Updating friend list subscriptions!!")
            friendList.updateSubscriptions()
        } else {
            Log.i(TAG,"No FriendList found!!")
        }

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = contactCallViewModel

        binding.contactsRecyclerView.setHasFixedSize(true)
        binding.contactsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.contactsRecyclerView.clipToOutline = true
        binding.contactsRecyclerView.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )
        binding.contactsRecyclerView.adapter = contactAdapter

        binding.favouritesContactsList.setHasFixedSize(true)
        binding.favouritesContactsList.layoutManager = LinearLayoutManager(requireContext(),LinearLayoutManager.HORIZONTAL,false)
        binding.favouritesContactsList.clipToOutline = true
        binding.favouritesContactsList.adapter = emrContactAdapter

        observeContactEvents()

        binding.chatListview.setOnClickListener() {
            findNavController().navigate(R.id.action_contactFragment_to_chatListFragment)
        }

        binding.addContacts.setOnClickListener() {
            Log.i(TAG,"Add CLicked!")
//            contactCallViewModel.findFriendByRefKey("")
            findNavController().navigate(ContactFragmentDirections.actionContactFragmentToNewOrEditContactFragment(
                ""
            ))
        }

        contactCallViewModel.listz.observe(viewLifecycleOwner) {
            contactAdapter.submitList(it)

//            if (binding.contactsRecyclerView.adapter != contactAdapter) {
//                Log.i(TAG,"in bind adapter to contact...")
                binding.contactsRecyclerView.adapter = contactAdapter
            //}

            Log.i(TAG,"Contacts List Updated with [${it.size}] [${contactAdapter.itemCount}] data")
            contactCallViewModel.fetchInProgress.value = false
        }

        contactCallViewModel.mrList.observe(viewLifecycleOwner) {
            emrContactAdapter.submitList(it)

            if (binding.contactsRecyclerView.adapter != emrContactAdapter) {
                Log.i(TAG,"in bind emr adapter to contact...")
                binding.favouritesContactsList.adapter = emrContactAdapter
            }

            Log.i(TAG,"Contacts EMr List Updated with [${it.size}] data")
            contactCallViewModel.fetchInProgress.value = false
        }

        contactCallViewModel.searchFilter.observe(viewLifecycleOwner) { search ->

            if (search.isNullOrEmpty()){
                binding.searchResultsCard.visibility = View.GONE
                contactCallViewModel.applyFilter(
                    filter = "",
                    isContactListFilter = false
                )
            } else {
                contactCallViewModel.applyFilter(
                    filter = search,
                    isContactListFilter = false
                )
            }
        }

        return binding.root
    }

    override fun onStart() {
        super.onStart()
    }

    private fun startChatFragment(remoteSipUri:String) {
        lateinit var localSipUri: String
        if (coreContext.core.defaultAccount?.params?.identityAddress?.username != null) {
            localSipUri = coreContext.core.defaultAccount?.params?.identityAddress?.username!!
        }
        findNavController().navigate( ContactFragmentDirections.actionContactFragmentToConversationFragment(
                localSipUri,"sip:${remoteSipUri}@212.38.94.76"
            )
        )
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG,"Search Value -----${contactCallViewModel.searchFilter.value}")
        if(contactCallViewModel.searchFilter.value?.isEmpty() == true) {
            binding.searchResultsCard.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    override fun switchToOutgoingCallFragment() {
        findNavController().navigate(R.id.outgoingCallFragment)
    }

    override fun endMainActivity() {
        requireActivity().finish()
    }

    private fun observeContactEvents() {
        contactCallViewModel.contactEvent
            .onEach { result ->
                when (result) {

                    ContactEvent.ContactRemoved -> {
                        Toast.makeText(requireContext(), "Contact Successfully Deleted!!", Toast.LENGTH_SHORT).show()
                       // contactCallViewModel.getContactList()
                    }

                    is ContactEvent.ContactError -> {
                        Log.e(TAG, "Error: ${result.message}")
                    }

                    is ContactEvent.ContactNotFound -> {
                        Toast.makeText(
                            requireContext(),
                            "Contact Not Found: ${result.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    ContactEvent.ContactEditFound -> {
                        Log.i(TAG,"Friend Found....")
                        findNavController().navigate(
                            ContactFragmentDirections.actionContactFragmentToContactProfilePage(
                            ""
                        ))
                    }
                    ContactEvent.ContactCreated -> { }
                    else -> { }
                }
            }
            .catch { throwable ->
                Log.e(TAG, "Error: $throwable")
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onPause() {
        super.onPause()
    }

}