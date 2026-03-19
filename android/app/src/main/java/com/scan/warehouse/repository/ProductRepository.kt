package com.scan.warehouse.repository

import android.content.Context
import com.scan.warehouse.model.ScanResponse
import com.scan.warehouse.model.SearchResponse
import com.scan.warehouse.network.RetrofitClient

class ProductRepository(private val context: Context) {

    private val api get() = RetrofitClient.getApiService(context)

    suspend fun scanBarcode(barcode: String): Result<ScanResponse> {
        return try {
            val response = api.scanBarcode(barcode)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchProducts(query: String, limit: Int = 20): Result<SearchResponse> {
        return try {
            val response = api.searchProducts(query, limit)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
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
}
