package com.digitalwallet.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import at.favre.lib.crypto.bcrypt.BCrypt
import com.digitalwallet.db.AppDatabase
import com.digitalwallet.model.Document
import com.digitalwallet.model.DocumentFull
import com.digitalwallet.model.DocumentStatus
import com.digitalwallet.model.DocumentType
import com.digitalwallet.model.DriverLicenseDetails
import com.digitalwallet.model.IdCardDetails
import com.digitalwallet.model.TicketDetails

class DocumentRepository(private val db: AppDatabase) {


    //  Base documents 
    suspend fun getDocumentsByUser(userId: Int): List<Document> {
        return db.documentDao().getDocumentsByUser(userId)
    }

    suspend fun updateDocumentValidity(userId: Int) {
        val currentTime = System.currentTimeMillis()
        val documents = db.documentDao().getDocumentsByUser(userId)
        documents.forEach { doc ->
            if (doc.status == DocumentStatus.VALID && currentTime > doc.expiryDate) {
                db.documentDao().update(doc.copy(status = DocumentStatus.EXPIRED))
            }
        }
    }

    suspend fun updateDocumentExpiry(
        userId: Int,
        documentId: Int,
        newExpiry: Long,
        pin: String
    ): Result<Boolean> {
        val user =
            db.userDao().getById(userId) ?: return Result.failure(Exception("USER_NOT_FOUND"))
        if (!BCrypt.verifyer().verify(pin.toCharArray(), user.pinHash).verified) {
            return Result.failure(Exception("INVALID_PIN"))
        }

        val doc = db.documentDao().getDocument(documentId, userId)
            ?: return Result.failure(Exception("DOC_NOT_FOUND"))

        // Guards: new expiry must be after issue date AND after current expiry
        if (newExpiry <= doc.issueDate) return Result.failure(Exception("EXPIRY_BEFORE_ISSUE"))
        if (newExpiry <= doc.expiryDate) return Result.failure(Exception("EXPIRY_NOT_LATER"))

        val updated =
            db.documentDao().updateExpiry(userId, documentId, newExpiry, DocumentStatus.VALID)
        return if (updated > 0) Result.success(true) else Result.failure(Exception("UPDATE_FAILED"))
    }

    suspend fun getDocument(userId: Int, documentId: Int): Document? {
        return db.documentDao().getDocument(documentId, userId)
    }

    suspend fun addDocument(
        base: Document,
        userId: Int,
        pin: String,
        id: IdCardDetails? = null,
        driver: DriverLicenseDetails? = null,
        ticket: TicketDetails? = null
    ): Result<Long> {
        val user =
            db.userDao().getById(userId) ?: return Result.failure(Exception("USER_NOT_FOUND"))

        if (!BCrypt.verifyer().verify(pin.toCharArray(), user.pinHash).verified) {
            return Result.failure(Exception("INVALID_PIN"))
        }

        if(base.type==DocumentType.ID_CARD && db.documentDao().isDuplicateIdCardIdNumber(id?.idNumber))
        {
            return Result.failure(Exception("ID_NUMBER_CONSTRAINT_ERROR"))
        }

        if(base.type==DocumentType.DRIVER_LICENSE  && db.documentDao().isDuplicateLicenseNumber(driver?.licenseNumber))
        {
            return Result.failure(Exception("LICENSE_NUMBER_CONSTRAINT_ERROR"))
        }
        return try {
            var newId: Long = -1L

            db.withTransaction {
                newId = db.documentDao().insert(base.copy(userId = userId))

                try {
                    when (base.type) {
                        DocumentType.ID_CARD -> {
                            id?.let { db.documentDao().upsertId(it.copy(documentId = newId.toInt())) }
                        }

                        DocumentType.DRIVER_LICENSE -> {
                            driver?.let {
                                db.documentDao().upsertDriver(it.copy(documentId = newId.toInt()))
                            }
                        }

                        DocumentType.TICKET -> {
                            ticket?.let {
                                db.documentDao().upsertTicket(it.copy(documentId = newId.toInt()))
                            }
                        }
                    }
                } catch (e: SQLiteConstraintException) {
                    throw e
                }
            }

            Result.success(newId)
        } catch (e: SQLiteConstraintException) {
           return Result.failure(Exception("QR_CONSTRAINT_ERROR"))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }


    suspend fun getDocumentQrCode(userId: Int, documentId: Int, pin: String): Result<String> {
        val user =
            db.userDao().getById(userId) ?: return Result.failure(Exception("USER_NOT_FOUND"))
        val ok = BCrypt.verifyer().verify(pin.toCharArray(), user.pinHash).verified
        if (!ok) return Result.failure(Exception("INVALID_PIN"))
        val doc = db.documentDao().getDocument(documentId, userId)
        return if (doc != null) Result.success(doc.qrCode)
        else Result.failure(Exception("DOC_NOT_FOUND"))
    }

    suspend fun deleteDocument(userId: Int, documentId: Int, pin: String): Result<Boolean> {
        val user =
            db.userDao().getById(userId) ?: return Result.failure(Exception("USER_NOT_FOUND"))
        val ok = BCrypt.verifyer().verify(pin.toCharArray(), user.pinHash).verified
        if (!ok) return Result.failure(Exception("INVALID_PIN"))
        val doc = db.documentDao().getDocument(documentId, userId)
        return if (doc != null) {
            db.documentDao().delete(doc)
            Result.success(true)
        } else Result.failure(Exception("DOC_NOT_FOUND"))
    }

    //  Details: get 

    suspend fun getDocumentWithDetails(userId: Int, docId: Int):DocumentFull?
    {
        val base=db.documentDao().getDocument(docId, userId) ?: return null

        return when(base.type)
        {
            DocumentType.ID_CARD ->
                DocumentFull(base, id = db.documentDao().getIdDetails(docId))

            DocumentType.DRIVER_LICENSE ->
                DocumentFull(base, driver = db.documentDao().getDriverDetails(docId))

            DocumentType.TICKET ->
                DocumentFull(base, ticket = db.documentDao().getTicketDetails(docId))
        }

    }
}
