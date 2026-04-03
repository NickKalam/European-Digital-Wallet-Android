package com.digitalwallet.viewmodel

import android.graphics.Bitmap
import androidx.core.graphics.scale
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digitalwallet.model.Document
import com.digitalwallet.model.DocumentType
import com.digitalwallet.model.DocumentFull
import com.digitalwallet.model.DriverLicenseDetails
import com.digitalwallet.model.IdCardDetails
import com.digitalwallet.model.TicketDetails
import com.digitalwallet.repository.DocumentRepository
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

class DocumentViewModel(private val repo: DocumentRepository) : ViewModel() {

    // List
    val documents = MutableLiveData<List<Document>>()

    // QR
    private val _scannedQrContent = MutableLiveData<String?>()
    val scannedQrContent: LiveData<String?> = _scannedQrContent
    private val _generatedQrBitmap = MutableLiveData<Bitmap?>()
    val generatedQrBitmap: LiveData<Bitmap?> = _generatedQrBitmap
    val qrCodeResult = MutableLiveData<Result<String>?>()

    // Add / Delete
    val addResult = MutableLiveData<Result<Long>?>()
    val deleteResult = MutableLiveData<Result<Boolean>?>()





    private val _fullDetail = MutableLiveData<DocumentFull?>()
    val fullDetail: LiveData<DocumentFull?> = _fullDetail

    //  List
    fun loadUserDocuments(userId: Int) {
        viewModelScope.launch {
            repo.updateDocumentValidity(userId)
            documents.value = repo.getDocumentsByUser(userId)
        }
    }


    //  New: base + typed details
    fun loadDocumentWithDetails(userId: Int, documentId: Int) {
        viewModelScope.launch {
            _fullDetail.value = repo.getDocumentWithDetails(userId,documentId)
        }
    }


    fun addDocumentWithDetails(
        base: Document,
        pin: String,
        id: IdCardDetails? = null,
        driver: DriverLicenseDetails? = null,
        ticket: TicketDetails? = null
    ) {
        viewModelScope.launch {
            val res = repo.addDocument(
                base = base,
                userId = base.userId,
                pin = pin,
                id = id,
                driver = driver,
                ticket = ticket
            )
            if (res.isSuccess) {
                loadUserDocuments(base.userId) // refresh list
            }
            addResult.value = res
        }
    }

    fun clearAddResult()
    {
        addResult.value=null
    }
    //  QR

