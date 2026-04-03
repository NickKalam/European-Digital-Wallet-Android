package com.digitalwallet.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Delete
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.digitalwallet.model.User

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun findByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE phone = :phone LIMIT 1")
    suspend fun findByPhone(phone: String): User?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: User): Long

    @Delete
    suspend fun delete(user: User): Int


    @Query("UPDATE users SET pinHash = :pinHash WHERE userId = :userId")
    suspend fun updatePin(userId: Int, pinHash: String): Int

    @Query("SELECT * FROM users WHERE userId = :userId")
    suspend fun getById(userId: Int): User?
}
