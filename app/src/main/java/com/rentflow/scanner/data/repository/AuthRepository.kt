package com.rentflow.scanner.data.repository

import com.rentflow.scanner.data.api.AuthApi
import com.rentflow.scanner.data.api.LoginRequest
import com.rentflow.scanner.data.preferences.TokenManager
import com.rentflow.scanner.domain.model.User
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val tokenManager: TokenManager,
) {
    suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            val response = authApi.login(LoginRequest(email, password))
            if (response.isSuccessful && response.body()?.data != null) {
                val data = response.body()!!.data!!
                tokenManager.saveTokens(data.access_token, data.refresh_token, "")
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Login failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCurrentUser(): Result<User> {
        return try {
            val response = authApi.me()
            if (response.isSuccessful && response.body()?.data != null) {
                Result.success(response.body()!!.data!!)
            } else {
                Result.failure(Exception("Failed to get user"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isLoggedIn(): Boolean = tokenManager.hasTokens()

    fun logout() {
        tokenManager.clearTokens()
    }
}
