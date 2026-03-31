package com.scan.warehouse.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class ScanResponse(
    @SerializedName("sku_id") val skuId: String,
    @SerializedName("product_name") val productName: String,
    @SerializedName("category") val category: String? = null,
    @SerializedName("brand") val brand: String? = null,
    @SerializedName("barcodes") val barcodes: List<String>,
    @SerializedName("images") val images: List<ImageItem>,
    @SerializedName("quantity") val quantity: Int? = null,
    @SerializedName("coupang_url") val coupangUrl: String? = null,
    @SerializedName("location") val location: String? = null,
) : Parcelable

@Parcelize
data class ImageItem(
    @SerializedName("file_path") val filePath: String,
    @SerializedName("image_type") val imageType: String,
) : Parcelable

data class SearchResponse(
    @SerializedName("total") val total: Int,
    @SerializedName("items") val items: List<SearchItem>
)

data class SearchItem(
    @SerializedName("sku_id") val skuId: String,
    @SerializedName("product_name") val productName: String,
    @SerializedName("category") val category: String? = null,
    @SerializedName("brand") val brand: String? = null,
    @SerializedName("barcode") val barcode: String? = null,
    @SerializedName("thumbnail") val thumbnail: String? = null
)

data class PrintRequest(
    @SerializedName("barcode") val barcode: String,
    @SerializedName("sku_id") val skuId: String,
    @SerializedName("product_name") val productName: String,
    @SerializedName("quantity") val quantity: Int
)

data class PrintResponse(
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String
)

data class CartRequest(
    @SerializedName("barcode") val barcode: String,
    @SerializedName("sku_id") val skuId: String,
    @SerializedName("product_name") val productName: String,
    @SerializedName("quantity") val quantity: Int
)

data class CartResponse(
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String
)

data class FamilyMember(
    @SerializedName("sku_id") val skuId: String,
    @SerializedName("sku_name") val skuName: String,
    @SerializedName("barcode") val barcode: String? = null,
    @SerializedName("location") val location: String? = null
)

data class BoxResponse(
    @SerializedName("qr_code") val qrCode: String,
    @SerializedName("box_name") val boxName: String,
    @SerializedName("product_master_name") val productMasterName: String,
    @SerializedName("product_master_image") val productMasterImage: String? = null,
    @SerializedName("location") val location: String? = null,
    @SerializedName("members") val members: List<FamilyMember>
)

data class ShelfItem(
    @SerializedName("id") val id: Int,
    @SerializedName("floor") val floor: Int,
    @SerializedName("zone") val zone: String,
    @SerializedName("shelf_number") val shelfNumber: Int,
    @SerializedName("label") val label: String? = null,
    @SerializedName("photo_path") val photoPath: String? = null,
    @SerializedName("photo_url") val photoUrl: String? = null
)

data class ShelfListResponse(
    @SerializedName("floor") val floor: Int,
    @SerializedName("zone") val zone: String,
    @SerializedName("shelves") val shelves: List<ShelfItem>
)


