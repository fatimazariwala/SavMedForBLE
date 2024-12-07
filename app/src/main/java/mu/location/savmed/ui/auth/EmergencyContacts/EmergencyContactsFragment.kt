package mu.location.savmed.ui.auth.EmergencyContacts

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import mu.location.savmed.R
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.databinding.EmergencyContactItemsBinding
import mu.location.savmed.databinding.FragmentEmergencyContactsBinding
import mu.location.savmed.ui.contacts.models.EndSwitchCallBack
import mu.location.savmed.ui.call.RvAdapterEmr

class EmergencyContactsFragment : Fragment(), EndSwitchCallBack {

    companion object {
        const val TAG = "[Emergency Contact]"
    }

    lateinit var binding: FragmentEmergencyContactsBinding
    lateinit var emergencyUpdateBinding: EmergencyContactItemsBinding

    lateinit var userName: String

    val emergencyContactItems = mutableListOf<View>()

    private lateinit var viewModel: EmergencyContactsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        binding = FragmentEmergencyContactsBinding.inflate(inflater,container,false)
        viewModel = ViewModelProvider(this).get(EmergencyContactsViewModel::class.java)

        userName = try {
            coreContext.core.defaultAccount?.params?.identityAddress?.username.toString()
        } catch (e: Exception) {
            Log.w(TAG,e.message.toString())
            ""
        }

        viewModel.contactsList.observe(viewLifecycleOwner, Observer { contacts ->
            Log.i(TAG,contacts.toString())
            binding.rvMain.apply {
                val rvAdapter = RvAdapterEmr(contacts,this@EmergencyContactsFragment)
                adapter = rvAdapter
                layoutManager = LinearLayoutManager(requireContext())
            }
            for (contact in contacts) {
                if (!coreContext.emrContact.contains(contact)) {
                    coreContext.emrContact.add(contact) // Explicitly add the contact
                }
                Log.i(TAG,coreContext.emrContact.size.toString())
                for(contactz in coreContext.emrContact) {
                    Log.i(TAG,"EMR OCntacts= ${contactz}")
                }
            }
        })

        viewModel.postContactsStatus.observe(viewLifecycleOwner, Observer { stat ->
            if(stat) {
                Toast.makeText(
                    requireContext(),
                    "EMR Contacts Updated!",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    requireContext(),
                    "EMR Contacts Update Failed!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        // Call the getEmergencyContacts function with the username
        viewModel.getEmergencyContacts(userName)



        binding.addButton.setOnClickListener() {
            val dialog = BottomSheetDialog(requireContext())
            emergencyUpdateBinding = EmergencyContactItemsBinding.inflate(inflater,null,false)

            val categories = arrayOf("home", "office", "others")
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categories)
            val autoCompleteTextView = emergencyUpdateBinding.autoCompleteTextView

            autoCompleteTextView.setAdapter(adapter)

            autoCompleteTextView.setOnClickListener {
                autoCompleteTextView.showDropDown()
            }

            val emergencyContactList = mutableListOf<EmergencyContact>()

            emergencyUpdateBinding.save.setOnClickListener() {

                val emrContact = EmergencyContact(
                    emergencyUpdateBinding.emrName.text.toString(),
                    emergencyUpdateBinding.autoCompleteTextView.text.toString()
                )
                emergencyContactList.add(emrContact)

                Log.i(TAG,"From click ${emrContact.contact}")
                viewModel.postEmergencyContacts(
                    EmergencyContacts(
                        userName = userName,
                        emergencyContacts = emergencyContactList
                    )
                )
                dialog.dismiss()
                findNavController().navigate(R.id.action_emergency_contacts_self)
            }

            emergencyUpdateBinding.bottomSheetCloseButton.setOnClickListener() {
                dialog.dismiss()
            }

            viewModel.getEmergencyContacts(userName)
            dialog.setCancelable(false)
            dialog.setContentView(emergencyUpdateBinding.root)
            dialog.show()
        }

        // Inflate the layout for this fragment
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Button>(R.id.btnHome).setOnClickListener() {
            findNavController().navigate(R.id.action_emergency_contacts_to_rippleFragment)
        }
    }

//    override fun outgoingCall(remoteUri: String) {
//        val lat = coreContext.onLocationEvent["latitude"] ?: 0.0
//        val lon = coreContext.onLocationEvent["longitude"] ?: 0.0
//        var address = ""
//        lifecycleScope.launch {
//
//            val geocoder = Geocoder(requireContext())
//            try {
//                val addresses = geocoder.getFromLocation(lat, lon, 1)
//                address = addresses!![0].getAddressLine(0)
//            } catch (e: Exception) {
//                address = "Unable to fetch address"
//            }
//
//            val gson = Gson();
//            var LocJson = gson.toJson(
//                locationData(
//                    lat,
//                    lon,
//                    0,
//                    address,
//                    coreContext.core.defaultAccount?.params?.identityAddress?.username.toString(),
//                    remoteUri.trim(),
//                )
//            );
//            Log.i(ContactFragment.TAG, LocJson);
//
//            val call: Call<locationData?>? = try {
//
//                RetrofitInstance.apiLocation.postLocationData(
//                    locationData(
//                        coreContext.onLocationEvent["latitude"] ?: 0.0,
//                        coreContext.onLocationEvent["longitude"] ?: 0.0,
//                        0, address,
//                        coreContext.core.defaultAccount?.params?.identityAddress?.username.toString(),
//                        remoteUri.trim(),
//                    )
//                )
//
//            } catch (e: IOException) {
//                Log.i(ContactFragment.TAG, e.message.toString())
//                return@launch
//            } catch (e: HttpException) {
//                Log.i(ContactFragment.TAG, e.message.toString())
//                return@launch
//            }
//
//            call?.enqueue(object: Callback<locationData?> {
//                override fun onResponse(
//                    call: Call<locationData?>,
//                    response: Response<locationData?>
//                ) {
//                    val responz = response.body()
//                    Log.i(ContactFragment.TAG,"Response : ${responz?.Latitude},${responz?.Longitude},${responz?.sqlStatus},${responz?.ReceiveruserName}")
//                }
//
//                override fun onFailure(
//                    call: Call<locationData?>,
//                    t: Throwable
//                ) {
//                    Log.i(ContactFragment.TAG,"Response : Failure ${t.message}")
//                }
//            })
//        }
//        coreContext.postOnCoreThread {
//            coreContext.startCall(remoteUri.trim())
//        }
//        val i =Intent(requireContext(),CallActivity::class.java)
//        startActivity(i)
//        requireActivity().finish()
//    }
//
//    override fun startChat(remoteUri: String) {
//        // Not Needed
//    }

    override fun switchToOutgoingCallFragment() {
        TODO("Not yet implemented")
    }

    override fun endMainActivity() {
        TODO("Not yet implemented")
    }
}