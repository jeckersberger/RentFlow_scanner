package com.rentflow.scanner.domain.model

data class User(
    val id: String,
    val email: String,
    val name: String,
    val roles: List<String>,
    val tenantId: String,
)
