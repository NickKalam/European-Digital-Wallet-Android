package com.digitalwallet.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.digitalwallet.R
import com.digitalwallet.databinding.FragmentChangePinBinding
import com.digitalwallet.repository.UserRepository
import com.digitalwallet.util.DatabaseProvider
import com.digitalwallet.viewmodel.AuthViewModel
import com.digitalwallet.viewmodel.AuthViewModelFactory
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth

class ChangePinFragment : Fragment() {

    private var _binding: FragmentChangePinBinding? = null
    private val binding get() = _binding!!

    private val firebaseAuth = FirebaseAuth.getInstance()

    private val viewModel: AuthViewModel by activityViewModels {
        AuthViewModelFactory(UserRepository(DatabaseProvider.getDatabase(requireContext())))
    }

    // State for mock phone verification and email-link flow
    private var sentCode: String? = null
    private var verifiedIdentity: String? = null
    private var emailLinkSent: Boolean = false   //  ensures user can't skip email verify

    private val notifChannelId = "mock_verification_channel"

    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChangePinBinding.inflate(inflater, container, false)
        createNotificationChannelIfNeeded()
        ensureNotifPermissionIfNeeded()

        var isOldPinVisible = false

        binding.toggleOldPinVisibility.setOnClickListener {
            isOldPinVisible = !isOldPinVisible
            if (isOldPinVisible) {
                binding.oldPinInput.inputType = InputType.TYPE_CLASS_NUMBER or
                        InputType.TYPE_NUMBER_VARIATION_NORMAL
                binding.toggleOldPinVisibility.setImageResource(R.drawable.ic_visibility)
            } else {
                binding.oldPinInput.inputType = InputType.TYPE_CLASS_NUMBER or
                        InputType.TYPE_NUMBER_VARIATION_PASSWORD
                binding.toggleOldPinVisibility.setImageResource(R.drawable.ic_visibility_off)
            }

            // Move cursor to end when toggling visibility
            binding.oldPinInput.setSelection(binding.oldPinInput.text?.length ?: 0)
        }

        var isNewPinVisible = false

        binding.toggleNewPinVisibility.setOnClickListener {
            isNewPinVisible = !isNewPinVisible
            if (isNewPinVisible) {
                binding.newPinInput.inputType = InputType.TYPE_CLASS_NUMBER or
                        InputType.TYPE_NUMBER_VARIATION_NORMAL
                binding.toggleNewPinVisibility.setImageResource(R.drawable.ic_visibility)
            } else {
                binding.newPinInput.inputType = InputType.TYPE_CLASS_NUMBER or
                        InputType.TYPE_NUMBER_VARIATION_PASSWORD
                binding.toggleNewPinVisibility.setImageResource(R.drawable.ic_visibility_off)
            }

            // Move cursor to end when toggling visibility
            binding.newPinInput.setSelection(binding.newPinInput.text?.length ?: 0)
        }

        // 1) Verify identifier exists (Room)
        binding.verifyEmail.setOnClickListener {
            val input = binding.emailOrPhoneInput.text.toString().trim().lowercase()
            if (input.isBlank()) {
                toast(getString(R.string.empty_fields_error))
                return@setOnClickListener
            }
            viewModel.verifyIdentity(input)
        }

        // 2) Continue (email -> paste link & signInWithEmailLink, phone -> enter code)
        binding.continueButton.setOnClickListener {
            val id = (verifiedIdentity ?: binding.emailOrPhoneInput.text.toString()).trim().lowercase()

            when {
                Patterns.EMAIL_ADDRESS.matcher(id).matches() -> {
                    // Prevent skipping the verify step
                    if (!emailLinkSent) {
                        toast(getString(R.string.tap_verify_first))
                        return@setOnClickListener
                    }

                    // Ask the user to paste the link they got via email
                    showPasteLinkDialog { link ->
                        if (!FirebaseAuth.getInstance().isSignInWithEmailLink(link)) {
                            toast(getString(R.string.generic_error))
                            return@showPasteLinkDialog
                        }
                        // Make sure we are not signed in as someone else
                        firebaseAuth.signOut()
                        firebaseAuth.signInWithEmailLink(id, link).addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val signedInEmail = firebaseAuth.currentUser?.email?.lowercase()
                                if (signedInEmail == id) {
                                    unlockPinChangeUI()
                                } else {
                                    toast("Signed-in email doesn't match the address you entered.")
                                }
                            } else {
                                toast(task.exception?.localizedMessage ?: getString(R.string.firebase_signin_failed))
                            }
                        }
                    }
                }

                Patterns.PHONE.matcher(id).matches() -> {
                    // Mock SMS code entry
                    promptForCode { entered ->
                        if (entered == sentCode) unlockPinChangeUI()
                        else toast(getString(R.string.invalid_code))
                    }
                }

