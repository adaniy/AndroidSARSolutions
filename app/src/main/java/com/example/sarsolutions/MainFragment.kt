package com.example.sarsolutions

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.findNavController
import com.example.sarsolutions.services.LocationService
import com.google.android.gms.location.LocationCallback
import com.google.firebase.auth.FirebaseAuth
import kotlinx.android.synthetic.main.main_fragment.*

class MainFragment : Fragment() {

    private lateinit var locationCallback: LocationCallback
    private var currentShiftId: String? = null
    private val auth = FirebaseAuth.getInstance()
    private lateinit var locationService: LocationService
    private var service: LocationService? = null

    private lateinit var viewModel: CasesViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.main_fragment, container, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = activity?.run {
            ViewModelProviders.of(this)[CasesViewModel::class.java]
        } ?: throw Exception("Invalid Activity")
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        // Restore state depending on view model
        restoreState()

        sign_out_button.setOnClickListener {
            auth.signOut()
            view?.findNavController()?.navigate(R.id.action_mainFragment_to_loginFragment)
        }

        // Toggle test mode
        test_button.setOnClickListener {
            if (viewModel.isTestingEnabled) { // Disable Testing
                viewModel.isTestingEnabled = false
                test_button.text = "Enable Test Mode"
                test_button.setBackgroundColor(resources.getColor(R.color.warning))
            } else { // Enable Testing
                viewModel.isTestingEnabled = true
                test_button.text = "Disable Test Mode"
                test_button.setBackgroundColor(resources.getColor(R.color.error))
            }
        }

        start_button.setOnClickListener {
            if (viewModel.getBinder().value == null) {
                disableButtons()
                startLocationService()
            }
            else {
                stopLocationService()
                enableButtons()
            }
        }

        // Rough flow: startButton --> startLocationServices() --> viewModel binder
        viewModel.getBinder().observe(viewLifecycleOwner, Observer { binder ->
            // Either service was bound or unbound
            service = binder?.getService()
            observeService()
        })
    }

    private fun observeService() {
        service?.getLastUpdated()?.observe(viewLifecycleOwner, Observer { lastUpdated ->
            location_id.text = lastUpdated
        })
    }

    private fun startLocationService() {
        val serviceIntent = Intent(context, LocationService::class.java)
        serviceIntent.putExtra(LocationService.isTestMode, viewModel.isTestingEnabled)
        ContextCompat.startForegroundService(context!!, serviceIntent)
        bindService()
    }

    private fun bindService() {
        val serviceIntent = Intent(context, LocationService::class.java)
        activity?.bindService(
            serviceIntent,
            viewModel.getServiceConnection(),
            Context.BIND_AUTO_CREATE
        )
    }

    private fun stopLocationService() {
        val serviceIntent = Intent(context, LocationService::class.java)
        unbindService()
        activity?.stopService(serviceIntent)
        // Deletes reference to binder in viewmodel
        // Will not be re-bind on configuration change
        // NOTE: Must call unbindService() before removeService()
        viewModel.removeService()
    }

    private fun unbindService() {
        if (viewModel.getBinder().value != null)
            activity?.unbindService(viewModel.getServiceConnection())
    }

    override fun onResume() {
        super.onResume()
        // Rebind service if an instance of a service exists
        if (viewModel.getBinder().value != null)
            bindService()
    }

    override fun onPause() {
        super.onPause()
        viewModel.lastUpdatedText = location_id.text.toString()
        unbindService()
    }

    // Restore view state on configuration change
    private fun restoreState() {
        if (viewModel.getBinder().value != null) {
            disableButtons()
            // Service is alive and running but needs to be bound back to activity
            bindService()
            location_id.text = viewModel.lastUpdatedText
        }

        if (viewModel.isTestingEnabled) {
            test_button.text = "Disable Test Mode"
            test_button.setBackgroundColor(resources.getColor(R.color.error))
        }
    }

    private fun disableButtons() {
        start_button.text = getString(R.string.stop)
        test_button.isEnabled = false
        sign_out_button.isEnabled = false
        start_button.setBackgroundColor(resources.getColor(R.color.error))
    }

    private fun enableButtons() {
        start_button.text = getString(R.string.start)
        test_button.isEnabled = true
        sign_out_button.isEnabled = true
        start_button.setBackgroundColor(resources.getColor(R.color.success))
    }

}
