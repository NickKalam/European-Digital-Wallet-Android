package com.digitalwallet.fragment

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.digitalwallet.R
import com.digitalwallet.databinding.DialogShowQrBinding
import com.digitalwallet.util.SessionManager
import com.digitalwallet.viewmodel.DocumentViewModel

class ShowQrDialogFragment : DialogFragment() {
    private val viewModel: DocumentViewModel by activityViewModels()

    private var _binding: DialogShowQrBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogShowQrBinding.inflate(layoutInflater)

        viewModel.clearQrResult()

        viewModel.qrCodeResult.observe(this) { result ->
            if (result == null) return@observe

            if (result.isSuccess) {
                val qrText = result.getOrNull().orEmpty()
                viewModel.generateQrBitmap(qrText)
            } else {
                val msg = when (result.exceptionOrNull()?.message) {
                    "INVALID_PIN" -> getString(R.string.invalid_pin_error)
                    "QR_RENDER_ERROR" -> getString(R.string.qr_render_error)
                    else -> getString(R.string.generic_error)
                }
                toast(msg)
                viewModel.clearQrResult()
            }
        }

        // QR DISPLAY OBSERVER (Using Visibility Toggle)
        viewModel.generatedQrBitmap.observe(this) { bitmap ->
            if (bitmap != null) {
                // A. Hide PIN, Show QR
                hideKeyboard()
                binding.pinInput.visibility = View.GONE
                binding.qrImage.visibility = View.VISIBLE
                binding.qrImage.setImageBitmap(bitmap)

                // B. Update Buttons
                (dialog as? AlertDialog)?.let { d ->
                    d.getButton(AlertDialog.BUTTON_POSITIVE).visibility = View.GONE
                    d.setTitle("QR")
                    val closeBtn = d.getButton(AlertDialog.BUTTON_NEGATIVE)
                    closeBtn.text = getString(android.R.string.ok)
                    closeBtn.setOnClickListener {
                        viewModel.clearQrResult()
                        dismissAllowingStateLoss()
                    }
                }
            }
        }

        return AlertDialog.Builder(requireActivity())
            .setTitle(getString(R.string.pin_required))
            .setView(binding.root)
            .setPositiveButton("OK") { _, _ ->
                // Overridden in onStart
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
    }

    override fun onStart() {
        super.onStart()
        // Override Positive Button to prevent auto-close on error
        (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val pin = binding.pinInput.text?.toString().orEmpty()

            val documentId = requireArguments().getInt("documentId")
            val userId = SessionManager.getUserId(requireContext())

            if (pin.isBlank()) {
                toast(getString(R.string.pin_required))
            } else if (userId != null && documentId != 0) {
                viewModel.getQrCode(userId, documentId, pin)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(documentId: Int) = ShowQrDialogFragment().apply {
            arguments = Bundle().apply { putInt("documentId", documentId) }
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        // Uses the token from the PIN input field
        imm.hideSoftInputFromWindow(binding.pinInput.windowToken, 0)
    }
    private fun toast(msg: String) =
        Toast.makeText(requireActivity(), msg, Toast.LENGTH_SHORT).show()
}