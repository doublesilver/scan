package com.scan.warehouse.network

import com.scan.warehouse.model.LoginApiResponse
import com.scan.warehouse.model.LoginRequest
import com.scan.warehouse.model.RefreshApiResponse
import com.scan.warehouse.model.RefreshRequest
import com.scan.warehouse.model.ScanApiResponse
import com.scan.warehouse.model.ScanRequest
import com.scan.warehouse.model.SearchApiResponse
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {

    @POST("api/scan")
    suspend fun scanBarcode(@Body request: ScanRequest): ScanApiResponse

    @GET("api/search")
    suspend fun searchProducts(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20
    ): SearchApiResponse

    @GET("api/images/proxy")
    suspend fun getImage(@Query("path") path: String): ResponseBody

    @GET("health")
    suspend fun healthCheck(): ResponseBody

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginApiResponse

    @POST("api/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshRequest): RefreshApiResponse
}
