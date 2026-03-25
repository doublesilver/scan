package com.scan.warehouse.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class ScanResponse(
    @SerializedName("sku_id") val skuId: Int,
    @SerializedName("sku_code") val skuCode: String? = null,
    @SerializedName("product_name") val productName: String,
    @SerializedName("material") val material: String? = null,
    @SerializedName("brand") val brand: String? = null,
    @SerializedName("color") val color: String? = null,
    @SerializedName("stock_quantity") val stockQuantity: Int? = null,
    @SerializedName("barcodes") val barcodes: List<String>,
    @SerializedName("images") val images: List<ImageItem>,
) : Parcelable

@Parcelize
data class ImageItem(
    val id: Int? = null,
    @SerializedName("file_path") val filePath: String,
    @SerializedName("image_type") val imageType: String,
    @SerializedName("sort_order") val sortOrder: Int? = null
) : Parcelable

data class ScanRequest(val barcode: String)
data class LoginRequest(val username: String, val password: String)
data class RefreshRequest(@SerializedName("refresh_token") val refreshToken: String)

data class ScanApiResponse(val data: ScanResponse)
data class SearchApiResponse(val data: SearchData)
data class LoginApiResponse(val data: LoginData)
data class RefreshApiResponse(val data: RefreshData)

data class SearchData(
    val products: List<SearchItem>,
    val skus: List<SearchItem>,
    val total: Int
)

data class SearchResponse(
    @SerializedName("total") val total: Int,
    @SerializedName("items") val items: List<SearchItem>
)

data class SearchItem(
    @SerializedName("sku_id") val skuId: String,
    @SerializedName("product_name") val productName: String,
    @SerializedName("category") val category: String? = null,
    @SerializedName("brand") val brand: String? = null,
    @SerializedName("barcode") val barcode: String? = null
)

data class LoginData(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    val user: UserInfo
)

data class RefreshData(
    @SerializedName("access_token") val accessToken: String
)

data class UserInfo(val id: Int, val name: String, val role: String)
