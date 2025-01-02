package mu.location.savmed.ui.contacts.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import mu.location.savmed.R
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.databinding.FragmentContactProfilePageBinding
import mu.location.savmed.ui.call.viewModels.CurrentCallViewModel
import mu.location.savmed.ui.contacts.models.ContactAvatarModel
import mu.location.savmed.ui.contacts.viewModels.ContactProfileViewModel
import mu.location.savmed.ui.contacts.viewModels.ContactViewModel
import mu.location.savmed.ui.main.SharedMainViewModel

class ContactProfilePage : Fragment() {

    companion object {
        const val TAG = "[Contact profile Page]"
    }

    lateinit var binding: FragmentContactProfilePageBinding

    private val args: ContactProfilePageArgs by navArgs()

    lateinit var contactProfileViewModel: ContactProfileViewModel
    lateinit var sharedMainViewModel: SharedMainViewModel
    lateinit var callViewModel: CurrentCallViewModel

    lateinit var localSipUri: String
    lateinit var remoteSipUri: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activity?.onBackPressedDispatcher?.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                findNavController().popBackStack()
            }
        })

        localSipUri = coreContext.core.defaultAccount?.params?.identityAddress?.username.toString()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentContactProfilePageBinding.inflate(inflater,container,false)

        contactProfileViewModel = requireActivity().run {
            ViewModelProvider.create(this)[ContactProfileViewModel::class.java]
        }
        sharedMainViewModel = requireActivity().run {
            ViewModelProvider.create(this)[SharedMainViewModel::class.java]
        }
        callViewModel = requireActivity().run {
            ViewModelProvider.create(this)[CurrentCallViewModel::class.java]
        }

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = contactProfileViewModel

        binding.editProfilepage.setOnClickListener() {
            findNavController().navigate(ContactProfilePageDirections.actionContactProfilePageToNewOrEditContactFragment(
                contactProfileViewModel.refKey
            ))
        }

        binding.messageIcon.setOnClickListener() {

            if (contactProfileViewModel.sipUserName.value != null) {
                findNavController().navigate(
                    ContactProfilePageDirections.actionContactProfilePageToConversationFragment(
                        localSipUri,
                        contactProfileViewModel.sipUserName.value.toString()
                    )
                )
            } else {
                Toast.makeText(requireContext(),"Not UserName Mentioned Cannot Open Chat!",Toast.LENGTH_LONG).show()
            }
        }

        if (args.freindRefKey != "") {
            contactProfileViewModel.findContact(sharedMainViewModel.displayedFriend,args.freindRefKey)
        }

        binding.voiceCallIcon.setOnClickListener() {
            coreContext.startCall(contactProfileViewModel.sipUserName.value.orEmpty().trim())
        }

        // Inflate the layout for this fragment
        return binding.root
    }

    override fun onDestroy() {
        super.onDestroy()
    }

}