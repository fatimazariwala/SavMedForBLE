package mu.location.savmed.ui.call.fragments

import android.os.Bundle
import android.os.SystemClock
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.databinding.FragmentIncomingCallBinding
import mu.location.savmed.ui.call.viewModels.CurrentCallViewModel

class IncomingCallFragment : Fragment() {

    private lateinit var binding: FragmentIncomingCallBinding
    private lateinit var callViewModel: CurrentCallViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        binding = FragmentIncomingCallBinding.inflate(inflater,container,false)

        callViewModel = requireActivity().run {
            ViewModelProvider(this)[CurrentCallViewModel::class.java]
        }
        coreContext.callStatus.observe(viewLifecycleOwner) { callStatus ->
            binding.callStatus.setText(callStatus)
        }
        callViewModel.callDuration.observe(viewLifecycleOwner) { duration ->
            binding.chronometer.base = SystemClock.elapsedRealtime() - (1000 * duration)
            binding.chronometer.start()
        }

        binding.viewModel = callViewModel
        binding.lifecycleOwner = this
        // Inflate the layout for this fragment
        return binding.root
    }

}