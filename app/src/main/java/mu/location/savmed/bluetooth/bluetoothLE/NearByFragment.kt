package mu.location.savmed.bluetooth.bluetoothLE

import android.Manifest
import android.app.AlertDialog
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
import kotlinx.coroutines.launch
import mu.location.savmed.R
import mu.location.savmed.SavMed.Companion.bluetoothLEController
//import mu.location.savmed.SavMed.Companion.bluetoothController
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.bluetooth.bluetoothLE.models.NearByAdapter
import mu.location.savmed.bluetooth.bluetoothLE.models.BluetoothLEViewModel
import mu.location.savmed.bluetooth.bluetoothLE.models.BluetoothLEViewModelFactory
import mu.location.savmed.databinding.FragmentNearbyhelpBinding

class NearByFragment : Fragment() {

    companion object {
        const val TAG = "[NearBy Frag]"
    }

    lateinit var binding: FragmentNearbyhelpBinding

    // Bluetooth ViewModel
    val viewModelFactory = BluetoothLEViewModelFactory(bluetoothLEController)
    lateinit var bluetoothLEViewModel: BluetoothLEViewModel

    // Initialize Bluetooth Manager Class
    private val bluetoothManager by lazy {
        requireActivity().getSystemService(BluetoothManager::class.java)
    }

    // get Device Bluetooth adapter through Bluetooth Manager
    private val bluetoothAdapter by lazy {
        bluetoothManager?.adapter
    }

    // Check is Bluetooth is Currently Enabled
    private val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    // Launcher to ask users permission before starting bluetooth
    // when user clicks allow bluetooth will be activated
    // This will be needed everytime we want to turn on bluetooth

    val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        bluetoothLEViewModel.startScan()
    }

    private val permissionCheck =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            try {
                Log.i(TAG,"Setting Adapter name to ${coreContext.core.defaultAccount?.params?.identityAddress?.username.toString()}_SavMed")
               // bluetoothController.setAdapterName(coreContext.core.defaultAccount?.params?.identityAddress?.username.toString())
            } catch (e: Exception) {
                Log.i(TAG,"Starting core...")
                coreContext.startCore()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentNearbyhelpBinding.inflate(inflater,container,false)
        bluetoothLEViewModel = ViewModelProvider(this, viewModelFactory)[BluetoothLEViewModel::class.java]

        var prevConnectionState = bluetoothLEViewModel.state.value

        binding.btnHome.setOnClickListener() {
            findNavController().navigate(R.id.action_nearByFragment_to_rippleFragment)
        }

        val adapter = NearByAdapter(
            requireActivity(),
            bluetoothLEViewModel,
            state.scannedDevices
        )

        lifecycleScope.launch {
            bluetoothLEViewModel.state.collect { state ->

                if (state.toastMessage != null) {
                    Toast.makeText(
                        requireContext(),
                        state.toastMessage,
                        Toast.LENGTH_LONG
                    ).show()
                }

                prevConnectionState = state
            }
        }

        binding.searchForHelp.setOnClickListener(){
            if (bluetoothAdapter != null) {
                if(isBluetoothEnabled) {
                    bluetoothLEViewModel.startScan()
                } else {
                    enableBluetoothLauncher.launch(
                        Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    )
                }
            }
        }

//        binding.allowIncomingConnections.setOnClickListener() {
//
//            if (bluetoothAdapter != null) {
//                if(isBluetoothEnabled) {
//                    val requestCode = 1;
////                    val discoverableIntent: Intent =
////                        Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
////                            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
////                        }
////                    startActivityForResult(discoverableIntent, requestCode)
//
//                   // bluetoothLEViewModel.waitForIncomingConnection()
//                } else {
//                    enableBluetoothLauncher.launch(
//                        Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
//                    )
//                }
//            }
//        }

        if (bluetoothAdapter != null) {
            if (bluetoothLEController.hasPermission(Manifest.permission.BLUETOOTH_SCAN) &&
                bluetoothLEController.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) &&
                bluetoothLEController.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
            ) {
                if(isBluetoothEnabled) {

                    bluetoothLEViewModel.startScan()

                    lifecycleScope.launch {
                        bluetoothLEViewModel.state.collect { state ->

                            binding.rvMain.adapter = adapter
                            Log.i(TAG, "SAV_MEDaaaa")

                            if(state.scannedDevices.isEmpty()) {
                                binding.sttTv.visibility = View.VISIBLE
                            } else {
                                binding.sttTv.visibility = View.GONE
                            }
                        }
                    }
                } else {
                    enableBluetoothLauncher.launch(
                        Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    )
                }

            } else {
                permissionCheck.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
                )
            }
        } else {
            Toast.makeText(
                requireContext(),
                "Bluetooth Not Supported!",
                Toast.LENGTH_SHORT
            ).show()
        }
        // Inflate the layout for this fragment
        return binding.root
    }

    private fun showSplashDialog(message: String) {
        val dialogBuilder = AlertDialog.Builder(requireContext())

        dialogBuilder.setMessage(message)
            .setCancelable(false) // Prevent dismissing the dialog by tapping outside
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss() // Dismiss the dialog when "OK" is pressed
            }

        // Create and show the dialog
        val alert = dialogBuilder.create()
        alert.show()

        // Optional: Auto-dismiss the dialog after a certain time
        alert.window?.setLayout(800, 400) // Set size of the dialog
        alert.setOnShowListener {
            alert.getButton(AlertDialog.BUTTON_POSITIVE).postDelayed({
                alert.dismiss()
            }, 2000) // Dismiss after 2 seconds
        }
    }
}