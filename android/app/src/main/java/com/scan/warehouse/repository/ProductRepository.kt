package com.scan.warehouse.repository

import android.content.Context
import com.google.gson.Gson
import com.scan.warehouse.db.AppDatabase
import com.scan.warehouse.db.CachedProduct
import com.scan.warehouse.model.ImageItem
import com.scan.warehouse.model.ScanResponse
import com.scan.warehouse.model.SearchItem
import com.scan.warehouse.model.SearchResponse
import com.scan.warehouse.network.RetrofitClient

class ProductRepository(private val context: Context) {

    private val api get() = RetrofitClient.getApiService(context)
    private val dao get() = AppDatabase.getInstance(context).productDao()
    private val gson = Gson()

    private var _isOffline = false
    val isOffline: Boolean get() = _isOffline

    suspend fun scanBarcode(barcode: String): Result<ScanResponse> {
        return try {
            val response = api.scanBarcode(barcode)
            _isOffline = false
            cacheProduct(barcode, response)
            Result.success(response)
        } catch (e: Exception) {
            val cached = dao.getByBarcode(barcode)
            if (cached != null) {
                _isOffline = true
                Result.success(cached.toScanResponse())
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun searchProducts(query: String, limit: Int = 20): Result<SearchResponse> {
        return try {
            val response = api.searchProducts(query, limit)
            _isOffline = false
            Result.success(response)
        } catch (e: Exception) {
            val cached = dao.searchByName(query)
            if (cached.isNotEmpty()) {
                _isOffline = true
                val items = cached.map { it.toSearchItem() }
                Result.success(SearchResponse(total = items.size, items = items))
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun healthCheck(): Result<Unit> {
        return try {
            api.healthCheck()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getImageUrl(filePath: String): String {
        val baseUrl = RetrofitClient.getBaseUrl(context)
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return "${normalized}api/image/${filePath}"
    }

    private suspend fun cacheProduct(barcode: String, response: ScanResponse) {
        val imageUrls = gson.toJson(response.images)
        val cached = CachedProduct(
            barcode = barcode,
            skuId = response.skuId,
            productName = response.productName,
            categoryName = response.category,
            brandName = response.brand,
            imageUrls = imageUrls
        )
        dao.insertAll(listOf(cached))
    }

    private fun CachedProduct.toScanResponse(): ScanResponse {
        val images: List<ImageItem> = try {
            gson.fromJson(imageUrls, Array<ImageItem>::class.java)?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        return ScanResponse(
            skuId = skuId,
            productName = productName,
            category = categoryName,
            brand = brandName,
            barcodes = listOf(barcode),
            images = images
        )
    }

    private fun CachedProduct.toSearchItem(): SearchItem {
        return SearchItem(
            skuId = skuId,
            productName = productName,
            category = categoryName,
            brand = brandName
        )
    }
}
