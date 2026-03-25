package com.rentflow.scanner.data.repository

import android.content.Context
import com.rentflow.scanner.data.api.AdHocBookingRequest
import com.rentflow.scanner.data.api.ScanRequest
import com.rentflow.scanner.data.api.ScannerApi
import com.rentflow.scanner.data.api.SessionCreateRequest
import com.rentflow.scanner.data.db.PendingScanDao
import com.rentflow.scanner.data.db.PendingScanEntity
import com.rentflow.scanner.data.preferences.TokenManager
import com.rentflow.scanner.domain.model.Equipment
import com.rentflow.scanner.domain.model.EquipmentStatus
import com.rentflow.scanner.domain.model.ScanResult
import com.rentflow.scanner.domain.model.ScanSession
import com.rentflow.scanner.worker.SyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScannerRepository @Inject constructor(
    private val scannerApi: ScannerApi,
    private val pendingScanDao: PendingScanDao,
    private val tokenManager: TokenManager,
    @ApplicationContext private val context: Context,
) {
    suspend fun resolveBarcode(barcode: String): Result<Equipment> {
        // Always try server first
        return try {
            val response = scannerApi.resolveBarcode(barcode)
            if (response.isSuccessful && response.body()?.data != null) {
                Result.success(response.body()!!.data!!)
            } else {
                // Server error — fallback to demo data only in demo mode
                if (tokenManager.getAccessToken() == "demo-access-token") {
                    Result.success(demoEquipment(barcode))
                } else {
                    Result.failure(Exception(response.body()?.message ?: "Equipment not found"))
                }
            }
        } catch (e: Exception) {
            if (tokenManager.getAccessToken() == "demo-access-token") {
                Result.success(demoEquipment(barcode))
            } else {
                Result.failure(e)
            }
        }
    }

    private fun demoEquipment(barcode: String) = Equipment(
        id = "demo-${barcode}",
        barcode = barcode,
        name = "Equipment $barcode",
        category = "Demo",
        status = EquipmentStatus.AVAILABLE,
        location = "Lager",
        projectName = null,
        rfidTag = null,
        imageUrl = null,
    )

    suspend fun scan(barcode: String, scanType: String, projectId: String? = null, notes: String? = null): Result<ScanResult> {
        val request = ScanRequest(
            barcode = barcode,
            scan_type = scanType,
            user_id = "",
            device_id = android.os.Build.SERIAL,
            project_id = projectId,
            notes = notes,
        )
        return try {
            val response = scannerApi.scan(request)
            if (response.isSuccessful && response.body()?.data != null) {
                Result.success(response.body()!!.data!!)
            } else {
                queueOffline(barcode, scanType, projectId, notes)
                Result.failure(Exception(response.body()?.message ?: "Scan failed"))
            }
        } catch (e: Exception) {
            queueOffline(barcode, scanType, projectId, notes)
            Result.failure(e)
        }
    }

    suspend fun createSession(type: String, projectId: String? = null): Result<ScanSession> {
        return try {
            val response = scannerApi.createSession(SessionCreateRequest(type, projectId))
            if (response.isSuccessful && response.body()?.data != null) {
                Result.success(response.body()!!.data!!)
            } else {
                Result.failure(Exception("Failed to create session"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun endSession(sessionId: String): Result<ScanSession> {
        return try {
            val response = scannerApi.endSession(sessionId)
            if (response.isSuccessful && response.body()?.data != null) {
                Result.success(response.body()!!.data!!)
            } else {
                Result.failure(Exception("Failed to end session"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sessionScan(sessionId: String, barcode: String, scanType: String): Result<ScanResult> {
        val request = ScanRequest(
            barcode = barcode,
            scan_type = scanType,
            user_id = "",
            device_id = android.os.Build.SERIAL,
        )
        return try {
            val response = scannerApi.sessionScan(sessionId, request)
            if (response.isSuccessful && response.body()?.data != null) {
                Result.success(response.body()!!.data!!)
            } else {
                Result.failure(Exception("Session scan failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observePendingCount(): Flow<Int> = pendingScanDao.observePendingCount()
    fun observePendingScans() = pendingScanDao.observeAll()

    suspend fun retryPendingScan(id: Long) {
        pendingScanDao.resetRetry(id)
        SyncWorker.enqueue(context)
    }

    suspend fun deletePendingScan(id: Long) {
        pendingScanDao.deleteById(id)
    }

    suspend fun adHocBooking(equipmentId: String, barcode: String, notes: String? = null): Result<Equipment> {
        return try {
            val response = scannerApi.adHocBooking(AdHocBookingRequest(equipmentId, barcode, notes))
            if (response.isSuccessful && response.body()?.data != null) {
                Result.success(response.body()!!.data!!)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Ad-hoc booking failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateEquipmentLocation(equipmentId: String, location: String): Result<Unit> {
        return try {
            val response = scannerApi.updateLocation(equipmentId, mapOf("location" to location))
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                if (tokenManager.getAccessToken() == "demo-access-token") Result.success(Unit)
                else Result.failure(Exception(response.body()?.message ?: "Standort-Update fehlgeschlagen"))
            }
        } catch (e: Exception) {
            if (tokenManager.getAccessToken() == "demo-access-token") Result.success(Unit)
            else Result.failure(e)
        }
    }

    suspend fun getScanHistory(barcode: String): Result<List<ScanResult>> {
        return try {
            val response = scannerApi.scanHistory(barcode)
            if (response.isSuccessful && response.body()?.data != null) {
                Result.success(response.body()!!.data!!)
            } else {
                Result.success(emptyList())
            }
        } catch (_: Exception) {
            Result.success(emptyList())
        }
    }

    private suspend fun queueOffline(barcode: String, scanType: String, projectId: String?, notes: String?) {
        pendingScanDao.insert(
            PendingScanEntity(
                barcode = barcode,
                scanType = scanType,
                projectId = projectId,
                notes = notes,
                timestamp = System.currentTimeMillis(),
                userId = "",
                deviceId = android.os.Build.SERIAL,
            )
        )
        SyncWorker.enqueue(context)
    }
}
