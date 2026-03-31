package com.scan.warehouse.network

import com.scan.warehouse.model.BoxResponse
import com.scan.warehouse.model.CartRequest
import com.scan.warehouse.model.CartResponse
import com.scan.warehouse.model.PrintRequest
import com.scan.warehouse.model.PrintResponse
import com.scan.warehouse.model.ScanResponse
import com.scan.warehouse.model.SearchResponse
import com.scan.warehouse.model.ShelfItem
import com.scan.warehouse.model.ShelfListResponse
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @GET("api/scan/{barcode}")
    suspend fun scanBarcode(@Path("barcode") barcode: String): ScanResponse

    @GET("api/search")
    suspend fun searchProducts(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20
    ): SearchResponse

    @GET("api/image/{path}")
    suspend fun getImage(@Path("path", encoded = true) path: String): ResponseBody

    @POST("api/print")
    suspend fun printLabel(@Body request: PrintRequest): PrintResponse

    @POST("api/cart")
    suspend fun addToCart(@Body request: CartRequest): CartResponse

    @GET("health")
    suspend fun healthCheck(): ResponseBody

    @GET("api/box/{qr_code}")
    suspend fun getBox(@Path("qr_code") qrCode: String): BoxResponse

    @GET("api/shelves/{floor}/{zone}")
    suspend fun getShelves(@Path("floor") floor: Int, @Path("zone") zone: String): ShelfListResponse

    @PATCH("api/shelf/{shelfId}")
    suspend fun updateShelfLabel(@Path("shelfId") shelfId: Int, @Body body: Map<String, String>): ShelfItem

    @DELETE("api/shelf/{shelfId}/label")
    suspend fun deleteShelfLabel(@Path("shelfId") shelfId: Int): ResponseBody

    @Multipart
    @POST("api/shelf/{shelfId}/photo")
    suspend fun uploadShelfPhoto(@Path("shelfId") shelfId: Int, @Part file: MultipartBody.Part): ShelfItem

    @DELETE("api/shelf/photo/{photoId}")
    suspend fun deleteShelfPhoto(@Path("photoId") photoId: Int): ResponseBody

}