                else -> toast(getString(R.string.invalid_email_or_phone))
            }
        }

        // 3) Change PIN
        binding.changePinButton.setOnClickListener {
            val identifier = verifiedIdentity ?: binding.emailOrPhoneInput.text.toString().trim().lowercase()
            val oldPin = binding.oldPinInput.text.toString()
            val newPin = binding.newPinInput.text.toString()
            if (identifier.isBlank() || oldPin.isBlank() || newPin.isBlank()) {
                toast(getString(R.string.empty_fields_error)); return@setOnClickListener
            }
            viewModel.changePin(identifier, oldPin, newPin)
        }

        // Observe identity verification (Room lookup succeeded/failed)
        viewModel.verifyIdentityResult.observe(viewLifecycleOwner) { res ->
            res ?: return@observe
            if (res.isSuccess) {
                val identifierRaw = binding.emailOrPhoneInput.text.toString().trim().lowercase()
                verifiedIdentity = identifierRaw

                if (Patterns.EMAIL_ADDRESS.matcher(identifierRaw).matches()) {
                    //  Send a SIGN-IN LINK email
                    val email = identifierRaw.lowercase()
                    firebaseAuth.sendSignInLinkToEmail(email, actionCodeSettings())
                        .addOnCompleteListener { linkTask ->
                            if (linkTask.isSuccessful) {
                                emailLinkSent = true
                                toast(getString(R.string.verification_email_sent))
                                prepareForAwaitingVerification()
                            } else {
                                emailLinkSent = false
                                val msg = linkTask.exception?.localizedMessage ?: getString(R.string.generic_error)
                                toast(msg)
                            }
                        }

                } else if (Patterns.PHONE.matcher(identifierRaw).matches()) {
                    // PHONE: mock SMS via notification
                    sentCode = viewModel.generateSixDigitCode()
                    showVerificationNotification(sentCode!!)
                    toast(getString(R.string.code_sent))
                    prepareForAwaitingVerification()

                } else {
                    toast(getString(R.string.invalid_email_or_phone))
                }
            } else {
                val msg = when (res.exceptionOrNull()?.message) {
                    "USER_NOT_FOUND" -> getString(R.string.user_not_found_error)
                    else -> getString(R.string.generic_error)
                }
                toast(msg)
            }
            viewModel.clearVerifyIdentity()
        }

        // Observe change pin
        viewModel.changePinResult.observe(viewLifecycleOwner) { res ->
            res ?: return@observe
            if (res.isSuccess) {
                toast(getString(R.string.pin_changed_success))
                findNavController().navigateUp()
            } else {
                val msg = when (res.exceptionOrNull()?.message) {
                    "INVALID_OLD_PIN" -> getString(R.string.invalid_pin_error)
                    "PIN_RULES" -> getString(R.string.pin_rules_error)
                    "PIN_SAME_AS_OLD" -> getString(R.string.pin_same_error)
                    "USER_NOT_FOUND" -> getString(R.string.user_not_found_error)
                    else -> getString(R.string.update_failed)
                }
                toast(msg)
            }
            viewModel.clearChangePin()
        }

        binding.loginButton.setOnClickListener { findNavController().navigateUp() }
        return binding.root
    }

    // Email link helpers

    private fun actionCodeSettings(): ActionCodeSettings {
        return ActionCodeSettings.newBuilder()
            .setUrl("https://digital-wallet-8e84c.firebaseapp.com") // your Firebase Hosting/Auth domain
            .setHandleCodeInApp(true)
            .setAndroidPackageName(requireContext().packageName, true, null)
            .build()
    }

    private fun showPasteLinkDialog(onLink: (String) -> Unit) {
        val input = EditText(requireContext()).apply {
            hint =getString(R.string.paste_verification_link)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(false)
        }
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.paste_verification_link_from_inbox))
            .setView(input)
            .setPositiveButton(getString(R.string.continue_button)) { _, _ ->
                onLink(input.text.toString().trim())
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // Mock phone helpers


    @SuppressLint("MissingPermission")
    private fun showVerificationNotification(code: String) {
        val title = getString(R.string.sms_verification_title)
        val text = getString(R.string.your_code_is, code)

        val builder = NotificationCompat.Builder(requireContext(), notifChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(requireContext())) {
            val id = (System.currentTimeMillis() % 100000).toInt()
            notify(id, builder.build())
        }
    }

    private fun promptForCode(onSubmit: (String) -> Unit) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.enter_code)
        }
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.verify_code))
            .setView(input)
            .setPositiveButton(getString(R.string.verify)) { _, _ ->
                onSubmit(input.text.toString().trim())
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun prepareForAwaitingVerification() {
        binding.continueButton.visibility = View.VISIBLE
        binding.verifyEmail.visibility = View.INVISIBLE
        binding.emailOrPhoneInput.isEnabled = false
    }

    private fun unlockPinChangeUI() {
        binding.oldPinInput.visibility = View.VISIBLE
        binding.toggleOldPinVisibility.visibility=View.VISIBLE
        binding.newPinInput.visibility = View.VISIBLE
        binding.toggleNewPinVisibility.visibility=View.VISIBLE
        binding.changePinButton.visibility = View.VISIBLE

        binding.verifyEmail.visibility = View.INVISIBLE
        binding.continueButton.visibility = View.GONE
        binding.emailOrPhoneInput.isEnabled = false

        toast(getString(R.string.user_found))
    }

    // ---------- Notifications channel ----------

    private fun createNotificationChannelIfNeeded() {
        val channel = NotificationChannel(
            notifChannelId,
            getString(R.string.mock_verification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = getString(R.string.mock_verification_channel_desc) }
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

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
