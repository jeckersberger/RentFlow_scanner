package com.rentflow.scanner.data.repository

import com.rentflow.scanner.data.api.WarehouseApi
import com.rentflow.scanner.domain.model.WarehouseZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WarehouseRepository @Inject constructor(
    private val warehouseApi: WarehouseApi,
) {
    suspend fun listZones(): Result<List<WarehouseZone>> {
        return try {
            val response = warehouseApi.listZones()
            if (response.isSuccessful && response.body()?.data != null) {
                Result.success(response.body()!!.data!!)
            } else {
                Result.failure(Exception("Failed to load zones"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
