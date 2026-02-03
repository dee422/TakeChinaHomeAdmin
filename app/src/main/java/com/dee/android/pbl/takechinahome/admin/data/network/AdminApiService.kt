package com.dee.android.pbl.takechinahome.admin.data.network

import com.dee.android.pbl.takechinahome.admin.data.model.AdminUserInfo
import com.dee.android.pbl.takechinahome.admin.data.model.ApiResponse
import com.dee.android.pbl.takechinahome.admin.data.model.ExchangeGift
import com.dee.android.pbl.takechinahome.admin.ui.screens.AdminGift
import com.dee.android.pbl.takechinahome.admin.data.model.AdminUser
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST

// --- 1. AI 辅助功能相关模型 ---

data class AiRefineResult(
    val success: Boolean,
    val refined_text: String?,
    val message: String?
)

data class ImageResponse(
    val success: Boolean,
    val image_url: String?,
    val message: String?
)

data class TextResponse(
    val success: Boolean,
    val refined_text: String?,
    val message: String?
)

// --- 2. 用户管理相关模型 ---

data class AdminUser(
    val email: String,
    val name: String,
    val role: String, // "admin" 或 "user"
    val createdAt: String
)

// --- 3. 核心 API 接口 ---

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
    ): ApiResponse<Unit>


    // --- B. 产品上架与库管理相关 ---

    @GET("get_gifts.php")
    suspend fun getGifts(): List<AdminGift>

    @FormUrlEncoded
    @POST("upload_gifts.php")
    suspend fun uploadGift(
        @Field("name") name: String,
        @Field("deadline") deadline: String,
        @Field("spec") spec: String,
        @Field("desc") desc: String,
        @Field("image_list") imageListJson: String
    ): ApiResponse<Unit>

    @FormUrlEncoded
    @POST("update_gift.php")
    suspend fun updateGift(
        @Field("id") id: Int,
        @Field("name") name: String,
        @Field("deadline") deadline: String,
        @Field("spec") spec: String,
        @Field("desc") desc: String
    ): ApiResponse<Unit>

    @FormUrlEncoded
    @POST("delete_gift_admin.php")
    suspend fun deleteGift(
        @Field("id") id: Int
    ): ApiResponse<Unit>


    // --- C. AI 辅助功能 (文本与图像) ---

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

    /**
     * 登录接口：验证身份并获取 role
     */
    @FormUrlEncoded
    @POST("admin_login.php")
    suspend fun login(
        @Field("email") email: String,
        @Field("password") password: String
    ): ApiResponse<AdminUserInfo>

    /**
     * 创建后台用户：由管理员操作
     */
    @FormUrlEncoded
    @POST("create_admin_user.php")
    suspend fun createAdminUser(
        @Field("email") email: String,
        @Field("password") password: String,
        @Field("name") name: String,
        @Field("role") role: String, // "admin" 或 "user"
        @Field("admin_token") adminToken: String = "secret_admin_key"
    ): ApiResponse<Unit>

    /**
     * 获取所有后台用户列表
     */
    @GET("get_admin_users.php")
    suspend fun getAdminUsers(): ApiResponse<List<AdminUser>>
}