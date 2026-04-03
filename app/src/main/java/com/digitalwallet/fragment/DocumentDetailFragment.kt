package com.digitalwallet.fragment

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.digitalwallet.R
import com.digitalwallet.databinding.FragmentDocumentDetailBinding
import com.digitalwallet.model.DocumentStatus
import com.digitalwallet.model.DocumentType
import com.digitalwallet.util.SessionManager
import com.digitalwallet.viewmodel.DocumentViewModel
import androidx.core.net.toUri
import java.time.*
import java.time.temporal.ChronoUnit

class DocumentDetailFragment : Fragment() {
    private var _binding: FragmentDocumentDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DocumentViewModel by activityViewModels()
    private val args: DocumentDetailFragmentArgs by navArgs()

    private var docPhotoUri: String? =null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDocumentDetailBinding.inflate(inflater, container, false)
        val documentId = args.documentId
        val userId = SessionManager.getUserId(requireContext())
        if (userId != null && documentId != 0) {
            viewModel.loadDocumentWithDetails(userId, documentId)
        } else {
            findNavController().navigate(R.id.action_documentsFragment_to_loginFragment)
        }

        binding.deleteButton.setOnClickListener {
            val context = requireContext()
            val input = EditText(context).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or
                        InputType.TYPE_NUMBER_VARIATION_PASSWORD
            }
            AlertDialog.Builder(context)
                .setTitle(getString(R.string.pin_required))
                .setView(input)
                .setPositiveButton(getString(R.string.delete)) { _, _ ->
                    val pin = input.text.toString()
                    docPhotoUri=when(viewModel.fullDetail.value?.base?.type)
                    {
                        DocumentType.ID_CARD->viewModel.fullDetail.value?.id?.ownerPhotoUri
                        DocumentType.DRIVER_LICENSE->viewModel.fullDetail.value?.driver?.ownerPhotoUri
                        else->null
                    }
                    if (userId != null && documentId != 0) {
                        viewModel.deleteDocument(userId, documentId, pin)
                    }
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }

        viewModel.deleteResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            if ( result.getOrNull() == true && result.isSuccess ) {
                docPhotoUri?.let{uriStr->
                    try{
                        val uri = uriStr.toUri()
                        requireContext().contentResolver.delete(uri, null, null)
                    }
                    catch(e: Exception)
                    {
                        /*no-op*/
                    }
                }
                docPhotoUri = null
                viewModel.clearDeleteResult()
                findNavController().navigate(R.id.action_documentDetailFragment_to_documentsFragment)
            } else if (result.isFailure) {
                docPhotoUri = null
                val msg = when (result.exceptionOrNull()?.message) {
                        "USER_NOT_FOUND" -> getString(R.string.user_not_found_error)
                        "INVALID_PIN" -> getString(R.string.invalid_pin_error)
                        "DOC_NOT_FOUND" -> getString(R.string.document_not_found)
                        else -> getString(R.string.generic_error)
                    }
                toast(msg)
                viewModel.clearDeleteResult()
            }
        }

        // New: observe full detail wrapper
        viewModel.fullDetail.observe(viewLifecycleOwner) { full ->
            val doc = full?.base ?: return@observe

            binding.typeText.text = getString(R.string.type, doc.type)
            binding.issuerText.text = getString(R.string.issuer, doc.issuer)
            binding.issueDateText.text = getString(R.string.issue_date, formatDate(doc.issueDate))
            binding.expiryDateText.text = getString(R.string.expiry_date, formatDate(doc.expiryDate))
            binding.statusText.text = getString(R.string.status, doc.status)
            binding.editButton.isEnabled = doc.status == DocumentStatus.EXPIRED
            // Hide all groups first
            binding.idDetailGroup.visibility = View.GONE
            binding.driverDetailGroup.visibility = View.GONE
            binding.ticketDetailGroup.visibility = View.GONE

            when (doc.type) {
                DocumentType.ID_CARD -> {
                    binding.idDetailGroup.visibility = View.VISIBLE
                    val d = full.id
                    binding.idFullNameText.text =
                        getString(R.string.full_name, d?.fullName ?: "-")
                    binding.idNationalityText.text =
                        getString(R.string.nationality, d?.nationality ?: "-")
                    binding.idNumberText.text =
                        getString(R.string.id_number, d?.idNumber ?: "-")
                    binding.idDobText.text =
                        getString(R.string.date_of_birth, d?.dateOfBirth ?: "-")

                    val photo = full.id?.ownerPhotoUri
                    if (!photo.isNullOrBlank()) {
                        binding.idOwnerPhoto.setImageURI(photo.toUri())
                    } else {
                        binding.idOwnerPhoto.setImageResource(R.drawable.ic_person_placeholder)
                    }
                }

                DocumentType.DRIVER_LICENSE -> {
                    binding.driverDetailGroup.visibility = View.VISIBLE
                    val d = full.driver
                    binding.dlFullNameText.text =
                        getString(R.string.full_name, d?.fullName ?: "-")
                    binding.dlNumberText.text =
                        getString(R.string.license_number, d?.licenseNumber ?: "-")
                    binding.dlCategoriesText.text =
                        getString(R.string.categories, d?.categories ?: "-")
                    binding.dlDobText.text =
                        getString(R.string.date_of_birth, d?.dateOfBirth ?: "-")

                    val photo = full.driver?.ownerPhotoUri
                    if (!photo.isNullOrBlank()) {
                        binding.dlOwnerPhoto.setImageURI(photo.toUri())
                    } else {
                        binding.dlOwnerPhoto.setImageResource(R.drawable.ic_person_placeholder)
                    }
                }

                DocumentType.TICKET -> {
                    binding.ticketDetailGroup.visibility = View.VISIBLE
                    val d = full.ticket
                    binding.tkEventText.text =
                        getString(R.string.event, d?.eventName ?: "-")
                    binding.tkEventDateText.text =
                        getString(R.string.event_date, d?.eventDate ?: "-")
                    binding.tkSeatText.text =
                        getString(R.string.seat, d?.seat ?: "-")
                    binding.tkVenueText.text =
                        getString(R.string.venue, d?.venue ?: "-")
                }
            }
        }

        binding.showQrButton.setOnClickListener {
            val dialog = ShowQrDialogFragment.newInstance(documentId)
            dialog.show(parentFragmentManager, "ShowQrDialog")
        }

        binding.editButton.setOnClickListener {
            val doc = viewModel.fullDetail.value?.base ?: return@setOnClickListener

            // 1) Use LocalDate to get the current date
            val now = LocalDate.now()

            // 2) Initialize DatePickerDialog using java.time values
            // Note: DatePicker expects months 0-11, but LocalDate uses 1-12, so subtract 1.
            val dlg = DatePickerDialog(requireContext(), { _, y, m, d ->

                // 3) Convert the picked date back to a timestamp (Long)
                val pickedDate = LocalDate.of(y, m + 1, d)

                // Convert to milliseconds at the start of the day in the system's timezone
                val pickedMillis = pickedDate.atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()

                // 4) Ask for PIN
                val pinInput = EditText(requireContext()).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER or
                            InputType.TYPE_NUMBER_VARIATION_PASSWORD
                    hint = "PIN"
                }

                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.pin_required))
                    .setView(pinInput)
                    .setPositiveButton("OK") { _, _ ->
                        val pin = pinInput.text.toString()
                        if (pin.isBlank()) {
                            toast(getString(R.string.invalid_pin_error))
                            return@setPositiveButton
                        }
                        // 5) Call VM with the new 'pickedMillis'

                        val currentUserId = SessionManager.getUserId(requireContext())
                        if (currentUserId != null) {
                            viewModel.updateExpiryDate(currentUserId, doc.documentId, pickedMillis, pin)
                        }
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()

            }, now.year, now.monthValue - 1, now.dayOfMonth)

            // 6) Set minDate to "tomorrow"
            dlg.datePicker.minDate = Instant.now()
                .plus(1, ChronoUnit.DAYS)
                .toEpochMilli()

            dlg.show()
        }

        // Observe update result (show message & clear)
        viewModel.updateExpiryResult.observe(viewLifecycleOwner) { res ->
            res ?: return@observe
            val msg = when {
                res.isSuccess -> getString(R.string.expiry_updated_success)
                else -> when (res.exceptionOrNull()?.message) {
                    "USER_NOT_FOUND" -> getString(R.string.user_not_found_error)
                    "INVALID_PIN" -> getString(R.string.invalid_pin_error)
                    "DOC_NOT_FOUND" -> getString(R.string.document_not_found)
                    "EXPIRY_BEFORE_ISSUE" -> getString(R.string.expiry_before_issue_error)
                    "EXPIRY_NOT_LATER" -> getString(R.string.expiry_not_later_error)
                    else -> getString(R.string.update_failed)
                }
            }
            toast(msg)
            binding.editButton.isEnabled=false
            viewModel.clearUpdateExpiryResult()
        }


        return binding.root
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    private fun formatDate(timestamp: Long): String {
        return Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
    }
}

