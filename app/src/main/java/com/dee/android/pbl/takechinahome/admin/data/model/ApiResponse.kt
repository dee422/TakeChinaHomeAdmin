package com.dee.android.pbl.takechinahome.admin.data.model

/**
 * 通用的网络响应包装类
 * @param success 后端返回的操作是否成功
 * @param message 后端返回的提示信息
 * @param data 后端返回的具体数据（泛型，可以是 List<ExchangeGift> 或 Unit）
 */
data class ApiResponse<T>(
    val success: Boolean,
    val message: String? = null,
    val refined_text: String? = null, // ✨ 添加这个字段，或者让 T 包含它
    val data: T? = null
)