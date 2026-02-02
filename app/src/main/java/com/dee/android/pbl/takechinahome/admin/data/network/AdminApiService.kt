package com.dee.android.pbl.takechinahome.admin.data.network

import com.dee.android.pbl.takechinahome.admin.data.model.ApiResponse
import com.dee.android.pbl.takechinahome.admin.data.model.ExchangeGift
import com.dee.android.pbl.takechinahome.admin.ui.screens.AdminGift
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST

// 定义 AI 专用返回模型（如果不想写在单独文件里，可以放在这里）
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
    val refined_text: String?, // 建议与 AI 润色接口保持一致字段名，方便复用
    val message: String?
)

interface AdminApiService {

    // --- 1. 市场审核相关 ---

    @GET("get_pending_items.php")
    suspend fun getPendingItems(): ApiResponse<List<ExchangeGift>>

    @FormUrlEncoded
    @POST("admin_audit_action.php")
    suspend fun auditItem(
        @Field("id") id: Int,
        @Field("status") newStatus: Int,
        @Field("admin_token") token: String = "secret_admin_key"
    ): ApiResponse<Unit>


    // --- 2. 产品上架与库管理相关 ---

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


    // --- 3. AI 辅助功能 (文本润色 & 图像生图) ---

    /**
     * AI 文本润色：用于补全描述文案
     */
    @FormUrlEncoded
    @POST("ai_proxy.php")
    suspend fun refineText(
        @Field("provider") provider: String,
        @Field("api_key") apiKey: String,
        @Field("text") text: String,
        @Field("samples") samples: String
    ): AiRefineResult

    /**
     * AI 海报生图：用于礼品开发模块
     */
    @FormUrlEncoded
    @POST("image_proxy.php") // 对应你上传的 PHP 文件名
    suspend fun generateImage(
        @Field("provider") provider: String,
        @Field("api_key") apiKey: String,
        @Field("prompt") prompt: String,
        @Field("no_chinese") noChinese: Boolean
    ): ImageResponse

    /**
     * AI 营销文案生成：专门生成带产品气息的营销语
     */
    @FormUrlEncoded
    @POST("ai_proxy.php") // 统一使用这个代理，通过 prompt 区分任务
    suspend fun generateMarketingCopy(
        @Field("provider") provider: String,
        @Field("api_key") apiKey: String,
        @Field("text") prompt: String, // 统一使用 text 字段名，因为后台 php 往往接收这个
        @Field("samples") samples: String = "" // 传空即可
    ): TextResponse
}