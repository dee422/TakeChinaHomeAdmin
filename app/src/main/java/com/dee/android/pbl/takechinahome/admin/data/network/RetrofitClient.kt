package com.dee.android.pbl.takechinahome.admin.data.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // 确保末尾有斜杠 /
    private const val BASE_URL = "https://ichessgeek.com/api/v1/"

    val instance: AdminApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AdminApiService::class.java)
    }
}