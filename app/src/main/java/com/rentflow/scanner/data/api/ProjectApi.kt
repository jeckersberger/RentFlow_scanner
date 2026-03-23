package com.rentflow.scanner.data.api

import com.rentflow.scanner.domain.model.Project
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface ProjectApi {
    @GET("api/v1/projects")
    suspend fun listProjects(@Query("search") search: String? = null): Response<ApiResponse<List<Project>>>
}
