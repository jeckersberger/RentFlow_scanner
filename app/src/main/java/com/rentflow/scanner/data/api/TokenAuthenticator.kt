package com.rentflow.scanner.data.api

import com.google.gson.Gson
import com.rentflow.scanner.data.preferences.TokenManager
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class TokenAuthenticator(
    private val tokenManager: TokenManager,
    private val baseUrl: String,
) : Authenticator {
    private val gson = Gson()
    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        val refreshToken = tokenManager.getRefreshToken() ?: return null

        synchronized(lock) {
            val currentToken = tokenManager.getAccessToken()
            val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            if (currentToken != null && currentToken != requestToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }

            val refreshBody = gson.toJson(RefreshRequest(refreshToken))
                .toRequestBody("application/json".toMediaType())
            val refreshRequest = Request.Builder()
                .url("${baseUrl}api/v1/auth/refresh")
                .post(refreshBody)
                .build()

            val client = OkHttpClient()
            val refreshResponse = client.newCall(refreshRequest).execute()
            if (!refreshResponse.isSuccessful) {
                tokenManager.clearTokens()
                return null
            }

            val body = refreshResponse.body?.string() ?: return null
            val apiResponse = gson.fromJson(body, ApiResponse::class.java)
            val dataJson = gson.toJson(apiResponse.data)
            val loginResponse = gson.fromJson(dataJson, LoginResponse::class.java)

            tokenManager.saveTokens(
                loginResponse.access_token,
                loginResponse.refresh_token,
                tokenManager.getTenantId() ?: "",
            )

            return response.request.newBuilder()
                .header("Authorization", "Bearer ${loginResponse.access_token}")
                .build()
        }
    }
}
