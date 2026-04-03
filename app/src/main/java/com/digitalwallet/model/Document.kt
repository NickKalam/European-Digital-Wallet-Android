package com.digitalwallet.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"]), Index(value = ["qrCode"], unique = true)]
)
data class Document(
    @PrimaryKey(autoGenerate = true) val documentId: Int = 0,
    val userId: Int,
    val type: DocumentType,
    val issuer: String,
    val issueDate: Long,
    val expiryDate: Long,
    val qrCode: String,
    val status: DocumentStatus = DocumentStatus.VALID,
    val createdAt: Long = System.currentTimeMillis()
)
