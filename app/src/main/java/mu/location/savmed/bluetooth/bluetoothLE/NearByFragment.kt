package mu.location.savmed.bluetooth.bluetoothLE

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import mu.location.savmed.R
import mu.location.savmed.SavMed.Companion.bleClient
import mu.location.savmed.SavMed.Companion.bleServer
//import mu.location.savmed.SavMed.Companion.bluetoothController
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.bluetooth.bluetoothLE.controls.BLEClient
import mu.location.savmed.bluetooth.bluetoothLE.models.BluetoothLEScannedDevices
import mu.location.savmed.bluetooth.bluetoothLE.models.NearByAdapter
import mu.location.savmed.bluetooth.bluetoothLE.models.BluetoothLEViewModel
import mu.location.savmed.bluetooth.bluetoothLE.models.ConnectionResult
//import mu.location.savmed.bluetooth.bluetoothLE.models.BluetoothLEViewModelFactory
import mu.location.savmed.databinding.FragmentNearbyhelpBinding
import mu.location.savmed.ui.call.viewModels.CurrentCallViewModel
import mu.location.savmed.ui.contacts.fragments.ContactFragment
import mu.location.savmed.ui.contacts.fragments.ContactFragment.Companion
import mu.location.savmed.utils.SettingsManager

class NearByFragment : Fragment() {

    companion object {
        const val TAG = "[NearBy Frag]"
    }

    lateinit var binding: FragmentNearbyhelpBinding
    lateinit var scannedDeviceAdapter: NearByAdapter
    // Bluetooth ViewModel
    lateinit var bluetoothLEViewModel: BluetoothLEViewModel
    lateinit var callViewModel: CurrentCallViewModel

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
        startScan()
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
        bluetoothLEViewModel = ViewModelProvider(this)[BluetoothLEViewModel::class.java]
        callViewModel = ViewModelProvider(this)[CurrentCallViewModel::class.java]

        var prevConnectionState = bluetoothLEViewModel.state.value

        scannedDeviceAdapter = NearByAdapter(
            recyclerView = binding.rvMain,
            onCallCLick = { sipUri -> callViewModel.outgoingCall(sipUri,requireContext(),"nearByFrag") },
            onMessageClick = { device -> bluetoothLEViewModel.SendMessage(device) }
        )

        binding.rvMain.apply {
            adapter = scannedDeviceAdapter
            layoutManager = LinearLayoutManager(requireContext())

            addItemDecoration (
                DividerItemDecoration(requireContext(),DividerItemDecoration.VERTICAL).apply {
                    setDrawable(
                        ResourcesCompat.getDrawable(
                            resources,
                            R.drawable.search_result_divider,
                            null
                        )!!
                    )
                }
            )
        }
        Log.i(TAG, "SAV_MEDaaaa")

        observeEvents()
        startScan()
//
//        binding.btnHome.setOnClickListener() {
//            findNavController().navigate(R.id.action_nearByFragment_to_rippleFragment)
//        }

        bleClient.scanStatus.observe(viewLifecycleOwner) { stat ->
            Toast.makeText(requireContext(),stat,Toast.LENGTH_SHORT).show()
        }

        lifecycleScope.launch {

            bluetoothLEViewModel.state.collect { state ->

                if (state != prevConnectionState) {
                    if (state.toastMessage != null) {
                        Toast.makeText(
                            requireContext(),
                            state.toastMessage,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
//
//                if (state.message != null) {
//                    Log.i("Received------", state.message)
//                    showSplashDialog(state.message)
//                }

                prevConnectionState = state
            }
        }

        binding.searchForHelp.setOnClickListener(){
            if (bluetoothAdapter != null) {
                if(isBluetoothEnabled) {
                    startScan()
                } else {
                    enableBluetoothLauncher.launch(
                        Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    )
                }
            }
        }

        binding.messages.setOnClickListener() {
            //Toast.makeText(requireContext(),"Upcoming!!",Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.action_nearByFragment_to_messageListFragment)
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeMessages()
    }

    private fun observeEvents() {
        bleClient.bleEvent.onEach { result ->
            when (result) {
                ConnectionResult.ConnectionEstablished -> {
                    Toast.makeText(requireContext(),"Connected!",Toast.LENGTH_SHORT).show()
                }
                is ConnectionResult.Error -> {
                    Toast.makeText(requireContext(),result.message,Toast.LENGTH_LONG).show()
                }
                else -> {

                }
            }
        }
        .catch { throwable ->
            Log.e(TAG, "Error: $throwable")
        }
        .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun observeMessages() {

        bleServer.bleServerEvent.onEach { result ->

            when(result) {
                is ConnectionResult.BLETransferSucceeded -> {
                    Log.i(TAG,"yoooooooooooo ${result.message}")
                    Toast.makeText(requireContext(),result.message,Toast.LENGTH_SHORT).show()
                }
                else -> { }
            }

        }
        .catch { throwable ->
            Log.e(TAG, "Error: $throwable")
        }
        .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun startScan() {
        bleClient.resetDeviceList()
        if (bluetoothAdapter != null) {
            if (
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SettingsManager.hasPermission(Manifest.permission.BLUETOOTH_SCAN,requireContext()) &&
                            SettingsManager.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION,requireContext()) &&
                            SettingsManager.hasPermission(Manifest.permission.BLUETOOTH_CONNECT,requireContext())
                } else {
                    SettingsManager.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION,requireContext())
                }
            ) {
                if (isBluetoothEnabled) {
                    bluetoothLEViewModel.startScan()
                    lifecycleScope.launch {

                        bluetoothLEViewModel.state.collect { state ->

                            scannedDeviceAdapter.submitList(state.scannedDevices)

                            if(state.scannedDevices.isEmpty()) {
                                binding.sttTv.visibility = View.VISIBLE
                            } else {
                                binding.sttTv.visibility = View.GONE
                                Log.i(TAG,"Trynna ubmit")
                            }
                        }
                    }

                } else {
                    Toast.makeText(requireContext(), "Already Scanning...", Toast.LENGTH_SHORT)
                        .show()
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    permissionCheck.launch(
                        arrayOf(
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.BLUETOOTH_CONNECT
                        )
                    )
                } else {
                    permissionCheck.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                    )
                }
            }
        } else {
            Toast.makeText(requireContext(),"Current Device Dose not Have Bluetooth Capabilities!",Toast.LENGTH_SHORT).show()
        }
    }

}