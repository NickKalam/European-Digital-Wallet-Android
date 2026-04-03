package com.digitalwallet.fragment

import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.digitalwallet.R
import com.digitalwallet.databinding.FragmentAddDocumentBinding
import com.digitalwallet.model.Document
import com.digitalwallet.model.DocumentStatus
import com.digitalwallet.model.DocumentType
import com.digitalwallet.model.DriverLicenseDetails
import com.digitalwallet.model.IdCardDetails
import com.digitalwallet.model.TicketDetails
import com.digitalwallet.repository.DocumentRepository
import com.digitalwallet.util.DatabaseProvider
import com.digitalwallet.util.SessionManager
import com.digitalwallet.viewmodel.DocumentViewModel
import com.digitalwallet.viewmodel.DocumentViewModelFactory
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil


class AddDocumentFragment : Fragment() {
    private var _binding: FragmentAddDocumentBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DocumentViewModel by activityViewModels {
        DocumentViewModelFactory(
            DocumentRepository(DatabaseProvider.getDatabase(requireContext()))
        )
    }


    // QR pickers
    // Capture QR as a Bitmap (no MediaStore file for QR)
    private val captureQrImage =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap == null) {
                toast(getString(R.string.no_qr_found))
            } else {
                viewModel.decodeQrFromBitmap(bitmap)
            }
        }

    private val pickQrImage = registerForActivityResult(
        PickVisualMedia()
    ) { uri ->
        if (uri == null) {
            toast(getString(R.string.no_qr_found)); return@registerForActivityResult
        }
        persistReadPermissionIfPossible(uri)

        lifecycleScope.launch(Dispatchers.IO){
            val bmp = loadBitmapFromUri(uri)
            withContext(Dispatchers.Main){
                if (bmp != null) {
                    viewModel.decodeQrFromBitmap(bmp)
                } else {
                    binding.qrStatusText.text = getString(R.string.decode_error)
                    toast(getString(R.string.decode_error))
                }
            }
        }
    }

    //  Owner Photo pickers (ID)
    private val pickIdPhotoFromFiles = registerForActivityResult(
        PickVisualMedia()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        persistReadPermissionIfPossible(uri)
        processImageUri(uri,"id",binding.idOwnerPhotoPreview,binding.idOwnerPhotoButton){savedUri->
            viewModel.idOwnerPhotoUri=savedUri
        }

    }

    private val captureIdPhotoFromCamera =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap == null) return@registerForActivityResult
            processCapturedBitmap(bitmap,"id",binding.idOwnerPhotoPreview,binding.idOwnerPhotoButton){savedUri->
                viewModel.idOwnerPhotoUri=savedUri
            }

        }

    //  Owner Photo pickers (DL)
    private val pickDlPhotoFromFiles = registerForActivityResult(
        PickVisualMedia()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        persistReadPermissionIfPossible(uri)
        processImageUri(uri,"dl",binding.dlOwnerPhotoPreview,binding.dlOwnerPhotoButton){savedUri->
            viewModel.dlOwnerPhotoUri=savedUri
        }

    }

    private val captureDlPhotoFromCamera =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap == null) return@registerForActivityResult
            processCapturedBitmap(bitmap,"dl",binding.dlOwnerPhotoPreview,binding.dlOwnerPhotoButton){savedUri->
                viewModel.dlOwnerPhotoUri=savedUri
            }

        }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddDocumentBinding.inflate(inflater, container, false)

        // Input hints
        setHints()

        // Type spinner
        val types = DocumentType.entries.map { it.name.replace('_', ' ') }
        binding.typeSpinner.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, types)

        binding.typeSpinner.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>, view: View?, pos: Int, id: Long
                ) {
                    val type = DocumentType.entries[pos]
                    binding.idGroup.visibility =
                        if (type == DocumentType.ID_CARD) View.VISIBLE else View.GONE
                    binding.driverGroup.visibility =
                        if (type == DocumentType.DRIVER_LICENSE) View.VISIBLE else View.GONE
                    binding.ticketGroup.visibility =
                        if (type == DocumentType.TICKET) View.VISIBLE else View.GONE
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
            }

        val euCountries = resources.getStringArray(R.array.eu_countries).toList()
        val nationalityAdapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, euCountries
        )
        binding.idNationalityInput.adapter = nationalityAdapter

        // Dates
        binding.issueDateInput.setOnClickListener {
            pickDate { date -> viewModel.issueDate = date; binding.issueDateInput.setText(formatDate(date)) }
        }
        binding.expiryDateInput.setOnClickListener {
            pickDate { date -> viewModel.expiryDate = date; binding.expiryDateInput.setText(formatDate(date)) }
        }
        binding.idDobInput.setOnClickListener {
            pickDate { millis -> viewModel.idDateOfBirth = millis; binding.idDobInput.setText(formatDate(viewModel.idDateOfBirth)) }
        }
        binding.dlDobInput.setOnClickListener {
            pickDate { millis -> viewModel.dlDateOfBirth = millis; binding.dlDobInput.setText(formatDate(viewModel.dlDateOfBirth)) }
        }
        binding.tkEventDateInput.setOnClickListener {
            pickDate { millis -> viewModel.tkEventDate = millis; binding.tkEventDateInput.setText(formatDate(viewModel.tkEventDate)) }
        }

        // QR: Files or Camera
        binding.pickQrButton.setOnClickListener {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.select_qr_source))
                .setItems(arrayOf(getString(R.string.from_files), getString(R.string.use_camera))) { _, which ->
                    when (which) {
                        0 -> pickQrImage.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                        1 -> ensureCameraPermissionThen { captureQrImage.launch(null) }
                    }
                }
                .show()
        }

        //  Owner Photo (ID): Files or Camera
        binding.idOwnerPhotoButton.setOnClickListener {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.select_owner_photo_source))
                .setItems(arrayOf(getString(R.string.from_files), getString(R.string.use_camera))) { _, which ->
                    when (which) {
                        0 -> pickIdPhotoFromFiles.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                        1 -> ensureCameraPermissionThen { captureIdPhotoFromCamera.launch(null) }
                    }
                }
                .show()
        }

        //  Owner Photo (DL): Files or Camera
        binding.dlOwnerPhotoButton.setOnClickListener {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.select_owner_photo_source))
                .setItems(arrayOf(getString(R.string.from_files), getString(R.string.use_camera))) { _, which ->
                    when (which) {
                        0 -> pickDlPhotoFromFiles.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                        1 -> ensureCameraPermissionThen { captureDlPhotoFromCamera.launch(null) }
                    }
                }
                .show()
        }

        //  Add with PIN
        binding.addButton.setOnClickListener {
            val userId = SessionManager.getUserId(requireContext())
            val type = DocumentType.entries[binding.typeSpinner.selectedItemPosition]
            val issuer = binding.issuerInput.text.toString().trim()

            if (userId == null) {
                toast(getString(R.string.user_no_login_error)); return@setOnClickListener
            }
            if (issuer.isBlank()
                || binding.issueDateInput.text.isNullOrBlank()
                || binding.expiryDateInput.text.isNullOrBlank()
            ) { toast(getString(R.string.empty_fields_error)); return@setOnClickListener }

            when (type) {
                DocumentType.ID_CARD -> {
                    if (binding.idDobInput.text.isNullOrBlank()
                        || binding.idOwnerPhotoButton.isEnabled
                        || binding.idFullNameInput.text.isNullOrBlank()
                        || binding.idNumberInput.text.isNullOrBlank()
                        || binding.idNationalityInput.selectedItem.toString().isBlank()
                    ) { toast(getString(R.string.empty_fields_error)); return@setOnClickListener }
                }
                DocumentType.DRIVER_LICENSE -> {
                    if (binding.dlDobInput.text.isNullOrBlank()
                        || binding.dlOwnerPhotoButton.isEnabled
                        || binding.dlFullNameInput.text.isNullOrBlank()
                        || binding.dlNumberInput.text.isNullOrBlank()
                        || binding.dlCategoriesInput.text.isNullOrBlank()
                    ) { toast(getString(R.string.empty_fields_error)); return@setOnClickListener }
                }
                DocumentType.TICKET -> {
                    if (binding.tkEventInput.text.isNullOrBlank()
                        || binding.tkEventDateInput.text.isNullOrBlank()
                        || binding.tkSeatInput.text.isNullOrBlank()
                        || binding.tkVenueInput.text.isNullOrBlank()
                    ) { toast(getString(R.string.empty_fields_error)); return@setOnClickListener }
                }
            }

            val decodedQr=viewModel.decodedQr
            if (decodedQr.isNullOrBlank()) {
                toast(getString(R.string.select_qr_first)); return@setOnClickListener
            }
            val dobToCheck= if(type==DocumentType.ID_CARD) viewModel.idDateOfBirth else viewModel.dlDateOfBirth
            val errorKey=viewModel.validateInput(type,viewModel.issueDate,viewModel.expiryDate,dobToCheck,
                binding.dlCategoriesInput.text.toString(),viewModel.tkEventDate)

            if(errorKey!=null)
            {
                val msg= when(errorKey)
                {
                    "DATE_ERROR"->getString(R.string.date_error)
                    "INVALID_DOB_ERROR"->getString(R.string.invalid_dob_error)
                    "DRIVER_AGE_ERROR"->getString(R.string.driver_age_error)
                    "INVALID_CATEGORIES_ERROR"->getString(R.string.invalid_categories)
                    "EVENT_DATE_ERROR"->getString(R.string.event_date_error)
                    else -> getString(R.string.generic_error)
                }
                toast(msg)
                return@setOnClickListener
            }
            val pinInput = android.widget.EditText(requireContext()).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
                hint = "PIN"
            }
            android.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.pin_required)
                .setView(pinInput)
                .setPositiveButton(getString(R.string.add)) { _, _ ->
                    val pin = pinInput.text.toString()
                    if (pin.isBlank()) { toast(getString(R.string.pin_required)); return@setPositiveButton }

                    val base = Document(
                        userId = userId,
                        type = type,
                        issuer = issuer,
                        issueDate = viewModel.issueDate,
                        expiryDate = viewModel.expiryDate,
                        qrCode = decodedQr,
                        status = DocumentStatus.VALID
                    )

                    when (type) {
                        DocumentType.ID_CARD -> {
                            val det = IdCardDetails(
                                documentId = 0,
                                fullName = binding.idFullNameInput.text?.toString(),
                                nationality = binding.idNationalityInput.selectedItem?.toString(),
                                idNumber = binding.idNumberInput.text?.toString(),
                                dateOfBirth = binding.idDobInput.text?.toString(),
                                ownerPhotoUri = viewModel.idOwnerPhotoUri
                            )
                            viewModel.addDocumentWithDetails(base, pin, id = det)
                        }
                        DocumentType.DRIVER_LICENSE -> {
                            val det = DriverLicenseDetails(
                                documentId = 0,
                                fullName = binding.dlFullNameInput.text?.toString(),
                                licenseNumber = binding.dlNumberInput.text?.toString(),
                                categories = binding.dlCategoriesInput.text?.toString()?.uppercase(),
                                dateOfBirth = binding.dlDobInput.text?.toString(),
                                ownerPhotoUri = viewModel.dlOwnerPhotoUri
                            )
                            viewModel.addDocumentWithDetails(base, pin, driver = det)
                        }
                        DocumentType.TICKET -> {
                            val det = TicketDetails(
                                documentId = 0,
                                eventName = binding.tkEventInput.text?.toString(),
                                seat = binding.tkSeatInput.text?.toString(),
                                venue = binding.tkVenueInput.text?.toString(),
                                eventDate = binding.tkEventDateInput.text?.toString()
                            )
                            viewModel.addDocumentWithDetails(base, pin, ticket = det)
                        }
                    }
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }

        //observe QR scanned result
        viewModel.scannedQrContent.observe(viewLifecycleOwner){ result ->
            if(result!=null){
                updateDecodedQr(result)
            }
            else
            {
                viewModel.decodedQr=null
                binding.qrStatusText.text = getString(R.string.no_qr_found)
            }
        }
        // Observe add result
        viewModel.addResult.observe(viewLifecycleOwner) { result ->
            if (result == null) return@observe
            if (result.isSuccess) {
                toast(getString(R.string.document_added_success))
                findNavController().navigateUp()
            } else {
                val key = result.exceptionOrNull()?.message
                if (key == "QR_CONSTRAINT_ERROR") {
                    binding.pickQrButton.isEnabled = true
                    binding.qrStatusText.text = getString(R.string.no_qr_selected)
                }
                val msg = when (key) {
                    "USER_NOT_FOUND" -> getString(R.string.user_not_found_error)
                    "INVALID_PIN" -> getString(R.string.invalid_pin_error)
                    "QR_CONSTRAINT_ERROR" -> getString(R.string.duplicate_qr_error)
                    "ID_NUMBER_CONSTRAINT_ERROR" -> getString(R.string.id_number_constraint_error)
                    "LICENSE_NUMBER_CONSTRAINT_ERROR" -> getString(R.string.license_number_constraint_error)
                    else -> getString(R.string.document_added_failure)
                }
                toast(msg)
            }
        }

        return binding.root
    }

    // Permissions
    private fun ensureCameraPermissionThen(launch: () -> Unit) {
        val perm = android.Manifest.permission.CAMERA
        when {
            ContextCompat.checkSelfPermission(requireContext(), perm) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED -> launch()

            shouldShowRequestPermissionRationale(perm) -> {
                android.app.AlertDialog.Builder(requireContext())
                    .setMessage(getString(R.string.camera_permission_required))
                    .setPositiveButton("OK") { _, _ -> requestCameraPermission.launch(perm) }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
            else -> requestCameraPermission.launch(perm)
        }
    }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) toast(getString(R.string.permission_required))
        }

    // Persistable read permission helper (safe no-op on non-persistable sources)
    private fun persistReadPermissionIfPossible(uri: Uri) {
        val cr = requireContext().contentResolver
        try {
            cr.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Photo Picker on API 33+ might not require/allow persist; ignore
        }
    }


    // Load a bitmap from Uri with downsampling + EXIF rotation (then is still decoded with ZXing)
    private fun loadBitmapFromUri(uri: Uri, targetMaxSide: Int = 2048): Bitmap? {
        val resolver = requireContext().contentResolver

        // 1) Bounds
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOptions) }

        val (srcW, srcH) = boundsOptions.outWidth to boundsOptions.outHeight
        if (srcW <= 0 || srcH <= 0) return null

        // 2) Compute sample size
        var inSampleSize = 1
        val maxSide = maxOf(srcW, srcH)
        if (maxSide > targetMaxSide) {
            inSampleSize = Integer.highestOneBit(ceil(maxSide / targetMaxSide.toDouble()).toInt())
        }

        // 3) Decode sampled
        val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
        var bmp: Bitmap? = null
        resolver.openInputStream(uri)?.use { bmp = BitmapFactory.decodeStream(it, null, decodeOptions) }
        if (bmp == null) return null

        // 4) Rotate per EXIF, if present
        val rotated = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> bmp!!.rotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> bmp!!.rotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> bmp!!.rotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> bmp!!.flip(horizontal = true)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> bmp!!.flip(horizontal = false)
                    else -> bmp!!
                }
            }
        }.getOrNull()

        if (rotated != null && rotated !== bmp) bmp?.recycle()
        return rotated ?: bmp
    }

    private fun Bitmap.rotate(degrees: Float): Bitmap {
        val m = android.graphics.Matrix()
        m.postRotate(degrees)
        return Bitmap.createBitmap(this, 0, 0, width, height, m, true)
    }

    private fun Bitmap.flip(horizontal: Boolean): Bitmap {
        val m = android.graphics.Matrix()
        if (horizontal) m.preScale(-1f, 1f) else m.preScale(1f, -1f)
        return Bitmap.createBitmap(this, 0, 0, width, height, m, true)
    }

    private fun updateDecodedQr(text: String) {
        viewModel.decodedQr = text
        binding.pickQrButton.isEnabled = false
        binding.qrStatusText.text = getString(R.string.qr_loaded)
    }

    //  Helpers (owner photo only)
    private fun saveBitmapToMediaStore(bitmap: Bitmap, displayName: String): Uri? {
        val resolver = requireContext().contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val itemUri = resolver.insert(collection, contentValues) ?: return null
        try {
            resolver.openOutputStream(itemUri, "w")?.use { out: OutputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)
            }
            return itemUri
        } catch (e: Exception) {
            runCatching { resolver.delete(itemUri, null, null) }
            return null
        }
    }

    private fun pickDate(onPicked: (Long) -> Unit) {
        val now = LocalDate.now()

        DatePickerDialog(
            requireContext(),
            { _, y, m, d ->
                val pickedDate = LocalDate.of(y, m + 1, d)
                val millis = pickedDate.atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                onPicked(millis)
            },
            now.year,
            now.monthValue - 1, // DatePicker expects 0-11 for months
            now.dayOfMonth
        ).show()
    }

    private fun formatDate(millis: Long): String {
        return Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString() // ISO-8601 (yyyy-MM-dd) by default
    }

    private fun processImageUri(uri: Uri,prefix:String,imageView: ImageView, button: View, onSaved:(String)->Unit )
    {
        lifecycleScope.launch(Dispatchers.IO) {
            val bitmap = loadBitmapFromUri(uri)

            if (bitmap != null) {
                val savedUri = saveBitmapToMediaStore(
                    bitmap,
                    prefix+"_owner_${System.currentTimeMillis()}.jpg")

                withContext(Dispatchers.Main) {
                    savedUri?.let {
                        val uriStr=savedUri.toString()
                        imageView.setImageURI(savedUri)
                        button.isEnabled = false
                        onSaved(uriStr)
                    }?: toast(getString(R.string.generic_error))

                }
            }
        }
    }
    private fun processCapturedBitmap(
        bitmap: Bitmap,
        prefix: String,
        imageView: ImageView,
        button: View,
        onSaved: (String) -> Unit
    )
    {
        lifecycleScope.launch(Dispatchers.IO) {
            val savedUri=saveBitmapToMediaStore(
                bitmap,
                prefix+"_owner_${System.currentTimeMillis()}.jpg"
            )
            withContext(Dispatchers.Main) {
                savedUri?.let {
                    val uriStr = savedUri.toString()
                    imageView.setImageURI(savedUri)
                    button.isEnabled = false
                    onSaved(uriStr)
                } ?: toast(getString(R.string.photo_capture_failed))
            }
        }
    }

    private fun setHints() {
        binding.issuerInput.hint = getString(R.string.issuer, "")
        binding.issueDateInput.hint = getString(R.string.issue_date, "")
        binding.expiryDateInput.hint = getString(R.string.expiry_date, "")
        binding.idFullNameInput.hint = getString(R.string.full_name, "")
        binding.idDobInput.hint = getString(R.string.date_of_birth, "")
        binding.nationalityLabel.text = getString(R.string.nationality, "")
        binding.idNumberInput.hint = getString(R.string.id_number, "")
        binding.dlFullNameInput.hint = getString(R.string.full_name, "")
        binding.dlDobInput.hint = getString(R.string.date_of_birth, "")
        binding.dlNumberInput.hint = getString(R.string.license_number, "")
        binding.dlCategoriesInput.hint = getString(R.string.categories, "")
        binding.tkEventInput.hint = getString(R.string.event, "")
        binding.tkEventDateInput.hint = getString(R.string.event_date, "")
        binding.tkSeatInput.hint = getString(R.string.seat, "")
        binding.tkVenueInput.hint = getString(R.string.venue, "")
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        //Delete ID / Driver's license photos if the user did not complete the process
        if( viewModel.addResult.value?.isSuccess != true)
        {
            viewModel.idOwnerPhotoUri?.let{uriStr->

                deletePicture(uriStr.toUri())
            }
            viewModel.dlOwnerPhotoUri?.let{uriStr->
                deletePicture(uriStr.toUri())
            }

        }
        viewModel.clearAddResult()
        viewModel.clearScannedQRContent()
        viewModel.clearAddDocumentFragmentState()
        _binding = null
    }

    private fun deletePicture(uri:Uri)
    {
        try{
            requireContext().contentResolver.delete(uri, null, null)
        }
        catch(e: Exception)
        {
            /*no-op*/
        }
    }
}
