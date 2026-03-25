package com.rentflow.scanner.data.api

import com.rentflow.scanner.domain.model.Equipment
import com.rentflow.scanner.domain.model.ScanResult
import com.rentflow.scanner.domain.model.ScanSession
import retrofit2.Response
import retrofit2.http.*

interface ScannerApi {
    @GET("api/v1/scan/resolve/{barcode}")
    suspend fun resolveBarcode(@Path("barcode") barcode: String): Response<ApiResponse<Equipment>>

    @POST("api/v1/scan")
    suspend fun scan(@Body request: ScanRequest): Response<ApiResponse<ScanResult>>

    @POST("api/v1/scan/batch")
    suspend fun scanBatch(@Body requests: List<ScanRequest>): Response<ApiResponse<List<ScanResult>>>

    @POST("api/v1/scan/sync")
    suspend fun syncOffline(@Body requests: List<ScanRequest>): Response<ApiResponse<Unit>>

    @POST("api/v1/scanner/sessions")
    suspend fun createSession(@Body request: SessionCreateRequest): Response<ApiResponse<ScanSession>>

    @PUT("api/v1/scanner/sessions/{id}/end")
    suspend fun endSession(@Path("id") id: String): Response<ApiResponse<ScanSession>>

    @POST("api/v1/scanner/sessions/{id}/scan")
    suspend fun sessionScan(@Path("id") id: String, @Body request: ScanRequest): Response<ApiResponse<ScanResult>>

    @GET("api/v1/scanner/sessions/{id}/protocol")
    suspend fun sessionProtocol(@Path("id") id: String): Response<ApiResponse<List<ScanResult>>>

    @POST("api/v1/scanner/adhoc-booking")
    suspend fun adHocBooking(@Body request: AdHocBookingRequest): Response<ApiResponse<Equipment>>

    @POST("api/v1/scanner/devices/register")
    suspend fun registerDevice(@Body request: DeviceRegistration): Response<ApiResponse<Unit>>

    @GET("api/v1/scanner/devices/ring")
    suspend fun checkRingCommand(): Response<ApiResponse<RingCommandResponse>>

    @POST("api/v1/scanner/devices/ring-ack")
    suspend fun acknowledgeRing(): Response<ApiResponse<Unit>>

    @PUT("api/v1/scan/equipment/{id}/location")
    suspend fun updateLocation(@Path("id") id: String, @Body body: Map<String, String>): Response<ApiResponse<Unit>>
}
