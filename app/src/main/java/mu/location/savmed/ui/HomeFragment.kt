package mu.location.savmed.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mu.location.savmed.MainActivity
import mu.location.savmed.R
//import mu.location.savmed.SavMed.Companion.bluetoothController
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.bluetooth.bluetoothClassic.uiModel.BluetoothViewModel
import mu.location.savmed.bluetooth.bluetoothClassic.uiModel.BluetoothViewModelFactory
import mu.location.savmed.bluetooth.bluetoothClassic.uiModel.DeviceAdapter
import mu.location.savmed.databinding.BottomSheetDialogBinding
import mu.location.savmed.databinding.FragmentHomeBinding
import mu.location.savmed.ui.auth.LoginActivity
import mu.location.savmed.ui.auth.RegistrationActivity
import mu.location.savmed.ui.call.CallActivity
import mu.location.savmed.ui.medical.MedicalInfoActivity
import mu.location.savmed.utils.ActivityHolder
import mu.location.savmed.utils.SharedPreference

class HomeFragment : Fragment() {

    lateinit var binding: FragmentHomeBinding
    lateinit var diaLogBinding: BottomSheetDialogBinding

   // val viewModelFactory = BluetoothViewModelFactory(bluetoothController)
   // lateinit var bluetoothViewModel: BluetoothViewModel

    // Initialize Bluetooth Manager Class
    private val bluetoothManager by lazy {
        requireActivity().getSystemService(BluetoothManager::class.java)
    }

    // get Device Bluetooth adapter through Bluetooth Manager
    private val bluetoothAdapter by lazy {
        bluetoothManager?.adapter
    }

//    val enableBluetoothLauncher = registerForActivityResult(
//        ActivityResultContracts.StartActivityForResult()
//    ) {
//        bluetoothViewModel.startScan()
//    }

    // Check is Bluetooth is Currently Enabled
    private val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    // Permission Launcher
    private val permissionCheck =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            /**No Action Needed**/
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        binding = FragmentHomeBinding.inflate(inflater,container,false)
        SharedPreference.init(requireContext())

        //bluetoothViewModel = ViewModelProvider(this, viewModelFactory)[BluetoothViewModel::class.java]

//        binding.viewNearBy.setOnClickListener() {
//            findNavController().navigate(R.id.action_homeFragment_to_nearByFragment)
//        }
//
//        binding.ecbtn.setOnClickListener() {
//            findNavController().navigate(R.id.action_homeFragment_to_emergency_contacts)
//        }
//
//        binding.appname.setOnClickListener() {
//            findNavController().navigate(R.id.action_homeFragment_to_rippleFragment)
//        }

        binding.idBtnShowBottomSheet.setOnClickListener() {
           // findNavController().navigate(R.id.action_homeFragment_to_bleNearByFragment)
            val dialog = BottomSheetDialog(requireContext())
            diaLogBinding = BottomSheetDialogBinding.inflate(inflater,null,false)

            diaLogBinding.pairedDevices.setOnClickListener() {
                diaLogBinding.paired.visibility = View.VISIBLE
                diaLogBinding.scanned.visibility = View.GONE
            //    displayDevices("PAIRED")
            }
            diaLogBinding.scannedDevices.setOnClickListener() {
                diaLogBinding.paired.visibility = View.GONE
                diaLogBinding.scanned.visibility = View.VISIBLE
              //  displayDevices("SCANNED")
            }

            diaLogBinding.bottomSheetCloseButton.setOnClickListener() {
                dialog.dismiss()
            }

            dialog.setCancelable(false)
            dialog.setContentView(diaLogBinding.root)
            dialog.show()
        }

        // Calling Activity Button
        binding.Siploginbutton.setOnClickListener {
            val intent = Intent(requireActivity(), CallActivity::class.java)
            startActivity(intent)
            requireActivity().finish()
        }

        // Location Activity Button
//        binding.locationBtn.setOnClickListener() {
//            val intent = Intent(requireActivity(), LocationActivity::class.java)
//            startActivity(intent)
//            requireActivity().finish();
//        }

