package com.dee.android.pbl.takechinahome.admin.data.network

import com.dee.android.pbl.takechinahome.admin.data.model.*
import com.dee.android.pbl.takechinahome.admin.ui.screens.AdminGift
import com.google.gson.annotations.SerializedName
import retrofit2.http.*

// --- AI 相关响应模型 ---
// 增加注解确保字段映射准确，同时符合 Kotlin 命名规范
data class AiRefineResult(
    val success: Boolean,
    @SerializedName("refined_text") val refinedText: String?,
    val message: String?
)

data class ImageResponse(
    val success: Boolean,
    @SerializedName("image_url") val imageUrl: String?,
    val message: String?
)

data class TextResponse(
    val success: Boolean,
    @SerializedName("refined_text") val refinedText: String?,
    val message: String?
)

// --- 核心 API 接口 ---
interface AdminApiService {

    // --- A. 市场审核相关 ---
    @GET("get_pending_items.php")
    suspend fun getPendingItems(): ApiResponse<List<ExchangeGift>>

    @FormUrlEncoded
    @POST("admin_audit_action.php")
    suspend fun auditItem(
        @Field("id") id: Int,
        @Field("status") newStatus: Int,
        @Field("admin_token") token: String = "secret_admin_key"
    ): ApiResponse<Any?>

    // --- B. 产品上架与库管理相关 ---
    @GET("get_gifts.php")
    suspend fun getGifts(): ApiResponse<List<AdminGift>>

    @FormUrlEncoded
    @POST("upload_gifts.php")
    suspend fun uploadGift(
        @Field("name") name: String,
        @Field("deadline") deadline: String,
        @Field("spec") spec: String,
        @Field("desc") desc: String,
        @Field("image_list") imageListJson: String
    ): ApiResponse<Any?>

    @FormUrlEncoded
    @POST("update_gift.php")
    suspend fun updateGift(
        @Field("id") id: Int,
        @Field("name") name: String,
        @Field("deadline") deadline: String,
        @Field("spec") spec: String,
        @Field("desc") desc: String
    ): ApiResponse<Any?>

    @FormUrlEncoded
    @POST("delete_gift_admin.php")
    suspend fun deleteGift(@Field("id") id: Int): ApiResponse<Any?>

    // --- C. AI 辅助功能 ---
    @FormUrlEncoded
    @POST("ai_proxy.php")
    suspend fun refineText(
        @Field("provider") provider: String,
        @Field("api_key") apiKey: String,
        @Field("text") text: String,
        @Field("samples") samples: String
    ): AiRefineResult

    @FormUrlEncoded
    @POST("image_proxy.php")
    suspend fun generateImage(
        @Field("provider") provider: String,
        @Field("api_key") apiKey: String,
        @Field("prompt") prompt: String,
        @Field("no_chinese") noChinese: Boolean
    ): ImageResponse

    @FormUrlEncoded
    @POST("ai_proxy.php")
    suspend fun generateMarketingCopy(
        @Field("provider") provider: String,
        @Field("api_key") apiKey: String,
        @Field("text") prompt: String,
        @Field("samples") samples: String = ""
    ): TextResponse

    // --- D. 账户权限与用户管理 ---
    @FormUrlEncoded
    @POST("admin_login.php")
    suspend fun login(
        @Field("email") email: String,
        @Field("password") password: String
    ): ApiResponse<AdminUserInfo>

    @FormUrlEncoded
    @POST("create_admin_user.php")
    suspend fun createAdminUser(
        @Field("email") email: String,
        @Field("password") password: String,
        @Field("name") name: String,
        @Field("role") role: String,
        @Field("admin_token") adminToken: String = "secret_admin_key"
    ): ApiResponse<Any?>

    @GET("get_admin_users.php")
    suspend fun getAdminUsers(): ApiResponse<List<AdminUser>>

    // --- E. 订单管理 ---
    @GET("get_intent_orders.php")
    suspend fun getIntentOrders(
        @Query("manager_id") managerId: Int
    ): ApiResponse<List<Order>>

    @FormUrlEncoded
    @POST("update_order_status.php")
    suspend fun updateOrderStatus(
        @Field("order_id") orderId: Int,
        @Field("status") status: String,
        @Field("is_intent") isIntent: Int
    ): ApiResponse<Any?>

    @GET("get_ai_suggestion.php")
    suspend fun getAiSuggestion(@Query("order_id") orderId: Int): ApiResponse<String>
}