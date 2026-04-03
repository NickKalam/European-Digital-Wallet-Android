package com.digitalwallet.viewmodel

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digitalwallet.model.User
import com.digitalwallet.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlin.random.Random

class AuthViewModel(private val userRepository: UserRepository) : ViewModel() {
    val loginResult = MutableLiveData<Result<User>?>()
    val registerResult = MutableLiveData<Result<Long>?>()
    val deleteUserResult=MutableLiveData<Result<Boolean>?>()
    val verifyIdentityResult = MutableLiveData<Result<User>?>()
    val changePinResult = MutableLiveData<Result<Boolean>?>()

    fun register(name: String, email: String, phone: String?, pin: String) {
        viewModelScope.launch {
            registerResult.value = userRepository.register(name, email, phone, pin)
        }
    }

    fun login(emailOrPhone: String, pin: String, ip: String? = null) {
        viewModelScope.launch {
            loginResult.value = userRepository.login(emailOrPhone, pin, ip)
        }
    }

    fun deleteUser(userId: Int) {
        viewModelScope.launch {
            try {
                // 1. Try to delete from Firebase
                val firebaseUser = FirebaseAuth.getInstance().currentUser
                firebaseUser?.delete()?.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("DeleteUser", "Firebase user deleted successfully.")
                    } else {
                        Log.e("DeleteUser", "Firebase delete failed: ${task.exception?.message}")
                    }
                }

                // 2. Delete from local Room DB
                val result = userRepository.deleteUser(userId)
                deleteUserResult.value=result
            } catch (e: Exception) {
                deleteUserResult.value=Result.failure(e)
            }
        }
    }

    fun verifyIdentity(emailOrPhone: String) {
        viewModelScope.launch {
            verifyIdentityResult.value = null
            verifyIdentityResult.value = userRepository.verifyIdentity(emailOrPhone)
        }
    }

    suspend fun verifyIdentityNow(emailOrPhone: String): Boolean {
        val result = userRepository.verifyIdentity(emailOrPhone)
        return result.isSuccess
    }

    fun changePin(emailOrPhone: String, oldPin: String, newPin: String) {
        viewModelScope.launch {
            changePinResult.value = null
            changePinResult.value = userRepository.changePin(emailOrPhone, oldPin, newPin)
        }
    }


    fun clearLoginResult()
    {
        loginResult.value=null
    }

    fun clearRegisterResult()
    {
        registerResult.value=null
    }
    fun clearVerifyIdentity() {
        verifyIdentityResult.value = null
    }

    fun clearDeleteUserResult() {
        deleteUserResult.value = null
    }
    fun clearChangePin() {
        changePinResult.value = null
    }

    //Phone verification code generator
    fun generateSixDigitCode(): String =
        (100000..999999).random(Random(System.currentTimeMillis())).toString()
}
