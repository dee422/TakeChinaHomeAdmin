package com.dee.android.pbl.takechinahome.admin.data.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "https://ichessgeek.com/api/v1/"

    // 保持 instance 兼容，同时增加 adminService 供界面调用
    val instance: AdminApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AdminApiService::class.java)
    }

    // 界面端现在可以通过 RetrofitClient.adminService 访问
    val adminService: AdminApiService = instance
}