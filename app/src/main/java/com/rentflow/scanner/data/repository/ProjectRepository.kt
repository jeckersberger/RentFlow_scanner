package com.rentflow.scanner.data.repository

import com.rentflow.scanner.data.api.ProjectApi
import com.rentflow.scanner.domain.model.Project
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    private val projectApi: ProjectApi,
) {
    suspend fun listProjects(search: String? = null): Result<List<Project>> {
        return try {
            val response = projectApi.listProjects(search)
            if (response.isSuccessful && response.body()?.data != null) {
                Result.success(response.body()!!.data!!)
            } else {
                Result.failure(Exception("Failed to load projects"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
