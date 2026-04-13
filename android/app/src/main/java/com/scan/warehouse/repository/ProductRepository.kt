package com.scan.warehouse.repository

import android.content.Context
import com.google.gson.Gson
import com.scan.warehouse.db.AppDatabase
import com.scan.warehouse.db.CachedProduct
import com.scan.warehouse.model.BoxResponse
import com.scan.warehouse.model.CellDetail
import com.scan.warehouse.model.MapLayout
import com.scan.warehouse.model.ImageItem
import com.scan.warehouse.model.PrintRequest
import com.scan.warehouse.model.PrintResponse
import com.scan.warehouse.model.ScanResponse
import com.scan.warehouse.model.SearchItem
import com.scan.warehouse.model.SearchResponse
import com.scan.warehouse.model.ShelfItem
import com.scan.warehouse.model.ShelfListResponse
import com.scan.warehouse.model.Zone
import com.scan.warehouse.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody

open class ProductRepository(protected val context: Context) {

    private val api get() = RetrofitClient.getApiService(context)
    private val dao get() = AppDatabase.getInstance(context).productDao()
    private val gson = Gson()

    private suspend fun <T> safeCall(block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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
            val cached = dao.searchByName(query, limit)
            if (cached.isNotEmpty()) {
                val items = cached.map { it.toSearchItem() }
                Pair(Result.success(SearchResponse(total = items.size, items = items)), true)
            } else {
                Pair(Result.failure(e), false)
            }
        }
    }

    open suspend fun healthCheck(): Result<Unit> = safeCall {
        api.healthCheck()
    }

    open fun getImageUrl(filePath: String): String {
        val baseUrl = RetrofitClient.getBaseUrl(context)
        val base = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
        if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
            // 외부 URL은 서버 프록시 경유 (PDA가 인터넷 접근 불가한 환경 대응 + 캐싱)
            val encoded = java.net.URLEncoder.encode(filePath, "UTF-8")
            return "$base/api/image/$encoded"
        }
        if (filePath.startsWith("/static/")) {
            return "$base$filePath"
        }
        return "$base/api/image/$filePath"
    }

    private suspend fun cacheProduct(barcode: String, response: ScanResponse) {
        val imageUrls = withContext(Dispatchers.Default) { gson.toJson(response.images) }
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

    open suspend fun scanBox(qrCode: String): Result<BoxResponse> = safeCall { api.getBox(qrCode) }

    open suspend fun uploadProductMasterImage(
        masterId: Int,
        file: MultipartBody.Part,
        imageType: okhttp3.RequestBody
    ): Result<Map<String, Any>> = safeCall {
        api.uploadProductMasterImage(masterId, file, imageType)
    }

    open suspend fun deleteProductMasterImage(masterId: Int, imageId: Int): Result<Map<String, String>> = safeCall {
        api.deleteProductMasterImage(masterId, imageId)
    }

    open suspend fun getShelves(floor: Int, zone: String): Result<ShelfListResponse> = safeCall { api.getShelves(floor, zone) }

    open suspend fun updateMapCell(cellKey: String, data: Map<String, Any>): Result<Unit> = safeCall { api.updateMapCell(cellKey, data) }

    open suspend fun updateShelfLabel(shelfId: Int, label: String): Result<ShelfItem> = safeCall {
        api.updateShelfLabel(shelfId, mapOf("label" to label))
    }

    open suspend fun deleteShelfLabel(shelfId: Int): Result<Unit> = safeCall { api.deleteShelfLabel(shelfId) }

    open suspend fun uploadCellPhoto(cellKey: String, filePart: MultipartBody.Part): Result<String> = safeCall {
        val result = api.uploadCellPhoto(cellKey, filePart)
        result["photo_url"] ?: ""
    }

    open suspend fun deleteCellPhoto(cellKey: String): Result<Unit> = safeCall { api.deleteCellPhoto(cellKey) }

    open suspend fun uploadLevelPhoto(cellKey: String, levelIndex: Int, filePart: MultipartBody.Part): Result<String> = safeCall {
        val result = api.uploadLevelPhoto(cellKey, levelIndex, filePart)
        result["photo_url"] ?: ""
    }

    open suspend fun deleteLevelPhoto(cellKey: String, levelIndex: Int): Result<Unit> = safeCall { api.deleteLevelPhoto(cellKey, levelIndex) }

    open suspend fun getMapLayout(): Result<MapLayout> = safeCall { api.getMapLayout() }

    open suspend fun updateProductLocation(skuId: String, location: String): Result<Unit> = safeCall {
        api.updateProductLocation(skuId, mapOf("location" to location))
    }

    open suspend fun createBox(data: Map<String, Any>): Result<BoxResponse> = safeCall { api.createBox(data) }

    open suspend fun updateBox(qrCode: String, data: Map<String, String>): Result<Unit> = safeCall { api.updateBox(qrCode, data); Unit }

    open suspend fun addBoxMember(qrCode: String, data: Map<String, String>): Result<Unit> = safeCall { api.addBoxMember(qrCode, data); Unit }

    open suspend fun removeBoxMember(qrCode: String, skuId: String): Result<Unit> = safeCall { api.removeBoxMember(qrCode, skuId); Unit }

    open suspend fun getZones(): Result<List<Zone>> = safeCall { api.getZones() }
    open suspend fun createZone(data: Map<String, Any>): Result<Zone> = safeCall { api.createZone(data) }
    open suspend fun updateZone(zoneId: Int, data: Map<String, Any>): Result<Zone> = safeCall { api.updateZone(zoneId, data) }
    open suspend fun deleteZone(zoneId: Int): Result<Unit> = safeCall { api.deleteZone(zoneId); Unit }
    open suspend fun getZoneCells(zoneId: Int): Result<List<CellDetail>> = safeCall { api.getZoneCells(zoneId) }
    open suspend fun getCellDetail(cellId: Int): Result<CellDetail> = safeCall { api.getCellDetail(cellId) }
    open suspend fun addCellLevel(cellId: Int, label: String): Result<Unit> = safeCall { api.addCellLevel(cellId, mapOf("label" to label)); Unit }
    open suspend fun deleteLevel(levelId: Int): Result<Unit> = safeCall { api.deleteLevel(levelId); Unit }
    open suspend fun addLevelProduct(levelId: Int, data: Map<String, String>): Result<Unit> = safeCall { api.addLevelProduct(levelId, data); Unit }
    open suspend fun removeLevelProduct(productId: Int): Result<Unit> = safeCall { api.removeLevelProduct(productId); Unit }
    open suspend fun uploadLevelProductPhoto(productId: Int, filePart: MultipartBody.Part): Result<Unit> = safeCall { api.uploadLevelProductPhoto(productId, filePart); Unit }
    open suspend fun deleteLevelProductPhoto(productId: Int): Result<Unit> = safeCall { api.deleteLevelProductPhoto(productId); Unit }

    open suspend fun uploadProductImage(skuId: String, file: MultipartBody.Part): Result<Map<String, String>> = safeCall {
        api.uploadProductImage(skuId, file)
    }

    open suspend fun deleteProductImage(skuId: String, imageId: Int): Result<Map<String, String>> = safeCall {
        api.deleteProductImage(skuId, imageId)
    }

    open suspend fun updateProduct(skuId: String, data: Map<String, String>): Result<Map<String, String>> = safeCall {
        api.updateProduct(skuId, data)
    }

}
