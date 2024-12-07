package mu.location.savmed.ui.contacts.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import mu.location.savmed.R
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.databinding.FragmentContactProfilePageBinding
import mu.location.savmed.ui.contacts.viewModels.ContactViewModel

class ContactProfilePage : Fragment() {

    companion object {
        const val TAG = "[Contact profile Page]"
    }

    lateinit var binding: FragmentContactProfilePageBinding

    private val args: ContactProfilePageArgs by navArgs()

    lateinit var contactViewModel: ContactViewModel
    lateinit var localSipUri: String
    lateinit var remoteSipUri: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activity?.onBackPressedDispatcher?.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (args.freindRefKey == "") {
                    findNavController().navigate(R.id.action_contactProfilePage_to_contactFragment)
                    contactViewModel.firstName.postValue("")
                    contactViewModel.lastName.postValue("")
                    contactViewModel.sipUri.postValue("")
                    contactViewModel.organization.postValue("")
                    contactViewModel.jobTitle.postValue("")
                    contactViewModel.picturePath.postValue("")
                } else {
                    findNavController().navigate(ContactProfilePageDirections.actionContactProfilePageToConversationFragment(
                        localSipUri,
                        "sip:${remoteSipUri}212.38.94.76"
                    ))
                }
            }
        })

        localSipUri = coreContext.core.defaultAccount?.params?.identityAddress?.username.toString()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentContactProfilePageBinding.inflate(inflater,container,false)

        contactViewModel = requireActivity().run {
            ViewModelProvider.create(this)[ContactViewModel::class.java]
        }

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = contactViewModel

        contactViewModel.sipUri.observe(viewLifecycleOwner){ sipUri ->
            remoteSipUri = sipUri
        }

        binding.editProfilepage.setOnClickListener() {
            contactViewModel.isEdit = true
            findNavController().navigate(R.id.action_contactProfilePage_to_newOrEditContactFragment)
        }

        binding.messageIcon.setOnClickListener() {
            findNavController().navigate(
                ContactProfilePageDirections.actionContactProfilePageToConversationFragment(
                    localSipUri,
                    contactViewModel.sipUri.value.toString()
                )
            )
        }

        if (args.freindRefKey != "") {
            contactViewModel.displayPreviouslyAddedContact(args.freindRefKey,false)
        }

        binding.voiceCallIcon.setOnClickListener() {
            coreContext.startCall(contactViewModel.sipUri.value.toString())
        }

        // Inflate the layout for this fragment
        return binding.root
    }

    override fun onDestroy() {
        super.onDestroy()
    }

}