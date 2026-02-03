package com.dee.android.pbl.takechinahome.admin.data.model

import com.google.gson.annotations.SerializedName

data class OrderDetailItem(
    val name: String,
    // ✨ 关键修改：根据你的 JSON 截图，qty 是数字，所以这里必须用 Int
    val qty: Int,
    val spec: String?,
    val note: String?
)

data class Order(
    val id: Int,
    @SerializedName("manager_id") val managerId: Int,
    @SerializedName("is_intent") val isIntent: Int,
    val status: String,
    @SerializedName("user_email") val userEmail: String,
    @SerializedName("contact_name") val contactName: String,
    // ✨ 确保这是一个 List
    val details: List<OrderDetailItem>,
    @SerializedName("ai_suggestion") val aiSuggestion: String?,
    @SerializedName("admin_notes") val adminNotes: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("manager_name") val managerName: String?
)