        // Logout Button
        binding.idBtnLogOut.setOnClickListener {
            coreContext.postOnCoreThread { core ->

                val account = core.defaultAccount
                account ?: return@postOnCoreThread
                core.removeAccount(account)
                core.clearAccounts()
                core.clearAllAuthInfo()
            }
            SharedPreference.clear()

            Log.i("SIPService", "Logged out and data cleared.")
            val i = Intent(requireContext(), MainActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(i)
            requireActivity().finish()
        }

        if (SharedPreference.username.isNullOrEmpty()) {
            binding.usernotloggedin.visibility = View.VISIBLE
            binding.btnHeathInfo.visibility = View.GONE
            binding.userloggedin.visibility = View.GONE

            binding.gotoRegist.setOnClickListener() {
                val intent = Intent(requireActivity(), RegistrationActivity::class.java)
                startActivity(intent)
                requireActivity().finish();
            }

            binding.loginBtn.setOnClickListener() {
                val intent = Intent(requireActivity(), LoginActivity::class.java)
                startActivity(intent)
                requireActivity().finish();
            }
        } else {
            binding.usernotloggedin.visibility = View.GONE
            binding.userloggedin.visibility = View.VISIBLE
            binding.idTVUserName.setText("Welcome ${SharedPreference.username}")
            binding.btnHeathInfo.visibility = View.VISIBLE

            binding.btnHeathInfo.setOnClickListener() {
                //stopService(Sipservice)
                val intent = Intent(requireContext(), MedicalInfoActivity::class.java)
                startActivity(intent)
                requireActivity().finish();
            }
        }
            // Inflate the layout for this fragment
        return binding.root
    }

//    private fun displayDevices(deviceType: String) {
//
//        if ( bluetoothController.hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT)) {
//            if (bluetoothAdapter != null) {
//
//                if(isBluetoothEnabled) {
//                    bluetoothController.updatePairedDevices()
//
//                    when(deviceType) {
//                        "PAIRED" -> {
//                            Log.i("[BOTTOM VIEW]","PAIRED")
//
//                            lifecycleScope.launch(Dispatchers.Main) {
//                                bluetoothViewModel.state.collect { state ->
//                                    val adapter = DeviceAdapter(
//                                        requireContext(),
//                                        state.pairedDevices,
//                                        onClickListener = { device ->
//                                            bluetoothViewModel.connectToDevice(device)
//                                        })
//                                    diaLogBinding.listViewPairedDevices.adapter = adapter
//                                    for (device in state.pairedDevices) {
//                                        Log.i("[paired device]",device.address)
//                                    }
//                                }
//                            }
//                        }
//                        "SCANNED" -> {
//                            if(bluetoothController.hasPermission(Manifest.permission.BLUETOOTH_SCAN) &&
//                                bluetoothController.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
//
//                                bluetoothViewModel.startScan()
//
//                                Log.i("[BOTTOM VIEW]", "SCANNED")
//                                lifecycleScope.launch {
//                                    bluetoothViewModel.state.collect { state ->
//                                        val adapter = DeviceAdapter(
//                                            requireContext(),
//                                            state.scannedDevices,
//                                            onClickListener = { device ->
//                                                bluetoothViewModel.connectToDevice(device)
//                                            })
//                                        diaLogBinding.listViewScannedDevices.adapter = adapter
//                                    }
//                                }
//                            } else {
//                                permissionCheck.launch(
//                                    arrayOf(
//                                        Manifest.permission.BLUETOOTH_SCAN,
//                                        Manifest.permission.ACCESS_COARSE_LOCATION
//                                    )
//                                )
//                            }
//                        }
//                        else -> {
//                            // Handle other cases or an unknown device type
//                        }
//                    }
//                } else {
//                    enableBluetoothLauncher.launch(
//                        Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
//                    )
//                }
//            } else {
//                Toast.makeText(
//                    requireContext(),
//                    "Bluetooth Support Unavailable!",
//                    Toast.LENGTH_LONG
//                ).show()
//            }
//        } else {
//            permissionCheck.launch(
//                arrayOf(
//                    Manifest.permission.BLUETOOTH_CONNECT
//                )
//            )
//        }
//    }
}