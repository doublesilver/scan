package com.scan.warehouse.network

import android.content.Context
import com.scan.warehouse.R
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val PREF_NAME = "warehouse_settings"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_API_KEY = "api_key"

    @Volatile
    private var apiService: ApiService? = null
    @Volatile
    private var currentBaseUrl: String = ""
    @Volatile
    private var appContext: Context? = null

    fun getApiService(context: Context): ApiService {
        appContext = context.applicationContext
        val baseUrl = getBaseUrl(context)
        if (apiService == null || currentBaseUrl != baseUrl) {
            synchronized(this) {
                if (apiService == null || currentBaseUrl != baseUrl) {
                    currentBaseUrl = baseUrl
                    apiService = buildRetrofit(baseUrl).create(ApiService::class.java)
                }
            }
        }
        return apiService!!
    }

    fun getBaseUrl(context: Context): String {
        val defaultUrl = context.getString(R.string.default_server_url)
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val url = prefs.getString(KEY_SERVER_URL, defaultUrl) ?: defaultUrl
        return if (url.isBlank() || url == "http://") defaultUrl else url
    }

    fun saveBaseUrl(context: Context, url: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SERVER_URL, url)
            .apply()
        synchronized(this) {
            apiService = null
        }
    }

    fun getApiKey(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_API_KEY, "") ?: ""
    }

    fun saveApiKey(context: Context, key: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_API_KEY, key).apply()
        synchronized(this) {
            apiService = null
        }
    }

    private fun buildRetrofit(baseUrl: String): Retrofit {
        val client = OkHttpClient.Builder()
            .addInterceptor(RetryInterceptor())
            .addInterceptor { chain ->
                val ctx = appContext
                val request = if (ctx != null) {
                    val apiKey = getApiKey(ctx)
                    if (apiKey.isNotEmpty()) {
                        chain.request().newBuilder()
                            .addHeader("X-API-Key", apiKey)
                            .build()
                    } else chain.request()
                } else chain.request()
                chain.proceed(request)
            }
            .apply {
                if (com.scan.warehouse.BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    })
                }
            }
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
