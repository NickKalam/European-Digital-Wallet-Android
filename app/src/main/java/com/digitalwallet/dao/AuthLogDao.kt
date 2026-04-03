package com.digitalwallet.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.digitalwallet.model.AuthLog

@Dao
interface AuthLogDao {
    @Insert
    suspend fun insert(authLog: AuthLog): Long

    @Query(
        """
        SELECT COUNT(*) FROM authlogs 
        WHERE userId = :userId AND status = 'FAILED' 
        AND loginTimestamp > :sinceTime
        AND loginTimestamp > (
        SELECT COALESCE(MAX(loginTimestamp), 0) 
        FROM authlogs 
        WHERE userId = :userId AND status = 'SUCCESS'
    )
    """
    )
    suspend fun countRecentFailedAttempts(userId: Int, sinceTime: Long): Int
}
