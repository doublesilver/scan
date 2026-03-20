package com.scan.warehouse.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class ScanResponse(
    @SerializedName("sku_id") val skuId: String,
    @SerializedName("product_name") val productName: String,
    @SerializedName("category") val category: String?,
    @SerializedName("brand") val brand: String?,
    @SerializedName("barcodes") val barcodes: List<String>,
    @SerializedName("images") val images: List<ImageItem>,
    @SerializedName("quantity") val quantity: Int? = null
) : Parcelable

@Parcelize
data class ImageItem(
    @SerializedName("file_path") val filePath: String,
    @SerializedName("image_type") val imageType: String
) : Parcelable

data class SearchResponse(
    @SerializedName("total") val total: Int,
    @SerializedName("items") val items: List<SearchItem>
)

data class SearchItem(
    @SerializedName("sku_id") val skuId: String,
    @SerializedName("product_name") val productName: String,
    @SerializedName("category") val category: String?,
    @SerializedName("brand") val brand: String?,
    @SerializedName("barcode") val barcode: String? = null
)
