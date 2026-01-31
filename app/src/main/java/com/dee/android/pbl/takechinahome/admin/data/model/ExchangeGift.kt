package com.dee.android.pbl.takechinahome.admin.data.model

import java.io.Serializable

data class ExchangeGift(
    val id: Int,
    val itemName: String,
    val description: String,
    val imageUrl: String?,
    val ownerEmail: String,
    val contactCode: String,
    val exchangeWish: Int,
    var status: Int // 1: 待审核, 2: 已上架, 3: 已下架
) : Serializable