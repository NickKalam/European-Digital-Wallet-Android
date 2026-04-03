package com.digitalwallet.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// New: full detail wrapper
data class DocumentFull(
    val base: Document,
    val id: IdCardDetails? = null,
    val driver: DriverLicenseDetails? = null,
    val ticket: TicketDetails? = null,
)

@Entity(
    tableName = "id_card_details",
    foreignKeys = [
        ForeignKey(
            entity = Document::class,
            parentColumns = ["documentId"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION
        )
    ],
    indices = [Index(value = ["documentId"], unique = true),Index(value=["idNumber"],unique=true)]
)
data class IdCardDetails(
    @PrimaryKey val documentId: Int,     // FK to documents.documentId
    val fullName: String? = null,
    val nationality: String? = null,
    val idNumber: String? = null,
    val dateOfBirth: String? = null,
    val ownerPhotoUri: String? = null
)

@Entity(
    tableName = "driver_license_details",
    foreignKeys = [
        ForeignKey(
            entity = Document::class,
            parentColumns = ["documentId"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION
        )
    ],
    indices = [Index(value = ["documentId"], unique = true),Index(value=["licenseNumber"],unique=true)]
)
data class DriverLicenseDetails(
    @PrimaryKey val documentId: Int,     // FK to documents.documentId
    val fullName: String? = null,
    val dateOfBirth: String? = null,
    val licenseNumber: String? = null,
    val categories: String? = null,
    val ownerPhotoUri: String? = null
)

@Entity(
    tableName = "ticket_details",
    foreignKeys = [
        ForeignKey(
            entity = Document::class,
            parentColumns = ["documentId"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION
        )
    ],
    indices = [Index(value = ["documentId"], unique = true)]
)
data class TicketDetails(
    @PrimaryKey val documentId: Int,     // FK to documents.documentId
    val eventName: String? = null,
    val eventDate: String? = null,
    val seat: String? = null,
    val venue: String? = null
)
