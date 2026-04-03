package com.scan.warehouse.model

import com.google.gson.annotations.SerializedName

data class Zone(
    @SerializedName("id") val id: Int,
    @SerializedName("code") val code: String,
    @SerializedName("name") val name: String,
    @SerializedName("rows") val rows: Int,
    @SerializedName("cols") val cols: Int
)

data class CellDetail(
    @SerializedName("id") val id: Int,
    @SerializedName("zone_id") val zoneId: Int? = null,
    @SerializedName("zone_code") val zoneCode: String? = null,
    @SerializedName("row") val row: Int,
    @SerializedName("col") val col: Int,
    @SerializedName("label") val label: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("bg_color") val bgColor: String? = null,
    @SerializedName("levels") val levels: List<LevelDetail>? = null
)

data class LevelDetail(
    @SerializedName("id") val id: Int,
    @SerializedName("level_index") val levelIndex: Int,
    @SerializedName("label") val label: String? = null,
    @SerializedName("products") val products: List<LevelProduct>? = null
)

data class LevelProduct(
    @SerializedName("id") val id: Int,
    @SerializedName("product_master_id") val productMasterId: Int? = null,
    @SerializedName("product_master_name") val productMasterName: String? = null,
    @SerializedName("photo") val photo: String? = null,
    @SerializedName("memo") val memo: String? = null,
    @SerializedName("sort_order") val sortOrder: Int = 0
)
