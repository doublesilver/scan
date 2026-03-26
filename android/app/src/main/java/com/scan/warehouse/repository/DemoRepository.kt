package com.scan.warehouse.repository

import android.content.Context
import com.scan.warehouse.model.ImageItem
import com.scan.warehouse.model.ScanResponse
import com.scan.warehouse.model.SearchItem
import com.scan.warehouse.model.SearchResponse
import kotlinx.coroutines.delay

class DemoRepository(context: Context) : ProductRepository(context) {

    private val products = listOf(
        ScanResponse(
            skuId = "1",
            productName = "쵸미세븐 무당벌레 청소기 레드 105 x 70mm",
            category = "청소용품",
            brand = "쵸미세븐",
            barcodes = listOf("8809461170008"),
            images = listOf(ImageItem(
                filePath = "http://img3.coupangcdn.com/image/product/image/vendoritem/2016/07/07/3013301785/ca384b18-4c33-4c9f-b10c-1e3d2b79089e.jpg",
                imageType = "thumb"
            ))
        ),
        ScanResponse(
            skuId = "2",
            productName = "쵸미세븐 무당벌레 청소기 그린 105 x 70mm",
            category = "청소용품",
            brand = "쵸미세븐",
            barcodes = listOf("8809461170015"),
            images = listOf(ImageItem(
                filePath = "http://img5.coupangcdn.com/image/product/image/vendoritem/2016/05/17/3013301790/2cf62628-00ab-493c-bab1-58c5ba0445ba.jpg",
                imageType = "thumb"
            ))
        ),
        ScanResponse(
            skuId = "3",
            productName = "쵸미세븐 버섯돌이 청소기 레드 95 x 90mm",
            category = "청소용품",
            brand = "쵸미세븐",
            barcodes = listOf("8809461170046"),
            images = listOf(ImageItem(
                filePath = "http://img3.coupangcdn.com/image/product/image/vendoritem/2016/07/07/3013301810/83958126824875-3e46afea-54d9-4253-8de8-c60e554bf343.jpg",
                imageType = "thumb"
            ))
        ),
        ScanResponse(
            skuId = "4",
            productName = "쵸미세븐 버섯돌이 청소기 그린 95 x 90mm",
            category = "청소용품",
            brand = "쵸미세븐",
            barcodes = listOf("8809461170053"),
            images = listOf(ImageItem(
                filePath = "http://img5.coupangcdn.com/image/product/image/vendoritem/2016/05/17/3013301815/4bc4231b-e6a1-4e32-96a3-7dd2688cf96f.jpg",
                imageType = "thumb"
            ))
        ),
        ScanResponse(
            skuId = "5",
            productName = "쵸미세븐 버섯돌이 청소기 핑크 95 x 90mm",
            category = "청소용품",
            brand = "쵸미세븐",
            barcodes = listOf("8809461170077"),
            images = listOf(ImageItem(
                filePath = "http://img3.coupangcdn.com/image/product/image/vendoritem/2016/07/07/3013301825/24ae139e-7c02-4f5f-9097-f9c5db927b2d.jpg",
                imageType = "thumb"
            ))
        )
    )

    override suspend fun scanBarcode(barcode: String): Pair<Result<ScanResponse>, Boolean> {
        delay(300)
        val product = products.find { it.barcodes.contains(barcode) }
            ?: return Pair(Result.failure(Exception("등록되지 않은 바코드입니다")), false)
        return Pair(Result.success(product), false)
    }

    override suspend fun searchProducts(query: String, limit: Int): Pair<Result<SearchResponse>, Boolean> {
        delay(300)
        val matched = products
            .filter { it.productName.contains(query, ignoreCase = true) }
            .map { SearchItem(skuId = it.skuId, productName = it.productName, category = it.category, brand = it.brand, barcode = it.barcodes.firstOrNull()) }
        return Pair(Result.success(SearchResponse(total = matched.size, items = matched)), false)
    }

    override suspend fun healthCheck(): Result<Unit> = Result.success(Unit)

    override fun getImageUrl(filePath: String): String = filePath
}
