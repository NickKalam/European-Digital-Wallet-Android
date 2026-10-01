package com.digitalwallet.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.digitalwallet.model.Document
import com.digitalwallet.model.DocumentStatus
import com.digitalwallet.model.DriverLicenseDetails
import com.digitalwallet.model.Event
import com.digitalwallet.model.IdCardDetails
import com.digitalwallet.model.TicketDetails
import com.digitalwallet.model.TicketWithEvent

@Dao
interface DocumentDao {

     
    @Query("SELECT * FROM documents WHERE userId = :userId")
    suspend fun getDocumentsByUser(userId: Int): List<Document>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(document: Document): Long

    @Delete
    suspend fun delete(document: Document)

    @Query("DELETE  FROM documents WHERE documentId = :docId")
    suspend fun deleteById(docId: Int)

    @Query("DELETE FROM events WHERE eventId NOT IN (SELECT DISTINCT eventId FROM ticket_details)")
    suspend fun deleteOrphanedEvents()

    @Query("SELECT * FROM documents WHERE documentId = :docId AND userId = :userId LIMIT 1")
    suspend fun getDocument(docId: Int, userId: Int): Document?

    @Query("""
    SELECT CASE 
        WHEN :idNumber IS NULL THEN 0
        ELSE EXISTS(SELECT 1 FROM id_card_details WHERE idNumber = :idNumber LIMIT 1)
    END
""")
    suspend fun isDuplicateIdCardIdNumber(idNumber : String?):Boolean

    @Query("""
    SELECT CASE 
        WHEN :licenseNumber IS NULL THEN 0
        ELSE EXISTS(SELECT 1 FROM driver_license_details WHERE licenseNumber = :licenseNumber LIMIT 1)
    END
""")
    suspend fun isDuplicateLicenseNumber(licenseNumber : String?):Boolean

    @Update
    suspend fun update(document: Document)

    @Query("UPDATE documents SET expiryDate = :newExpiry , status = :valid WHERE documentId = :documentId AND userId = :userId")
    suspend fun updateExpiry(
        userId: Int,
        documentId: Int,
        newExpiry: Long,
        valid: DocumentStatus
    ): Int

    @Query("SELECT * FROM events WHERE eventName = :name AND eventDate = :date AND venue = :venue LIMIT 1")
    suspend fun getEventByDetails(name: String?, date: String?, venue: String?): Event?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvent(event: Event): Long

    // Change the existing getTicketDetails:
    @Transaction
    @Query("SELECT * FROM ticket_details WHERE documentId = :docId")
    suspend fun getTicketWithEvent(docId: Int): TicketWithEvent?

    @Upsert
    suspend fun upsertId(details: IdCardDetails)

    @Upsert
    suspend fun upsertDriver(details: DriverLicenseDetails)

    @Upsert
    suspend fun upsertTicket(details: TicketDetails)

    // New detail getters 
    @Query("SELECT * FROM id_card_details WHERE documentId = :docId")
    suspend fun getIdDetails(docId: Int): IdCardDetails?

    @Query("SELECT * FROM driver_license_details WHERE documentId = :docId")
    suspend fun getDriverDetails(docId: Int): DriverLicenseDetails?

    @Query("SELECT * FROM ticket_details WHERE documentId = :docId")
    suspend fun getTicketDetails(docId: Int): TicketDetails?
}
