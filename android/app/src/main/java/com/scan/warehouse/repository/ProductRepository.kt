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

open class ProductRepository(protected val context: Context) {

    private val api get() = RetrofitClient.getApiService(context)
    private val dao get() = AppDatabase.getInstance(context).productDao()
    private val gson = Gson()

    open suspend fun scanBarcode(barcode: String): Pair<Result<ScanResponse>, Boolean> {
        return try {
            val scanData = api.scanBarcode(barcode)
            cacheProduct(barcode, scanData)
            Pair(Result.success(scanData), false)
        } catch (e: Exception) {
            val cached = dao.getByBarcode(barcode)
            if (cached != null) {
                Pair(Result.success(cached.toScanResponse()), true)
            } else {
                Pair(Result.failure(e), false)
            }
        }
    }

    open suspend fun searchProducts(query: String, limit: Int = 20): Pair<Result<SearchResponse>, Boolean> {
        return try {
            val response = api.searchProducts(query, limit)
            Pair(Result.success(response), false)
        } catch (e: Exception) {
            val cached = dao.searchByName(query)
            if (cached.isNotEmpty()) {
                val items = cached.map { it.toSearchItem() }
                Pair(Result.success(SearchResponse(total = items.size, items = items)), true)
            } else {
                Pair(Result.failure(e), false)
            }
        }
    }

    open suspend fun healthCheck(): Result<Unit> {
        return try {
            api.healthCheck()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open fun getImageUrl(filePath: String): String {
        if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
            return filePath
        }
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
            brand = brandName,
            barcode = barcode
        )
    }
}
