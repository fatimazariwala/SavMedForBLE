package mu.location.savmed.ui.contacts.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import mu.location.savmed.R
import mu.location.savmed.databinding.FragmentNewOrEditContactBinding
import mu.location.savmed.ui.contacts.models.ContactEvent
import mu.location.savmed.ui.contacts.viewModels.ContactViewModel
import mu.location.savmed.utils.FileUtils

class NewOrEditContactFragment : Fragment() {

    companion object{
        const val TAG = "[Add/Edit Fragment]"
    }

    private val args: NewOrEditContactFragmentArgs by navArgs()

    //for email field
    private lateinit var addEmailButton: Button
    private lateinit var emailsContainer: LinearLayout
    private var emailCount = 0  // To track number of email fields

    //for address field
    private lateinit var addAddressButton: Button
    private lateinit var addressesContainer: LinearLayout
    private var addressCount = 0  // To track number of address fields

    private var _binding : FragmentNewOrEditContactBinding ?= null
    private val binding get() = _binding!!

    lateinit var contactViewModel: ContactViewModel
    var addContactState = false

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if(uri != null) {
            Log.i(TAG,"Picture Picked [$uri]")
            val localFileName = FileUtils.getFileStorageCacheDir(
                ContactViewModel.TEMP_PICTURE_NAME
            )
            lifecycleScope.launch {
                if (FileUtils.copyFile(uri,localFileName)) {
                    val newFile = FileUtils.getProperFilePath(
                        localFileName.absolutePath
                    )
                    Log.i(TAG,"Copied File To [$newFile]")
                    contactViewModel.picturePath.value = newFile
                } else {
                    Log.e(TAG,"Failed To copy File from [$uri] to [${localFileName.absolutePath}]")
                }
            }
        } else {
            Log.w(TAG,"No Picture Picked")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                findNavController().navigate(R.id.action_newOrEditContactFragment_to_contactFragment)
            }
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentNewOrEditContactBinding.inflate(inflater,container,false)

        contactViewModel = requireActivity().run {
            ViewModelProvider(this)[ContactViewModel::class.java]
        }

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = contactViewModel

        observeContactEvents()

        val refKey = args.friendRefKey
        if (args.friendRefKey != "") {
            contactViewModel.findFriendByRefKey(refKey)
        }

        // Access views using ViewBinding instead of findViewById initialise email button and its container
        addEmailButton = binding.addEmailButton
        emailsContainer = binding.emailsContainer

        // Set click listener on the "Add Email" button
        binding.addEmailButton.setOnClickListener {
            addEmailField()
        }
        //End Email Added Code


        //start address filed code
        // Initialize the "Add Address" button and addresses container
        addAddressButton = binding.addAddressButton
        addressesContainer = binding.addressesContainer

        // Set the initial click listener for adding address input fields
        binding.addAddressButton.setOnClickListener {
            addAddressField()
        }

        binding.deleteButton.setOnClickListener() {
            contactViewModel.picturePath.value = ""
        }
        //end address filed code

        binding.addProfileImage.setOnClickListener() {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        binding.buttonSubmit.setOnClickListener() {
            Log.i(TAG,"NAme,,,,, ${binding.firstName.text} [${contactViewModel.lastName.value}")
            contactViewModel.saveChanges()
        }

        // Inflate the layout for this fragment
        return binding.root
    }

    private fun addEmailField() {
        emailCount++

        // Create a new TextInputLayout for the new email field
        val newEmailInputLayout = layoutInflater.inflate(R.layout.email_input_layout, emailsContainer, false) as LinearLayout
        val newEmailEditText = newEmailInputLayout.findViewById<EditText>(R.id.emailEditText)
        newEmailEditText.hint = "Email $emailCount"  // Set unique hint for each field

        // Get reference to the delete icon for this email field
        val deleteEmailIcon = newEmailInputLayout.findViewById<ImageView>(R.id.deleteEmailIcon)

        // Show the delete icon next to the email input field
        deleteEmailIcon.visibility = View.VISIBLE

        // Set up delete icon click listener
        deleteEmailIcon.setOnClickListener {
            emailsContainer.removeView(newEmailInputLayout)  // Remove the specific email input layout
            emailCount--  // Decrease the email count
            checkAddEmailButtonVisibility()  // Check if "Add Email" button should be shown
        }

        // Add the new email input field to the container
        emailsContainer.addView(newEmailInputLayout)

        // Show the "Add Another Email" button below the new email field
        val addEmailButtonBelow = newEmailInputLayout.findViewById<Button>(R.id.addEmailButtonBelow)
        addEmailButtonBelow.visibility = View.GONE

        // Set the button click listener to add another email
        addEmailButtonBelow.setOnClickListener {
            addEmailField()  // Recursively call to add another email field
        }

        // Hide the original "Add Email" button after the first field is added
        addEmailButton.visibility = View.GONE

    }

