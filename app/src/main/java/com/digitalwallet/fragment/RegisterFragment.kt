package com.digitalwallet.fragment

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.util.Patterns
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.digitalwallet.R
import com.digitalwallet.databinding.FragmentRegisterBinding
import com.digitalwallet.repository.UserRepository
import com.digitalwallet.util.DatabaseProvider
import com.digitalwallet.viewmodel.AuthViewModel
import com.digitalwallet.viewmodel.AuthViewModelFactory
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {

    private var isRegistered=false
    private var tempUid: String? = null
    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val firebaseAuth = FirebaseAuth.getInstance()
    private val viewModel: AuthViewModel by viewModels {
        AuthViewModelFactory(UserRepository(DatabaseProvider.getDatabase(requireContext())))
    }

    // Mock SMS state
    private var sentCode: String? = null
    private var currentPhone: String? = null
    private val notifChannelId = "mock_verification_channel"
    private val placeholderPassword = "TempPass123!"

    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onStart() {
        super.onStart()

        // Check for an unverified firebase account  from a previous crash
        val user = firebaseAuth.currentUser

        // Delete firebase user  who is  logged in to Firebase, but 'tempUid' is null
        // and  the user is not verified due to the app crashing after a verification email was sent
        //but before the registration process was  completed
        if (user != null && tempUid == null && !user.isEmailVerified) {
            user.delete().addOnFailureListener {
                // If delete fails (e.g. token expired), just sign out to clear the bad state
                firebaseAuth.signOut()
            }
        }
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        createNotificationChannelIfNeeded()
        ensureNotifPermissionIfNeeded()

        var isPinVisible = false

        binding.togglePinVisibility.setOnClickListener {
            isPinVisible = !isPinVisible
            if (isPinVisible) {
                binding.pinInput.inputType = InputType.TYPE_CLASS_NUMBER or
                        InputType.TYPE_NUMBER_VARIATION_NORMAL
                binding.togglePinVisibility.setImageResource(R.drawable.ic_visibility)
            } else {
                binding.pinInput.inputType = InputType.TYPE_CLASS_NUMBER or
                        InputType.TYPE_NUMBER_VARIATION_PASSWORD
                binding.togglePinVisibility.setImageResource(R.drawable.ic_visibility_off)
            }

            // Move cursor to end when toggling visibility
            binding.pinInput.setSelection(binding.pinInput.text?.length ?: 0)
        }

        binding.registerButton.setOnClickListener {
            val name = binding.nameInput.text.toString().trim()
            val email = binding.emailInput.text.toString().trim().lowercase()
            val phone = binding.phoneInput.text.toString().trim().takeIf { it.isNotEmpty() }
            val pin = binding.pinInput.text.toString().trim()

            if (name.isBlank() || email.isBlank() || pin.isBlank()) {
                toast(getString(R.string.empty_fields_error)); return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                toast(getString(R.string.invalid_email)); return@setOnClickListener
            }
            if (phone != null && !Patterns.PHONE.matcher(phone).matches()) {
                toast(getString(R.string.invalid_phone)); return@setOnClickListener
            }
            lifecycleScope.launch {
                val emailInUse = viewModel.verifyIdentityNow(email)
                if (emailInUse) {
                    toast(getString(R.string.email_already_in_use))
                    return@launch
                }

                if (phone != null) {
                    val phoneInUse = viewModel.verifyIdentityNow(phone)
                    if (phoneInUse) {
                        toast(getString(R.string.phone_already_in_use))
                        return@launch
                    }
                }
                firebaseAuth.fetchSignInMethodsForEmail(email).addOnSuccessListener { res ->
                    val exists = (res.signInMethods?.isNotEmpty() == true)
                    if (exists) {
                        toast(getString(R.string.email_already_in_use)); return@addOnSuccessListener
                    }

                    firebaseAuth.createUserWithEmailAndPassword(email, placeholderPassword)
                        .addOnCompleteListener { task ->
                            if (!task.isSuccessful) {
                                toast(
                                    task.exception?.localizedMessage
                                        ?: getString(R.string.generic_error)
                                )
                                return@addOnCompleteListener
                            }
                            tempUid = firebaseAuth.currentUser?.uid
                            firebaseAuth.currentUser?.sendEmailVerification()
                                ?.addOnCompleteListener { emailTask ->
                                    if (emailTask.isSuccessful) {
                                        toast(getString(R.string.verification_email_sent))
                                        binding.nameInput.isEnabled = false
                                        binding.phoneInput.isEnabled = false
                                        binding.emailInput.isEnabled = false
                                        binding.pinInput.isEnabled = false
                                        binding.continueButton.visibility =
                                            android.view.View.VISIBLE
                                        binding.registerButton.visibility = android.view.View.GONE
                                    } else {
                                        toast(
                                            emailTask.exception?.localizedMessage
                                                ?: getString(R.string.generic_error)
                                        )
                                    }
                                }
                        }
                }.addOnFailureListener { e ->
                    toast(
                        e.localizedMessage ?: getString(R.string.generic_error)
                    )
                }
            }
        }

        binding.continueButton.setOnClickListener {
            val name = binding.nameInput.text.toString().trim()
            val email = binding.emailInput.text.toString().trim().lowercase()
            val phone = binding.phoneInput.text.toString().trim().takeIf { it.isNotEmpty() }
            val pin = binding.pinInput.text.toString().trim()

            firebaseAuth.currentUser?.reload()?.addOnSuccessListener {
                val user = firebaseAuth.currentUser
                if (user?.isEmailVerified == true) {
                    if (phone != null) {
                        startMockPhoneVerification(phone) {
                            viewModel.register(name, email, phone, pin)
                        }
                    } else {
                        viewModel.register(name, email, null, pin)
                    }
                } else {
                    toast(getString(R.string.email_not_verified))
                }
            }
        }

        binding.loginButton.setOnClickListener {
            findNavController().navigateUp()
        }

        viewModel.registerResult.observe(viewLifecycleOwner) { result ->
            if (result?.isSuccess==true) {
                toast(getString(R.string.registration_success))
                isRegistered=true
                findNavController().navigateUp()
            } else {
                val msg = when (result?.exceptionOrNull()?.message) {
                    "USER_EXISTS" -> getString(R.string.user_exists)
                    "PIN_RULES" -> getString(R.string.pin_rules_error)
                    else -> getString(R.string.generic_error)
                }
                toast(msg)
            }
        }

        return binding.root
    }

    // Mock phone verification

    private fun startMockPhoneVerification(phone: String, onVerified: () -> Unit) {
        currentPhone = phone
        sentCode = viewModel.generateSixDigitCode()
        showVerificationNotification(sentCode!!)
        toast(getString(R.string.code_sent))
        promptForCode { entered ->
            if (entered == sentCode) {
                toast(getString(R.string.phone_verified))
                onVerified()
            } else {
                toast(getString(R.string.invalid_code))
            }
        }
    }


    @android.annotation.SuppressLint("MissingPermission")
    private fun showVerificationNotification( code: String) {
        val title = getString(R.string.sms_verification_title)
        val text = getString(R.string.your_code_is, code)
        val builder = NotificationCompat.Builder(requireContext(), notifChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        NotificationManagerCompat.from(requireContext()).notify((System.currentTimeMillis() % 100000).toInt(), builder.build())
    }

    private fun promptForCode(onSubmit: (String) -> Unit) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.enter_code)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.verify_code))
            .setView(input)
            .setPositiveButton(getString(R.string.verify)) { _, _ ->
                onSubmit(input.text.toString().trim())
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun createNotificationChannelIfNeeded() {
        val channel = NotificationChannel(
            notifChannelId,
            getString(R.string.mock_verification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.mock_verification_channel_desc)
        }
        val nm = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun ensureNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()


    override fun onDestroyView() {
        super.onDestroyView()
        //delete user from firebase if they got a verification email but didn't complete the registration
        val hasPhone=binding.phoneInput.text.toString().isNotBlank()
        val user = firebaseAuth.currentUser
        val currentUid = user?.uid
        lifecycleScope.launch {
            if (!isRegistered && currentUid != null && currentUid == tempUid) {
                // Reload to get latest verification state
                user.reload()
                if (user.isEmailVerified.not() || hasPhone)
                {
                    try {
                        user.delete()
                        Log.d("Cleanup", "Deleted unverified user")
                    } catch (e: Exception) {
                        Log.e("Cleanup", "Failed to delete unverified user: ${e.message}")
                    }
                }
            }
        }
        viewModel.clearRegisterResult()
        _binding = null
    }

}
