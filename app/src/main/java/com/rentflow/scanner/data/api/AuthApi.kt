package com.rentflow.scanner.data.api

import com.rentflow.scanner.domain.model.User
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {
    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<LoginResponse>>

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<ApiResponse<LoginResponse>>

    @GET("api/v1/auth/me")
    suspend fun me(): Response<ApiResponse<User>>
}
