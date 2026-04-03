package com.scan.warehouse.network

import com.scan.warehouse.model.AppVersion
import com.scan.warehouse.model.BoxResponse
import com.scan.warehouse.model.CellDetail
import com.scan.warehouse.model.MapLayout
import com.scan.warehouse.model.CartRequest
import com.scan.warehouse.model.CartResponse
import com.scan.warehouse.model.PrintRequest
import com.scan.warehouse.model.PrintResponse
import com.scan.warehouse.model.ScanResponse
import com.scan.warehouse.model.SearchResponse
import com.scan.warehouse.model.ShelfItem
import com.scan.warehouse.model.ShelfListResponse
import com.scan.warehouse.model.Zone
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

    @GET("api/app-version")
    suspend fun getAppVersion(): AppVersion

    @GET("api/box/{qr_code}")
    suspend fun getBox(@Path("qr_code") qrCode: String): BoxResponse

    @GET("api/shelves/{floor}/{zone}")
    suspend fun getShelves(@Path("floor") floor: Int, @Path("zone") zone: String): ShelfListResponse

    @PATCH("api/shelf/{shelfId}")
    suspend fun updateShelfLabel(@Path("shelfId") shelfId: Int, @Body body: Map<String, String>): ShelfItem

    @DELETE("api/shelf/{shelfId}/label")
    suspend fun deleteShelfLabel(@Path("shelfId") shelfId: Int): ResponseBody

    @Multipart
    @POST("api/map-layout/cell/{cellKey}/photo")
    suspend fun uploadCellPhoto(@Path("cellKey") cellKey: String, @Part file: MultipartBody.Part): Map<String, String>

    @DELETE("api/map-layout/cell/{cellKey}/photo")
    suspend fun deleteCellPhoto(@Path("cellKey") cellKey: String): Map<String, String>

    @Multipart
    @POST("api/map-layout/cell/{cellKey}/level/{levelIndex}/photo")
    suspend fun uploadLevelPhoto(
        @Path("cellKey") cellKey: String,
        @Path("levelIndex") levelIndex: Int,
        @Part file: MultipartBody.Part
    ): Map<String, String>

    @DELETE("api/map-layout/cell/{cellKey}/level/{levelIndex}/photo")
    suspend fun deleteLevelPhoto(
        @Path("cellKey") cellKey: String,
        @Path("levelIndex") levelIndex: Int
    ): Map<String, String>

    @GET("api/map-layout")
    suspend fun getMapLayout(): MapLayout

    @PATCH("api/map-layout/cell/{cellKey}")
    suspend fun updateMapCell(@Path("cellKey") cellKey: String, @Body data: Map<String, @JvmSuppressWildcards Any>): Map<String, String>

    @PATCH("api/product/{skuId}/location")
    suspend fun updateProductLocation(@Path("skuId") skuId: String, @Body data: Map<String, String>): Map<String, String>

    @POST("api/box")
    suspend fun createBox(@Body data: Map<String, @JvmSuppressWildcards Any>): BoxResponse

    @PATCH("api/box/{qrCode}")
    suspend fun updateBox(@Path("qrCode") qrCode: String, @Body data: Map<String, String>): BoxResponse

    @POST("api/box/{qrCode}/member")
    suspend fun addBoxMember(@Path("qrCode") qrCode: String, @Body data: Map<String, String>): Map<String, String>

    @DELETE("api/box/{qrCode}/member/{skuId}")
    suspend fun removeBoxMember(@Path("qrCode") qrCode: String, @Path("skuId") skuId: String): Map<String, String>

    @GET("api/zones")
    suspend fun getZones(): List<Zone>

    @POST("api/zones")
    suspend fun createZone(@Body data: Map<String, @JvmSuppressWildcards Any>): Zone

    @PATCH("api/zones/{zoneId}")
    suspend fun updateZone(@Path("zoneId") zoneId: Int, @Body data: Map<String, @JvmSuppressWildcards Any>): Zone

    @DELETE("api/zones/{zoneId}")
    suspend fun deleteZone(@Path("zoneId") zoneId: Int): Map<String, String>

    @GET("api/zones/{zoneId}/cells")
    suspend fun getZoneCells(@Path("zoneId") zoneId: Int): List<CellDetail>

    @GET("api/cells/{cellId}")
    suspend fun getCellDetail(@Path("cellId") cellId: Int): CellDetail

    @POST("api/cells/{cellId}/levels")
    suspend fun addCellLevel(@Path("cellId") cellId: Int, @Body data: Map<String, String>): Map<String, @JvmSuppressWildcards Any>

    @DELETE("api/levels/{levelId}")
    suspend fun deleteLevel(@Path("levelId") levelId: Int): Map<String, String>

    @POST("api/levels/{levelId}/products")
    suspend fun addLevelProduct(@Path("levelId") levelId: Int, @Body data: Map<String, String>): Map<String, @JvmSuppressWildcards Any>

    @DELETE("api/level-products/{productId}")
    suspend fun removeLevelProduct(@Path("productId") productId: Int): Map<String, String>

    @Multipart
    @POST("api/level-products/{productId}/photo")
    suspend fun uploadLevelProductPhoto(@Path("productId") productId: Int, @Part file: MultipartBody.Part): Map<String, String>

    @DELETE("api/level-products/{productId}/photo")
    suspend fun deleteLevelProductPhoto(@Path("productId") productId: Int): Map<String, String>

    @POST("api/inbound")
    suspend fun processInbound(@Body data: Map<String, @JvmSuppressWildcards Any>): Map<String, String>

    @POST("api/outbound")
    suspend fun processOutbound(@Body data: Map<String, @JvmSuppressWildcards Any>): Map<String, String>

    @POST("api/inventory-check")
    suspend fun inventoryCheck(@Body data: Map<String, @JvmSuppressWildcards Any>): Map<String, @JvmSuppressWildcards Any>

}
