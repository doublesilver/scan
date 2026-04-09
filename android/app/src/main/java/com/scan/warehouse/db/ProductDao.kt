package com.scan.warehouse.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProductDao {

    @Query("SELECT * FROM cached_products WHERE barcode = :barcode")
    suspend fun getByBarcode(barcode: String): CachedProduct?

    @Query("SELECT * FROM cached_products WHERE productName LIKE '%' || :query || '%' LIMIT :limit")
    suspend fun searchByName(query: String, limit: Int = 20): List<CachedProduct>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<CachedProduct>)

    @Query("SELECT COUNT(*) FROM cached_products")
    suspend fun getCount(): Int
}
