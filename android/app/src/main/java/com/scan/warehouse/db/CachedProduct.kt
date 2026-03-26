package com.scan.warehouse.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_products")
data class CachedProduct(
    @PrimaryKey val barcode: String,
    val skuId: String,
    val productName: String,
    val categoryName: String?,
    val brandName: String?,
    val imageUrls: String?
)
