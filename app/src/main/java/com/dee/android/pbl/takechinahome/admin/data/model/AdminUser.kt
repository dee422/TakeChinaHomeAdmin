package com.dee.android.pbl.takechinahome.admin.data.model

import com.google.gson.annotations.SerializedName

enum class AdminRole { ADMIN, USER }

data class AdminUserInfo(
    @SerializedName("id") val id: Int, // ✨ 确认后端返回的是 "id" 还是 "user_id"
    val email: String,
    val name: String,
    val role: AdminRole,
    val token: String
)

data class AdminUser(
    @SerializedName("id") val id: Int,
    val email: String,
    val name: String,
    val role: String,
    val createdAt: String
)