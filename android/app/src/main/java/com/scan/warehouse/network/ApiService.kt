package com.scan.warehouse.network

import com.scan.warehouse.model.ScanResponse
import com.scan.warehouse.model.SearchResponse
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @GET("api/scan/{barcode}")
    suspend fun scanBarcode(
        @Path("barcode") barcode: String
    ): ScanResponse

    @GET("api/search")
    suspend fun searchProducts(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20
    ): SearchResponse

    @GET("api/image/{path}")
    suspend fun getImage(
        @Path("path", encoded = true) path: String
    ): ResponseBody

    @GET("health")
    suspend fun healthCheck(): ResponseBody
}
