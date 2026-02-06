package com.dee.android.pbl.takechinahome.admin.data.model

import com.google.gson.annotations.SerializedName

data class OrderDetailItem(
    val name: String,
    val qty: Int,
    val spec: String? = null,
    val note: String? = null
)

data class Order(
    val id: Int,
    @SerializedName("manager_id") val managerId: Int,
    @SerializedName("is_intent") val isIntent: Int,
    val status: String,
    @SerializedName("user_email") val userEmail: String,
    @SerializedName("contact_name") val contactName: String,

    // 保持 emptyList 默认值，防止遍历 details 时崩溃
    val details: List<OrderDetailItem> = emptyList(),

    @SerializedName("ai_suggestion") val aiSuggestion: String? = null,
    @SerializedName("admin_notes") val adminNotes: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,

    // ✨ 核心修正：确保能够接收到后端关联查询出的经理姓名
    // 如果 formal_orders 里有 manager_name，它会读到
    // 如果意向单 orders 关联查出了 manager_name，它也会读到
    @SerializedName("manager_name") val managerName: String? = null,

    // 正式单结构化字段
    @SerializedName("target_gift_name") val targetGiftName: String? = null,
    @SerializedName("target_qty") val targetQty: Int = 0,
    @SerializedName("delivery_date") val deliveryDate: String? = null,
    @SerializedName("contact_method") val contactMethod: String? = null,
    @SerializedName("intent_confirm_status") val intentConfirmStatus: Int = 0,

    // 正式单专有字段
    @SerializedName("original_order_id") val originalOrderId: Int? = null,
    @SerializedName("final_image_path") val finalImagePath: String? = null
)