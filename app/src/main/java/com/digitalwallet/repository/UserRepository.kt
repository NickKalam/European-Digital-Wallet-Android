package com.digitalwallet.repository

import android.util.Patterns
import at.favre.lib.crypto.bcrypt.BCrypt
import com.digitalwallet.db.AppDatabase
import com.digitalwallet.model.AuthLog
import com.digitalwallet.model.AuthStatus
import com.digitalwallet.model.User

class UserRepository(private val db: AppDatabase) {
    suspend fun register(name: String, email: String, phone: String?, pin: String): Result<Long> {
        val exists = db.userDao().findByEmail(email.trim().lowercase()) != null || (phone != null && db.userDao()
            .findByPhone(phone) != null)
        if (exists) return Result.failure(Exception("USER_EXISTS"))

        if (pin.length !in 4..8 || !pin.all { it.isDigit() })
            return Result.failure(Exception("PIN_RULES"))

        val hash = BCrypt.withDefaults().hashToString(10, pin.toCharArray())
        val user = User(name = name, email = email.trim().lowercase(), phone = phone, pinHash = hash)
        val id = db.userDao().insert(user)
        return Result.success(id)
    }

    suspend fun login(emailOrPhone: String, pin: String, ip: String? = null): Result<User> {

        val user = findByEmailOrPhone(emailOrPhone.trim().lowercase())
            ?: return Result.failure(Exception("INVALID_CREDENTIALS"))

        val fifteenMinsAgo = System.currentTimeMillis() - 15 * 60 * 1000
        val failCount = db.authLogDao().countRecentFailedAttempts(user.userId, fifteenMinsAgo)
        if (failCount >= 3) return Result.failure(Exception("ACCOUNT_LOCKED"))

        val result = BCrypt.verifyer().verify(pin.toCharArray(), user.pinHash)
        val status = if (result.verified) AuthStatus.SUCCESS else AuthStatus.FAILED
        db.authLogDao().insert(AuthLog(userId = user.userId, status = status, ipAddress = ip))

        return if (result.verified) Result.success(user) else Result.failure(Exception("INVALID_CREDENTIALS"))

    }

    suspend fun verifyIdentity(emailOrPhone: String): Result<User> {
        val user =
            findByEmailOrPhone(emailOrPhone.trim().lowercase()) ?: return Result.failure(Exception("USER_NOT_FOUND"))
        return Result.success(user)
    }

    suspend fun changePin(emailOrPhone: String, oldPin: String, newPin: String): Result<Boolean> {
        val user =
            findByEmailOrPhone(emailOrPhone.trim().lowercase()) ?: return Result.failure(Exception("USER_NOT_FOUND"))


        if (newPin.length !in 4..8 || !newPin.all { it.isDigit() }) {
            return Result.failure(Exception("PIN_RULES"))
        }
        if (BCrypt.verifyer().verify(newPin.toCharArray(), user.pinHash).verified) {
            return Result.failure(Exception("PIN_SAME_AS_OLD"))
        }

        val verified = BCrypt.verifyer().verify(oldPin.toCharArray(), user.pinHash).verified
        if (!verified) return Result.failure(Exception("INVALID_OLD_PIN"))

        val newHash = BCrypt.withDefaults()
            .hashToString(10, newPin.toCharArray())

        val updated = db.userDao().updatePin(user.userId, newHash)
        return if (updated > 0) Result.success(true) else Result.failure(Exception("UPDATE_FAILED"))
    }

    suspend fun deleteUser(userId: Int):Result<Boolean>
    {
        return try {
            val user = db.userDao().getById(userId) ?: return Result.failure(Exception("USER_NOT_FOUND"))
            val deleted = db.userDao().delete(user)
            if (deleted > 0) Result.success(true)
            else Result.failure(Exception("DELETE_FAILED"))
        }
        catch (e: Exception) {
            Result.failure(e)
        }
    }
    private suspend fun findByEmailOrPhone(emailOrPhone: String): User? {
        val isEmail = Patterns.EMAIL_ADDRESS.matcher(emailOrPhone.trim().lowercase()).matches()
        val isPhone = Patterns.PHONE.matcher(emailOrPhone.trim().lowercase()).matches()
        if (isPhone)
            return db.userDao().findByPhone(emailOrPhone.trim().lowercase())
        else if (isEmail)
            return db.userDao().findByEmail(emailOrPhone.trim().lowercase())

        return null
    }
}