    // Method to check if the "Add Email" button should be visible or not
    private fun checkAddEmailButtonVisibility() {
        if (emailsContainer.childCount == 0) {
            addEmailButton.visibility = View.VISIBLE  // Show the "Add Email" button if no fields left
        }
    }

    private fun addAddressField() {
        addressCount++

        // Inflate the new address input layout
        val newAddressInputLayout = layoutInflater.inflate(R.layout.address_input_layout, addressesContainer, false) as LinearLayout

        // Get reference to the new EditText (address field)
        val newAddressEditText = newAddressInputLayout.findViewById<EditText>(R.id.addressEditText)

        // Set unique hint for each address input
        newAddressEditText.hint = "Address $addressCount"

        // Get reference to the delete icon for this address field
        val deleteAddressIcon = newAddressInputLayout.findViewById<ImageView>(R.id.deleteAddressIcon)

        // Show the delete icon next to the address input field
        deleteAddressIcon.visibility = View.VISIBLE

        // Set up delete icon click listener
        deleteAddressIcon.setOnClickListener {
            addressesContainer.removeView(newAddressInputLayout)  // Remove the specific address input layout
            addressCount--  // Decrease the address count
            checkAddAddressButtonVisibility()  // Check if "Add Address" button should be shown
        }

        // Add the new address field to the container
        addressesContainer.addView(newAddressInputLayout)

        // Show the "Add Another Address" button below the new address field
        val addAddressButtonBelow = newAddressInputLayout.findViewById<Button>(R.id.addAddressButtonBelow)
        addAddressButtonBelow.visibility = View.VISIBLE

        // Set the button click listener to add another address
        addAddressButtonBelow.setOnClickListener {
            addAddressField()  // Recursively call to add another address field
        }

        // Hide the original "Add Address" button after the first field is added
        addAddressButton.visibility = View.GONE
    }

    // Method to check if the "Add Address" button should be visible or not
    private fun checkAddAddressButtonVisibility() {
        if (addressesContainer.childCount == 0) {
            addAddressButton.visibility = View.VISIBLE  // Show the "Add Address" button if no fields left
        }
    }


    private fun observeContactEvents() {
        contactViewModel.contactEvent
            .onEach { result ->
                when (result) {

                    ContactEvent.ContactCreated -> {
                        Toast.makeText(requireContext(),"Contact Successfully Created!",Toast.LENGTH_SHORT).show()
                        findNavController().navigate(R.id.action_newOrEditContactFragment_to_contactFragment)
                    }

                    ContactEvent.EmptyField -> {
                        Toast.makeText(requireContext(),"Please Filled All Required Fields!", Toast.LENGTH_LONG).show()
                    }

                    ContactEvent.ContactEdited -> {
                        //contactViewModel.getContactList()
                        Toast.makeText(requireContext(),"Contact Successfully Edited!",Toast.LENGTH_SHORT).show()
                        findNavController().navigate(R.id.action_newOrEditContactFragment_to_contactFragment)
                    }

                    is ContactEvent.ContactError -> {
                        Log.i(TAG, "Error: ${result.message}")
                        if (result.message == "existing_found") {
                            showSplashDialog("Existing Contact Found! Please Add a different Username.")
                        }
                    }

                    is ContactEvent.ContactNotFound -> {
                        Toast.makeText(
                            requireContext(),
                            "Contact Not Found: ${result.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    else -> { }
                }
            }
            .catch { throwable ->
                Log.e(TAG, "Error: $throwable")
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun showSplashDialog(message: String) {
        Log.i(TAG, "in splash")

        val dialogBuilder = AlertDialog.Builder(requireContext())
        val result = CompletableDeferred<Boolean>()

        dialogBuilder.setMessage(message)
            .setCancelable(false)
            .setPositiveButton("OK") { dialog, _ ->
                //contactViewModel.isEdit = false
                findNavController().navigate(R.id.action_newOrEditContactFragment_to_contactFragment)
                dialog.dismiss()
                result.complete(true)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
               // findNavController().navigate(R.id.newOrEditContactFragment)
                dialog.dismiss()
                result.complete(false)
            }

        val alert = dialogBuilder.create()
        alert.show()

        alert.window?.setLayout(800, 400)

        alert.setOnShowListener {
            alert.getButton(AlertDialog.BUTTON_POSITIVE).postDelayed({
                alert.dismiss()
                result.complete(false)
            }, 2000)
        }

        lifecycleScope.launch {
            val dialogResult = result.await()
            // Handle the result if needed
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        contactViewModel.firstName.postValue("")
        contactViewModel.lastName.postValue("")
        contactViewModel.sipUri.postValue("")
        contactViewModel.organization.postValue("")
        contactViewModel.jobTitle.postValue("")
        contactViewModel.picturePath.postValue("")
    }
}