    fun generateQrBitmap(content: String) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val bmp = com.journeyapps.barcodescanner.BarcodeEncoder()
                    .encodeBitmap(content, com.google.zxing.BarcodeFormat.QR_CODE, 800, 800)
                _generatedQrBitmap.postValue(bmp)
            } catch (e: Exception) {
                _generatedQrBitmap.postValue(null)
                qrCodeResult.postValue(Result.failure(Exception("QR_RENDER_ERROR")))
            }
        }
    }

    fun getQrCode(userId: Int, documentId: Int, pin: String) {
        viewModelScope.launch {
            qrCodeResult.value = repo.getDocumentQrCode(userId, documentId, pin)
        }
    }

    fun clearQrResult() {
        qrCodeResult.value = null
        _generatedQrBitmap.value = null
    }


    //  Delete 
    fun deleteDocument(userId: Int, documentId: Int, pin: String) {
        viewModelScope.launch {
            deleteResult.value = repo.deleteDocument(userId, documentId, pin)
        }
    }

    fun clearDeleteResult() {
        deleteResult.value = null
    }

    val updateExpiryResult = MutableLiveData<Result<Boolean>?>()

    fun updateExpiryDate(userId: Int, documentId: Int, newExpiry: Long, pin: String) {
        viewModelScope.launch {
            updateExpiryResult.value = null
            updateExpiryResult.value = repo.updateDocumentExpiry(userId, documentId, newExpiry, pin)
            // refresh details so UI shows the new date
            loadDocumentWithDetails(userId, documentId)
        }
    }

    fun clearUpdateExpiryResult() {
        updateExpiryResult.value = null
    }

    fun validateInput(type:DocumentType, issueDate: Long,
                      expiryDate: Long , dobMillis: Long?,
                      categories:String?, eventDate:Long?):String?{
        if (issueDate>=expiryDate) return "DATE_ERROR"

        when (type) {
            DocumentType.ID_CARD -> {
                if (dobMillis!=null && asLocalDate(dobMillis).isAfter(asLocalDate(System.currentTimeMillis()))) {
                    return "INVALID_DOB_ERROR"
                }
            }
            DocumentType.DRIVER_LICENSE -> {
                if (dobMillis!=null &&!isAtLeast18(dobMillis)) {
                     return "DRIVER_AGE_ERROR"
                }
                if (categories!=null && !validateCategories(categories)) {
                    return "INVALID_CATEGORIES_ERROR"
                }
            }
            DocumentType.TICKET -> {
                if (eventDate!=null && !isInFuture(eventDate)) {
                    return "EVENT_DATE_ERROR"
                }
            }
        }
        return null
    }
    private fun asLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()


    private fun isAtLeast18(dobMillis: Long, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val dob = asLocalDate(dobMillis)
        val today = asLocalDate(nowMillis)
        return Period.between(dob, today).years >= 18
    }

    private fun isInFuture(millis: Long, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val date = asLocalDate(millis)
        val today = asLocalDate(nowMillis)
        return date.isAfter(today)
    }

    private val validCategories = setOf(
        "AM", "A1", "A2", "A", "B", "BE", "C1", "C1E", "C", "CE", "D1", "D1E", "D", "DE"
    )

    private fun validateCategories(input: String): Boolean {
        val parts = input.split(",", " ").map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        return parts.isNotEmpty() && parts.all { it in validCategories }
    }


    //  QR decoding (ZXing, from Bitmap)
     fun decodeQrFromBitmap(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.Default) {
            val scaled = scaleDownIfNeeded(bitmap)
            val pixels = IntArray(scaled.width * scaled.height)
            scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)

            val source = RGBLuminanceSource(scaled.width, scaled.height, pixels)
            val binary = BinaryBitmap(HybridBinarizer(source))
            val reader = MultiFormatReader()

            var res: String? = null
            try {
                res = reader.decode(binary).text
            } catch (_: Exception) {
                // one more pass with TRY_HARDER
                try {
                    val hints = mapOf(
                        com.google.zxing.DecodeHintType.TRY_HARDER to java.lang.Boolean.TRUE,
                        com.google.zxing.DecodeHintType.POSSIBLE_FORMATS to listOf(
                            com.google.zxing.BarcodeFormat.QR_CODE
                        )
                    )
                    reader.setHints(hints)
                    res = reader.decode(binary).text
                } catch (_: Exception) {
                }
            } finally {
                if (scaled !== bitmap) scaled.recycle()
            }
            _scannedQrContent.postValue(res)
        }
    }

    fun clearScannedQRContent()
    {
        _scannedQrContent.value=null
    }
    private val maxSide:Int = 1280
    private fun scaleDownIfNeeded(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val max = maxOf(w, h)
        if (max <= maxSide) return src
        val ratio = maxSide.toFloat() / max
        return src.scale(
            (w * ratio).toInt().coerceAtLeast(1),
            (h * ratio).toInt().coerceAtLeast(1)
        )
    }

    //AddDocumentFragment inputs for state preservation
    var issueDate: Long = System.currentTimeMillis()
    var expiryDate: Long = System.currentTimeMillis()
    var idDateOfBirth: Long = System.currentTimeMillis()
    var dlDateOfBirth: Long = System.currentTimeMillis()
    var tkEventDate: Long = System.currentTimeMillis()
    var decodedQr: String? = null
    var idOwnerPhotoUri: String? = null
    var dlOwnerPhotoUri: String? = null

    fun clearAddDocumentFragmentState()
    {
        issueDate= System.currentTimeMillis()
        expiryDate= System.currentTimeMillis()
        idDateOfBirth= System.currentTimeMillis()
        dlDateOfBirth= System.currentTimeMillis()
        tkEventDate=System.currentTimeMillis()
        decodedQr = null
        idOwnerPhotoUri= null
        dlOwnerPhotoUri= null
    }

}
