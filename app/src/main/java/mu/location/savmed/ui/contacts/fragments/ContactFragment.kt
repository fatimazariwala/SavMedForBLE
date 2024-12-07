package mu.location.savmed.ui.contacts.fragments

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.WorkerThread
import androidx.core.content.res.ResourcesCompat
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
import mu.location.savmed.ui.chat.chatNew.viewModel.ConversationViewModel
import mu.location.savmed.ui.contacts.adapter.ContactAdapter
import mu.location.savmed.ui.contacts.models.ContactAvatarModel
import mu.location.savmed.ui.contacts.models.ContactEvent
import mu.location.savmed.ui.contacts.models.EndSwitchCallBack
import mu.location.savmed.ui.contacts.viewModels.ContactViewModel
import org.linphone.core.Account
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Friend
import org.linphone.core.FriendList
import org.linphone.core.FriendListListenerStub
import org.linphone.core.MagicSearch
import org.linphone.core.MagicSearchListenerStub
import org.linphone.core.SearchResult

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

    private lateinit var contactCallViewModel : ContactViewModel
    private lateinit var currentCallViewModel: CurrentCallViewModel
    private val currentCallViewModelFactory: CurrentCallViewModelFactory = CurrentCallViewModelFactory(this)

    private lateinit var convViewModel: ConversationViewModel

    private val friendListListener: FriendListListenerStub = object : FriendListListenerStub() {
        @WorkerThread
        override fun onContactCreated(friendList: FriendList, linphoneFriend: Friend) {
            super.onContactCreated(friendList, linphoneFriend)
            Log.i(TAG,"contact createddd ${friendList.displayName}")
            for (f in friendList.friends) {
                Log.i(TAG,"friends name -> ${f.name}")
            }
            submitListItAdapter()
        }

        override fun onContactDeleted(friendList: FriendList, linphoneFriend: Friend) {
            super.onContactDeleted(friendList, linphoneFriend)
            submitListItAdapter()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        emrContactAdapter =  ContactAdapter(
            favourite = true,
            onCallClick = { sipUri -> context?.let { currentCallViewModel.outgoingCall(sipUri, it) } },
            onChatClick = { sipUri -> startChatFragment(sipUri) },
            onInfoClick = { refKey -> contactCallViewModel.displayPreviouslyAddedContact(refKey,false) },
            onRemoveClick = { refKey -> contactCallViewModel.removeContact(refKey) }
        )

        contactAdapter = ContactAdapter(
            favourite = false,
            onCallClick = { sipUri -> context?.let { currentCallViewModel.outgoingCall(sipUri, it) } },
            onChatClick = { sipUri -> startChatFragment(sipUri) },
            onInfoClick = {refKey -> contactCallViewModel.displayPreviouslyAddedContact(refKey,false)},
            onRemoveClick = {refKey -> contactCallViewModel.removeContact(refKey)}
        )

        coreContext.postOnCoreThread { core ->

            magic = core.createMagicSearch()

//            if (core.getFriendListByName(SAVMED_ADDRESS_BOOK_FRIEND_LIST) != null) {
//                core.getFriendListByName(SAVMED_ADDRESS_BOOK_FRIEND_LIST)
//                    ?.addListener(friendListListener)
//            } else {
//                Toast.makeText(requireContext(),"Friend list not found",Toast.LENGTH_SHORT).show()
//            }

            magic.addListener(object : MagicSearchListenerStub() {

                override fun onSearchResultsReceived(magicSearch: MagicSearch) {
                    super.onSearchResultsReceived(magicSearch)

                    for (magic in magicSearch.lastSearch) {
                        Log.i(TAG,"Search Result: ${magic.address?.username}")
                    }
                    coreContext.postOnMainThread {
                        searchResultAdapter.submitList(magicSearch.lastSearch.toList())
                        updateSearchResultsVisibility(magicSearch.lastSearch.toList())
                    }
                }
            })
        }

    }

    private fun updateSearchResultsVisibility(results: List<SearchResult>) {
        if (!results.isNullOrEmpty()) {
            binding.searchResultsCard.visibility = View.VISIBLE
        } else {
            binding.searchResultsCard.visibility = View.GONE
        }
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

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = contactCallViewModel

        setupRecyclerView()
        observeContactEvents()
        submitListItAdapter()

        Log.i(TAG,"yoooo iiiiiimmmm")
        contactCallViewModel.getContactList()

        contactCallViewModel.mrList.observe(viewLifecycleOwner) {
            emrContactAdapter.submitList(it)

            // Wait for adapter to have items before setting it in the RecyclerView,
            // otherwise scroll position isn't retained
            if (binding.favouritesContactsList.adapter != emrContactAdapter) {
                binding.favouritesContactsList.adapter = emrContactAdapter
            }

            for (item in it) {
                Log.i(TAG,"i am fro emrlistz ${item.name.value} ${item.address?.asStringUriOnly()}")
            }

            Log.i(TAG,"emrContacts list updated with [${it.size}] items")
           // listViewModel.fetchInProgress.value = false
        }

        contactCallViewModel.listz.observe(viewLifecycleOwner) {

            val contactList = it.toMutableList()

            Log.i(TAG,"---------.......${contactList.size} ${contactAdapter.currentList.size}")

            contactAdapter.submitList(contactList)

            Log.i(TAG,"---------.aftr submit......${contactList.size} ${contactAdapter.currentList.size}")
            // Wait for adapter to have items before setting it in the RecyclerView,
            // otherwise scroll position isn't retained
            if (binding.contactsRecyclerView.adapter != contactAdapter) {
                Log.i(TAG,"in bind $contactAdapter")
                binding.contactsRecyclerView.adapter = contactAdapter
            }
            //submitListItAdapter()
            for (item in it) {
                Log.i(TAG,"i am fro listz ${item.name.value} ${item.address?.asStringUriOnly()}")
            }
            Log.i(TAG,"Contacts list updated with [${it.size}] items")
            // listViewModel.fetchInProgress.value = false
        }

        contactCallViewModel.searchFilter.observe(viewLifecycleOwner) { search ->

            if (search.isNullOrEmpty()){
                binding.searchResultsCard.visibility = View.GONE
            } else {
                val result = magic.getContactsListAsync(
                    search,
                    "212.38.94.76",
                    MagicSearch.Source.All.toInt(),
                    MagicSearch.Aggregation.None)
            }
        }

        binding.addContacts.setOnClickListener() {
            Log.i(TAG,"Add CLicked!")
            findNavController().navigate(R.id.action_contactFragment_to_newOrEditContactFragment)
        }
//        binding.btnHome.setOnClickListener() {
//            val i = Intent(requireContext(),MainActivity::class.java)
//            startActivity(i)
//            requireActivity().finish()
//        }
//
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

    private fun setupRecyclerView() {

        binding.contactsRecyclerView.apply {
            adapter = contactAdapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)
            addItemDecoration(
                DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
            )
        }

        binding.favouritesContactsList.apply {
            adapter = emrContactAdapter
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            setHasFixedSize(true)
//            addItemDecoration(
//                DividerItemDecoration(requireContext(), DividerItemDecoration.HORIZONTAL)
//            )
        }

        binding.chatListview.setOnClickListener() {
            findNavController().navigate(R.id.action_contactFragment_to_chatListFragment)
        }

        if (coreContext.core.getFriendListByName(
                SAVMED_ADDRESS_BOOK_FRIEND_LIST
            )?.friends?.isNotEmpty() == true) {
            for (data in coreContext.core.getFriendListByName(
                SAVMED_ADDRESS_BOOK_FRIEND_LIST
            )?.friends?.toList()!!) {
                Log.i(TAG,"yooo ${data.address?.username}")
            }
        } else {
            Log.i(
                TAG,"List empty ${
                coreContext.core.getFriendListByName(
                    SAVMED_ADDRESS_BOOK_FRIEND_LIST
                )?.friends?.size}")
        }

        searchResultAdapter = SearchResultAdapter(
            onChatClick = { sipUri -> startNewCallOrChat(sipUri,"chat") },
            onCallClick = {sipUri -> startNewCallOrChat(sipUri,"call")}
        )

        binding.searchResultsRecyclerView.apply {
            adapter = searchResultAdapter
            layoutManager = LinearLayoutManager(requireContext())

            // Set max height programmatically
            val maxHeight = resources.getDimensionPixelSize(R.dimen.search_results_max_height)
            layoutParams = layoutParams.apply {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }

            // Add item decoration for dividers
            addItemDecoration(
                DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL).apply {
                    setDrawable(
                        ResourcesCompat.getDrawable(
                        resources,
                        R.drawable.search_result_divider,
                        null
                    )!!)
                }
            )
        }
    }

    fun startNewCallOrChat(remoteUri: String,Flag: String) {

//        contactCallViewModel.searchFilter.value = null

//        contactCallViewModel.addFriendToList(remoteUri)
        Log.i(TAG,"IN startNew Call or chat")

        when(Flag) {
            "chat" -> context?.let { startChatFragment(remoteUri) }
            "call" -> context?.let { currentCallViewModel.outgoingCall(remoteUri, it) }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG,"Search Value ${contactCallViewModel.searchFilter.value}")
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

    private fun submitListItAdapter() {
        val contacts =  coreContext.core.getFriendListByName(
            SAVMED_ADDRESS_BOOK_FRIEND_LIST
        )?.friends

        val emrList: ArrayList<ContactAvatarModel> = ArrayList()
        val list: ArrayList<ContactAvatarModel> = ArrayList()

        if (contacts != null) {
            for (contact in contacts) {
                if (contact.address != null) {
                    if (contact.starred) {
                        Log.i(TAG, "In emt list----")
                        emrList.add(
                            coreContext.contactsManager.getContactAvatarModelForAddress(
                                contact.address
                            )
                        )
                    } else {
                        Log.i(TAG, "in list-----${contact.address?.asStringUriOnly()}-")
                        list.add(coreContext.contactsManager.getContactAvatarModelForAddress(contact.address))
                    }
                } else {
                    contact.remove()
                    Log.i(TAG,"Null contact address of ${contact.refKey} Removing it")
                }
            }
        } else {
            Log.i(TAG,"Contact List EMpty")
        }

        for (data in emrList) {
            Log.i(TAG,"i am emr list data ${data.address?.username} ${data.name} ${data.firstLetter} ${data.id}")
        }
        for (data in list) {
            Log.i(TAG,"i am ooo list data ${data.address?.username} ${data.name.value} ${data.firstLetter} ${data.id}")
        }
        emrContactAdapter.submitList(emrList)
        contactAdapter.submitList(list)
    }
    private fun observeContactEvents() {
        contactCallViewModel.contactEvent
            .onEach { result ->
                when (result) {

                    ContactEvent.ContactRemoved -> {
                        Toast.makeText(requireContext(), "Contact Successfully Deleted!!", Toast.LENGTH_SHORT).show()
                        contactCallViewModel.getContactList()
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

                    ContactEvent.ContactCreated -> {

                    }

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