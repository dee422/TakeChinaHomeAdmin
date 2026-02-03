package com.dee.android.pbl.takechinahome.admin.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "https://ichessgeek.com/api/v1/"

    // 1. 配置日志拦截器
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // 2. 配置 OkHttpClient
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        /* 如果你的 HTTPS 证书有问题，可以在这里添加 .sslSocketFactory(...)
           但在生产环境建议先解决证书链完整性问题。
        */
        .build()

    // 3. 构建 Retrofit 实例
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            // ✨ 关键点：必须将配置好的 okHttpClient 传入
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // 4. 定义服务接口
    val adminService: AdminApiService by lazy {
        retrofit.create(AdminApiService::class.java)
    }

    // 为了兼容你之前的调用习惯，保留 instance
    val instance: AdminApiService get() = adminService
}