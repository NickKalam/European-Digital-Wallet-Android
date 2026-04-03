package com.digitalwallet.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "authlogs",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"])]
)
data class AuthLog(
    @PrimaryKey(autoGenerate = true) val authId: Int = 0,
    val userId: Int,
    val loginTimestamp: Long = System.currentTimeMillis(),
    val ipAddress: String? = null,
    val status: AuthStatus
)
