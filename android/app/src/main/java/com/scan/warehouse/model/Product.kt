package com.scan.warehouse.model

import com.google.gson.annotations.SerializedName

data class ScanResponse(
    @SerializedName("sku_id") val skuId: String,
    @SerializedName("product_name") val productName: String,
    @SerializedName("category") val category: String?,
    @SerializedName("brand") val brand: String?,
    @SerializedName("barcodes") val barcodes: List<String>,
    @SerializedName("images") val images: List<ImageItem>
)

data class ImageItem(
    @SerializedName("file_path") val filePath: String,
    @SerializedName("image_type") val imageType: String
)

data class SearchResponse(
    @SerializedName("total") val total: Int,
    @SerializedName("items") val items: List<SearchItem>
)

data class SearchItem(
    @SerializedName("sku_id") val skuId: String,
    @SerializedName("product_name") val productName: String,
    @SerializedName("category") val category: String?,
    @SerializedName("brand") val brand: String?
)
