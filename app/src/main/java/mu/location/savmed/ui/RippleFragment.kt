package mu.location.savmed.ui

import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import mu.location.savmed.MainActivity
import mu.location.savmed.SavMed.Companion.bleClient
import mu.location.savmed.SavMed.Companion.bleServer
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.contacts.ContactsManager.Companion.SAVMED_ADDRESS_BOOK_FRIEND_LIST
import mu.location.savmed.databinding.FragmentRippleBinding
import mu.location.savmed.ui.auth.LoginActivity
import mu.location.savmed.ui.auth.RegistrationActivity
import mu.location.savmed.ui.call.viewModels.CurrentCallViewModel
import mu.location.savmed.ui.contacts.fragments.ContactFragment
import mu.location.savmed.utils.SharedPreference

class RippleFragment : Fragment() {

    companion object {
        const val TAG = "[Ripple Fragment]"
    }

    lateinit var binding: FragmentRippleBinding

    lateinit var rippleViewModel: RippleViewModel

    private lateinit var callViewModel: CurrentCallViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SharedPreference.init(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        rippleViewModel = requireActivity().run {
            ViewModelProvider(this)[RippleViewModel::class.java]
        }

        callViewModel = run {
            ViewModelProvider(this)[CurrentCallViewModel::class.java]
        }

        binding = FragmentRippleBinding.inflate(inflater, container, false)

        binding.content.startRippleAnimation()

        Log.i(TAG,"In shared pref going")

        binding.sos.setOnClickListener() {
            bleClient.startBLEScan()
            callViewModel.informEmrContacts()
        }
        binding.idBtnLogOut.setOnClickListener {

            rippleViewModel.logout()
            SharedPreference.clear()

            Log.i("SIPService", "Logged out and data cleared.")
            val i = Intent(requireContext(), MainActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(i)
            requireActivity().finish()

//            if (logout_stat) {
//                Toast.makeText(requireContext(), "Logout Successfull!", Toast.LENGTH_SHORT).show()
//            } else {
//                Toast.makeText(requireContext(), "Logout Unsuccessfull!", Toast.LENGTH_SHORT).show()
//            }
        }

        if (SharedPreference.username.isNullOrEmpty()) {
            binding.usernotloggedin.visibility = View.VISIBLE
            binding.userloggedin.visibility = View.GONE
            binding.loginBtn.visibility = View.VISIBLE
            binding.idBtnLogOut.visibility = View.GONE

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
            binding.loginBtn.visibility = View.GONE
            binding.idBtnLogOut.visibility = View.VISIBLE
            binding.idTVUserName.setText("Welcome ${SharedPreference.username}")
            rippleViewModel.setAvatar()
        }

        getLocation(requireContext())

        // Inflate the layout for this fragment
        return binding.root
    }

    fun getLocation(context: Context) {
        val lat = coreContext.onLocationEvent["latitude"]
        val lon = coreContext.onLocationEvent["longitude"]

        Log.i(TAG,"I am in setLoc $lat $lon")

        if (lon != null && lat != null) {
            val geocoder = Geocoder(context)
            try {
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                binding.locationData.text = addresses!![0].getAddressLine(0)
            } catch (e: Exception) {
                Log.i(TAG,"Location fetch error -> ${e.message}")
                binding.locationData.text = "Unable to fetch address -> [${lat},${lon}]"
            }
        } else {

            val delayMillis = (10000..20000).random().toLong() // Random delay between 10 and 20 seconds
            Log.i(TAG, "Latitude or Longitude is null, retrying after $delayMillis milliseconds.")

            // Use Handler to post a delayed task
            Handler(Looper.getMainLooper()).postDelayed({
                getLocation(context)  // Call the function again after the delay
            }, delayMillis)

            binding.locationData.text = "Unable to fetch Location"
        }
    }

}