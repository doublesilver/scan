package com.scan.warehouse.network

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val DEFAULT_BASE_URL = "http://192.168.0.100:8000"
    private const val PREF_NAME = "warehouse_settings"
    private const val KEY_SERVER_URL = "server_url"

    private var apiService: ApiService? = null
    private var currentBaseUrl: String = ""

    fun getApiService(context: Context): ApiService {
        val baseUrl = getBaseUrl(context)
        if (apiService == null || currentBaseUrl != baseUrl) {
            currentBaseUrl = baseUrl
            apiService = buildRetrofit(baseUrl).create(ApiService::class.java)
        }
        return apiService!!
    }

    fun getBaseUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SERVER_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    }

    fun saveBaseUrl(context: Context, url: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SERVER_URL, url)
            .apply()
        apiService = null
    }

    private fun buildRetrofit(baseUrl: String): Retrofit {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        return Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
