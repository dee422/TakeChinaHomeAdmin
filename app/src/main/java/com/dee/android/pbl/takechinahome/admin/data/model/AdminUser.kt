package com.dee.android.pbl.takechinahome.admin.data.model

// 定义角色枚举
enum class AdminRole {
    ADMIN, USER
}

// 登录返回的用户信息
data class AdminUserInfo(
    val email: String,
    val name: String,
    val role: AdminRole, // 使用枚举
    val token: String
)

// 管理员列表中的用户信息
data class AdminUser(
    val email: String,
    val name: String,
    val role: String, // 后端返回通常是 String "admin"/"user"
    val createdAt: String
)