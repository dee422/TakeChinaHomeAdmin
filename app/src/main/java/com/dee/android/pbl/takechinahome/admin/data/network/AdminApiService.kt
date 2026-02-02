package com.dee.android.pbl.takechinahome.admin.data.network

import com.dee.android.pbl.takechinahome.admin.data.model.ApiResponse
import com.dee.android.pbl.takechinahome.admin.data.model.ExchangeGift
import com.dee.android.pbl.takechinahome.admin.ui.screens.AdminGift // 确保这里的包名对应你的模型位置
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface AdminApiService {

    // --- 1. 市场审核相关 ---

    /**
     * 获取所有兑换申请数据
     */
    @GET("get_pending_items.php")
    suspend fun getPendingItems(): ApiResponse<List<ExchangeGift>>

    /**
     * 审核操作（通过/拒绝）
     */
    @FormUrlEncoded
    @POST("admin_audit_action.php")
    suspend fun auditItem(
        @Field("id") id: Int,
        @Field("status") newStatus: Int,
        @Field("admin_token") token: String = "secret_admin_key"
    ): ApiResponse<Unit>


    // --- 2. 产品上架与库管理相关 ---

    /**
     * 获取产品库列表：用于在管理页面展示所有已上架产品
     */
    @GET("get_gifts.php")
    suspend fun getGifts(): List<AdminGift>

    /**
     * 快速录入新品：由拍照人员使用
     */
    @FormUrlEncoded
    @POST("upload_gifts.php")
    suspend fun uploadGift(
        @Field("name") name: String,
        @Field("deadline") deadline: String,
        @Field("spec") spec: String,
        @Field("desc") desc: String,
        @Field("image_list") imageListJson: String
    ): ApiResponse<Unit>

    /**
     * 精修/补全产品信息：由后台人员在管理页面使用
     * 注意：必须传 id 以便后端定位更新哪一条记录
     */
    @FormUrlEncoded
    @POST("update_gift.php")
    suspend fun updateGift(
        @Field("id") id: Int,
        @Field("name") name: String,
        @Field("deadline") deadline: String,
        @Field("spec") spec: String,
        @Field("desc") desc: String
    ): ApiResponse<Unit>

    /**
     * 删除产品：可选，用于下架或清理错误数据
     */
    @FormUrlEncoded
    @POST("delete_gift_admin.php")
    suspend fun deleteGift(
        @Field("id") id: Int
    ): ApiResponse<Unit>
}