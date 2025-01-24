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
import mu.location.savmed.databinding.FragmentActiveCallBinding
import mu.location.savmed.ui.call.viewModels.CurrentCallViewModel

class ActiveCallFragment : Fragment() {

    private lateinit var binding: FragmentActiveCallBinding

    private lateinit var callViewModel: CurrentCallViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        binding = FragmentActiveCallBinding.inflate(inflater,container, false)

        callViewModel = requireActivity().run {
            ViewModelProvider(this)[CurrentCallViewModel::class.java]
        }

        Log.i("In coming call frag","inocming call fragemt")

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = callViewModel

        coreContext.callStatus.observe(viewLifecycleOwner) { callStatus ->
            binding.callStatus.setText(callStatus)
        }
        callViewModel.callDuration.observe(viewLifecycleOwner) { duration ->
            binding.chronometer.base = SystemClock.elapsedRealtime() - (1000 * duration)
            binding.chronometer.start()
        }
        // Inflate the layout for this fragment
        return binding.root
    }
}