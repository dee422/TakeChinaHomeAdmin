package com.dee.android.pbl.takechinahome.admin.data.model

import java.io.Serializable

data class ExchangeGift(
    val id: Int,
    val itemName: String,
    // 允许 description 为空，防止 JSON 解析崩溃
    val description: String?,
    val imageUrl: String?,
    // 如果数据库里这些也有可能是 NULL，建议全部加上问号
    val ownerEmail: String?,
    val contactCode: String?,
    val exchangeWish: Int,
    var status: Int // 1: 待审核, 2: 已上架, 3: 已下架
) : Serializable