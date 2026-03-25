package com.rentflow.scanner.data.repository

import com.rentflow.scanner.data.api.WarehouseApi
import com.rentflow.scanner.data.preferences.TokenManager
import com.rentflow.scanner.domain.model.WarehouseZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WarehouseRepository @Inject constructor(
    private val warehouseApi: WarehouseApi,
    private val tokenManager: TokenManager,
) {
    suspend fun listZones(): Result<List<WarehouseZone>> {
        if (tokenManager.getAccessToken() == "demo-access-token") {
            return Result.success(listOf(
                WarehouseZone("1", "Lager", 0),
                WarehouseZone("2", "Halle A", 0),
                WarehouseZone("3", "Halle B", 0),
                WarehouseZone("4", "Außenlager", 0),
                WarehouseZone("5", "Werkstatt", 0),
                WarehouseZone("6", "Baustelle", 0),
            ))
        }
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
