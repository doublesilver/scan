package com.scan.warehouse.network

import android.content.Context
import com.scan.warehouse.R
import com.scan.warehouse.model.RefreshRequest
import com.google.gson.Gson
import com.scan.warehouse.model.RefreshApiResponse
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private const val AUTH_PREF_NAME = "warehouse_auth"
private const val KEY_ACCESS_TOKEN = "access_token"
private const val KEY_REFRESH_TOKEN = "refresh_token"

fun saveTokens(context: Context, accessToken: String, refreshToken: String) {
    context.getSharedPreferences(AUTH_PREF_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_ACCESS_TOKEN, accessToken)
        .putString(KEY_REFRESH_TOKEN, refreshToken)
        .apply()
}

fun getAccessToken(context: Context): String? {
    return context.getSharedPreferences(AUTH_PREF_NAME, Context.MODE_PRIVATE)
        .getString(KEY_ACCESS_TOKEN, null)
}

fun getRefreshToken(context: Context): String? {
    return context.getSharedPreferences(AUTH_PREF_NAME, Context.MODE_PRIVATE)
        .getString(KEY_REFRESH_TOKEN, null)
}

fun clearTokens(context: Context) {
    context.getSharedPreferences(AUTH_PREF_NAME, Context.MODE_PRIVATE)
        .edit()
        .clear()
        .apply()
}

class AuthInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = getAccessToken(context)
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}

class TokenAuthenticator(private val context: Context) : Authenticator {
    private val gson = Gson()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null

        val refreshToken = getRefreshToken(context) ?: return null
        val baseUrl = RetrofitClient.getBaseUrl(context)
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        val body = gson.toJson(RefreshRequest(refreshToken))
            .toRequestBody("application/json".toMediaType())

        val refreshRequest = Request.Builder()
            .url("${normalized}api/auth/refresh")
            .post(body)
            .build()

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        val refreshResponse = try {
            client.newCall(refreshRequest).execute()
        } catch (_: Exception) {
            return null
        }

        if (refreshResponse.isSuccessful) {
            val responseBody = refreshResponse.body?.string() ?: return null
            val refreshData = try {
                gson.fromJson(responseBody, RefreshApiResponse::class.java)
            } catch (_: Exception) {
                return null
            }
            val newAccessToken = refreshData.data.accessToken
            val currentRefresh = getRefreshToken(context) ?: refreshToken
            saveTokens(context, newAccessToken, currentRefresh)

            return response.request.newBuilder()
                .header("Authorization", "Bearer $newAccessToken")
                .build()
        } else {
            clearTokens(context)
            return null
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}

object RetrofitClient {

    private const val PREF_NAME = "warehouse_settings"
    private const val KEY_SERVER_URL = "server_url"

    @Volatile
    private var apiService: ApiService? = null
    @Volatile
    private var currentBaseUrl: String = ""

    fun getApiService(context: Context): ApiService {
        val baseUrl = getBaseUrl(context)
        if (apiService == null || currentBaseUrl != baseUrl) {
            synchronized(this) {
                if (apiService == null || currentBaseUrl != baseUrl) {
                    currentBaseUrl = baseUrl
                    apiService = buildRetrofit(context, baseUrl).create(ApiService::class.java)
                }
            }
        }
        return apiService!!
    }

    fun getBaseUrl(context: Context): String {
        val defaultUrl = context.getString(R.string.default_server_url)
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SERVER_URL, defaultUrl) ?: defaultUrl
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

    private fun buildRetrofit(context: Context, baseUrl: String): Retrofit {
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(context.applicationContext))
            .addInterceptor(RetryInterceptor())
            .apply {
                if (com.scan.warehouse.BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    })
                }
            }
            .authenticator(TokenAuthenticator(context.applicationContext))
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
