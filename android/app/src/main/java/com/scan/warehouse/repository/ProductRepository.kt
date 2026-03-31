package com.scan.warehouse.repository

import android.content.Context
import com.google.gson.Gson
import com.scan.warehouse.db.AppDatabase
import com.scan.warehouse.db.CachedProduct
import com.scan.warehouse.model.BoxResponse
import com.scan.warehouse.model.CartRequest
import com.scan.warehouse.model.MapLayout
import com.scan.warehouse.model.CartResponse
import com.scan.warehouse.model.ImageItem
import com.scan.warehouse.model.PrintRequest
import com.scan.warehouse.model.PrintResponse
import com.scan.warehouse.model.ScanResponse
import com.scan.warehouse.model.SearchItem
import com.scan.warehouse.model.SearchResponse
import com.scan.warehouse.model.ShelfItem
import com.scan.warehouse.model.ShelfListResponse
import com.scan.warehouse.network.RetrofitClient
import okhttp3.MultipartBody

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

    open suspend fun addToCart(barcode: String, skuId: String, productName: String, quantity: Int): CartResponse {
        return api.addToCart(CartRequest(barcode, skuId, productName, quantity))
    }

    open suspend fun printLabel(barcode: String, skuId: String, productName: String, quantity: Int): PrintResponse {
        return api.printLabel(PrintRequest(barcode, skuId, productName, quantity))
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

    open suspend fun scanBox(qrCode: String): Result<BoxResponse> {
        return try {
            Result.success(api.getBox(qrCode))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getShelves(floor: Int, zone: String): Result<ShelfListResponse> {
        return try {
            Result.success(api.getShelves(floor, zone))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun updateMapCell(cellKey: String, data: Map<String, Any>): Result<Unit> {
        return try {
            api.updateMapCell(cellKey, data)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun updateShelfLabel(shelfId: Int, label: String): Result<ShelfItem> {
        return try {
            Result.success(api.updateShelfLabel(shelfId, mapOf("label" to label)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun deleteShelfLabel(shelfId: Int): Result<Unit> {
        return try {
            api.deleteShelfLabel(shelfId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun uploadCellPhoto(cellKey: String, filePart: MultipartBody.Part): Result<String> {
        return try {
            val result = api.uploadCellPhoto(cellKey, filePart)
            Result.success(result["photo_url"] ?: "")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun deleteCellPhoto(cellKey: String): Result<Unit> {
        return try {
            api.deleteCellPhoto(cellKey)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun uploadLevelPhoto(cellKey: String, levelIndex: Int, filePart: MultipartBody.Part): Result<String> {
        return try {
            val result = api.uploadLevelPhoto(cellKey, levelIndex, filePart)
            Result.success(result["photo_url"] ?: "")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun deleteLevelPhoto(cellKey: String, levelIndex: Int): Result<Unit> {
        return try {
            api.deleteLevelPhoto(cellKey, levelIndex)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getMapLayout(): Result<MapLayout> {
        return try {
            Result.success(api.getMapLayout())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

}
