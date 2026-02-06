// Order.kt 完整代码
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

    // ✨ 核心修正：这里不要用问号，直接给 emptyList()
    // 即使后端 formal_orders 没有这个字段，Gson 也会保证它是空列表而非 null
    val details: List<OrderDetailItem> = emptyList(),

    @SerializedName("ai_suggestion") val aiSuggestion: String? = null,
    @SerializedName("admin_notes") val adminNotes: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
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