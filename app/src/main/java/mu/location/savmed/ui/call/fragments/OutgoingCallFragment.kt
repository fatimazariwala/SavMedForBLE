package mu.location.savmed.ui.call.fragments

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.databinding.FragmentOutgoingCallBinding
import mu.location.savmed.ui.call.viewModels.CurrentCallViewModel

class OutgoingCallFragment : Fragment() {

    private lateinit var callsViewModel : CurrentCallViewModel

    private lateinit var binding : FragmentOutgoingCallBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        binding = FragmentOutgoingCallBinding.inflate(inflater,container,false)

        callsViewModel = requireActivity().run {
            ViewModelProvider(this)[CurrentCallViewModel::class.java]
        }
        coreContext.callStatus.observe(viewLifecycleOwner) { callStatus ->
            binding.callStatus.setText(callStatus)
        }
        callsViewModel.callDuration.observe(viewLifecycleOwner) { duration ->
            binding.chronometer.base = SystemClock.elapsedRealtime() - (1000 * duration)
            binding.chronometer.start()
        }
        Log.i("in outogign call frga","innnn")

        binding.viewModel = callsViewModel
        binding.lifecycleOwner = this
        // Inflate the layout for this fragment
        return binding.root
    }

}