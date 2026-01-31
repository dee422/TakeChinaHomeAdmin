package com.dee.android.pbl.takechinahome.admin.data.network

import com.dee.android.pbl.takechinahome.admin.data.model.ApiResponse
import com.dee.android.pbl.takechinahome.admin.data.model.ExchangeGift
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST

interface AdminApiService {

    // 获取待审核列表 (T 是 List<ExchangeGift>)
    @GET("get_pending_items.php")
    suspend fun getPendingItems(): ApiResponse<List<ExchangeGift>>

    // 审核操作 (T 是 Unit，因为不需要返回具体对象)
    @FormUrlEncoded
    @POST("admin_audit_action.php")
    suspend fun auditItem(
        @Field("id") id: Int,
        @Field("status") newStatus: Int, // 2 为通过，3 或 4 为驳回/封禁
        @Field("admin_token") token: String = "secret_admin_key"
    ): ApiResponse<Unit>
}