package com.rentflow.scanner.data.repository

import com.rentflow.scanner.data.api.AuthApi
import com.rentflow.scanner.data.api.LoginRequest
import com.rentflow.scanner.data.api.QrLoginRequest
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
        // Demo-Login für Tests ohne Backend
        if (email == "test@rentflow.de" && password == "test1234") {
            tokenManager.saveTokens("demo-access-token", "demo-refresh-token", "")
            return Result.success(Unit)
        }
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

    suspend fun qrLogin(qrToken: String): Result<Unit> {
        return try {
            val response = authApi.qrLogin(QrLoginRequest(qrToken))
            if (response.isSuccessful && response.body()?.data != null) {
                val data = response.body()!!.data!!
                tokenManager.saveTokens(data.access_token, data.refresh_token, "")
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.body()?.message ?: "QR-Login fehlgeschlagen"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCurrentUser(): Result<User> {
        if (tokenManager.getAccessToken() == "demo-access-token") {
            return Result.success(User("demo", "test@rentflow.de", "Test User", listOf("scanner"), "demo-tenant"))
        }
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
