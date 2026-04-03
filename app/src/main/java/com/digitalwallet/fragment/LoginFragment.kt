package com.digitalwallet.fragment

import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.digitalwallet.R
import com.digitalwallet.databinding.FragmentLoginBinding
import com.digitalwallet.repository.UserRepository
import com.digitalwallet.util.DatabaseProvider
import com.digitalwallet.util.SessionManager
import com.digitalwallet.viewmodel.AuthViewModel
import com.digitalwallet.viewmodel.AuthViewModelFactory
import com.google.firebase.auth.FirebaseAuth

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!


    private val viewModel: AuthViewModel by viewModels {
        AuthViewModelFactory(
            UserRepository(DatabaseProvider.getDatabase(requireContext()))
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
       // Log.d("FirebaseCheck", "Current user: ${FirebaseAuth.getInstance().currentUser}")

        _binding = FragmentLoginBinding.inflate(inflater, container, false)

        var isPinVisible = false

        binding.togglePinVisibility.setOnClickListener {
            isPinVisible = !isPinVisible
            if (isPinVisible) {
                binding.pinInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_VARIATION_NORMAL
                binding.togglePinVisibility.setImageResource(R.drawable.ic_visibility)
            } else {
                binding.pinInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
                binding.togglePinVisibility.setImageResource(R.drawable.ic_visibility_off)
            }

            // Move cursor to end when toggling visibility
            binding.pinInput.setSelection(binding.pinInput.text?.length ?: 0)
        }

        binding.loginButton.setOnClickListener {
            val emailOrPhone = binding.emailOrPhoneInput.text.toString()
            val pin = binding.pinInput.text.toString()
            // Validate email or phone
            if (!Patterns.EMAIL_ADDRESS.matcher(emailOrPhone).matches() && !Patterns.PHONE.matcher(
                    emailOrPhone
                ).matches()
            ) {
                toast(getString(R.string.invalid_email_or_phone))
                return@setOnClickListener
            }

            viewModel.login(emailOrPhone.lowercase(), pin)
        }
        binding.registerButton.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }
        binding.changePinButton.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_changePinFragment)
        }

        viewModel.loginResult.observe(viewLifecycleOwner) { result ->
            if (result?.isSuccess == true) {
                val user = result.getOrNull()
                if (user != null) {
                    SessionManager.saveUserId(requireContext(), user.userId)
                    val auth = FirebaseAuth.getInstance()
                    auth.signInWithEmailAndPassword(user.email.lowercase(), "TempPass123!")
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Log.d("Login", "Firebase email login successful")
                                findNavController().navigate(R.id.action_loginFragment_to_documentsFragment)
                            } else {
                                Log.e("Login", "Firebase email login failed: ${task.exception?.message}")
                                findNavController().navigate(R.id.action_loginFragment_to_documentsFragment)
                            }
                        }
                }
            }
            else if(result?.isFailure == true)
            {
                val msg = when (result.exceptionOrNull()?.message) {
                    "INVALID_CREDENTIALS" -> getString(R.string.invalid_credentials)
                    "ACCOUNT_LOCKED" -> getString(R.string.account_locked)
                    else -> getString(R.string.generic_error)
                }
                toast(msg)
            }
        }


        return binding.root
    }


    override fun onResume() {
        super.onResume()
        val userId = SessionManager.getUserId(requireContext())
        viewModel.clearLoginResult()
        if (userId != null) findNavController().navigate(R.id.action_loginFragment_to_documentsFragment)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.clearLoginResult()
        _binding = null
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
}
