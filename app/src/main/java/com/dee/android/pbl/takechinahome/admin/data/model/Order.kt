package com.dee.android.pbl.takechinahome.admin.data.model

import com.google.gson.annotations.SerializedName

data class OrderDetailItem(
    val name: String,
    // qty 是数字，使用 Int
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
    val details: List<OrderDetailItem>,
    @SerializedName("ai_suggestion") val aiSuggestion: String?,
    @SerializedName("admin_notes") val adminNotes: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("manager_name") val managerName: String?,

    // ✨ 新增意向订单结构化字段
    @SerializedName("target_gift_name") val targetGiftName: String?,
    @SerializedName("target_qty") val targetQty: Int,
    @SerializedName("delivery_date") val deliveryDate: String?,
    @SerializedName("contact_method") val contactMethod: String?,
    @SerializedName("intent_confirm_status") val intentConfirmStatus: Int
)