package com.rentflow.scanner.data.api

data class ApiResponse<T>(
    val data: T?,
    val message: String?,
)

data class LoginResponse(
    val access_token: String,
    val refresh_token: String,
    val expires_in: Int,
    val token_type: String,
)

data class LoginRequest(
    val email: String,
    val password: String,
)

data class RefreshRequest(
    val refresh_token: String,
)

data class ScanRequest(
    val barcode: String,
    val scan_type: String,
    val user_id: String,
    val device_id: String,
    val device_type: String = "scanner",
    val project_id: String? = null,
    val location_id: String? = null,
    val notes: String? = null,
)

data class SessionCreateRequest(
    val type: String,
    val project_id: String? = null,
    val location_id: String? = null,
)
