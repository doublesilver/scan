package com.scan.warehouse.repository

import android.content.Context
import com.scan.warehouse.model.BoxResponse
import com.scan.warehouse.model.CartResponse
import com.scan.warehouse.model.FamilyMember
import com.scan.warehouse.model.ImageItem
import com.scan.warehouse.model.MapCell
import com.scan.warehouse.model.MapLayout
import com.scan.warehouse.model.MapZone
import com.scan.warehouse.model.PrintResponse
import com.scan.warehouse.model.ScanResponse
import com.scan.warehouse.model.SearchItem
import com.scan.warehouse.model.SearchResponse
import com.scan.warehouse.model.ShelfItem
import com.scan.warehouse.model.ShelfListResponse
import kotlinx.coroutines.delay
import okhttp3.MultipartBody

class DemoRepository(context: Context) : ProductRepository(context) {

    private val products = listOf(
        ScanResponse(
            skuId = "6947290001",
            productName = "쵸미세븐 무당벌레 청소기 레드 105 x 70mm",
            category = "청소용품",
            brand = "쵸미세븐",
            barcodes = listOf("8809461170008"),
            images = listOf(ImageItem(
                filePath = "https://img3.coupangcdn.com/image/product/image/vendoritem/2016/07/07/3013301785/ca384b18-4c33-4c9f-b10c-1e3d2b79089e.jpg",
                imageType = "thumb"
            )),
            coupangUrl = "https://www.coupang.com/vp/products/6947290001",
            location = "5층-A-03"
        ),
        ScanResponse(
            skuId = "6947290002",
            productName = "쵸미세븐 무당벌레 청소기 그린 105 x 70mm",
            category = "청소용품",
            brand = "쵸미세븐",
            barcodes = listOf("8809461170015"),
            images = listOf(ImageItem(
                filePath = "https://img5.coupangcdn.com/image/product/image/vendoritem/2016/05/17/3013301790/2cf62628-00ab-493c-bab1-58c5ba0445ba.jpg",
                imageType = "thumb"
            )),
            coupangUrl = "https://www.coupang.com/vp/products/6947290002",
            location = "5층-B-07"
        ),
        ScanResponse(
            skuId = "66873488", productName = "매트 그레인 마그넷 링크 실리콘 스트랩 블랙", category = "스트랩", brand = "스페이스쉴드",
            barcodes = listOf("8800341973306"), images = listOf(ImageItem(filePath = "https://img1.coupangcdn.com/image/retail/images/2025/11/13/17/9/02f6a0b6-12e7-4796-b7cc-ddded8ed5268.jpg", imageType = "thumb")),
            coupangUrl = "https://www.coupang.com/vp/products/66873488", location = "5층-A-01"
        ),
        ScanResponse(
            skuId = "66873490", productName = "매트 그레인 마그넷 링크 실리콘 스트랩 미드나잇블루", category = "스트랩", brand = "스페이스쉴드",
            barcodes = listOf("8800341973313"), images = listOf(ImageItem(filePath = "https://img1.coupangcdn.com/image/retail/images/2025/11/13/17/9/02f6a0b6-12e7-4796-b7cc-ddded8ed5268.jpg", imageType = "thumb")),
            coupangUrl = "https://www.coupang.com/vp/products/66873490", location = "5층-A-02"
        ),
        ScanResponse(
            skuId = "66873492", productName = "매트 그레인 마그넷 링크 실리콘 스트랩 그레이", category = "스트랩", brand = "스페이스쉴드",
            barcodes = listOf("8800341973320"), images = listOf(ImageItem(filePath = "https://img1.coupangcdn.com/image/retail/images/2025/11/13/17/9/02f6a0b6-12e7-4796-b7cc-ddded8ed5268.jpg", imageType = "thumb")),
            coupangUrl = "https://www.coupang.com/vp/products/66873492", location = "5층-A-03"
        ),
        ScanResponse(
            skuId = "66873497", productName = "매트 그레인 마그넷 링크 실리콘 스트랩 스타라이트", category = "스트랩", brand = "스페이스쉴드",
            barcodes = listOf("8800341973337"), images = listOf(ImageItem(filePath = "https://img1.coupangcdn.com/image/retail/images/2025/11/13/17/9/02f6a0b6-12e7-4796-b7cc-ddded8ed5268.jpg", imageType = "thumb")),
            coupangUrl = "https://www.coupang.com/vp/products/66873497", location = "5층-A-04"
        ),
        ScanResponse(
            skuId = "66873499", productName = "매트 그레인 마그넷 링크 실리콘 스트랩 라임", category = "스트랩", brand = "스페이스쉴드",
            barcodes = listOf("8800341973344"), images = listOf(ImageItem(filePath = "https://img1.coupangcdn.com/image/retail/images/2025/11/13/17/9/02f6a0b6-12e7-4796-b7cc-ddded8ed5268.jpg", imageType = "thumb")),
            coupangUrl = "https://www.coupang.com/vp/products/66873499", location = "5층-A-05"
        ),
        ScanResponse(
            skuId = "66873501", productName = "매트 그레인 마그넷 링크 실리콘 스트랩 버건디블랙", category = "스트랩", brand = "스페이스쉴드",
            barcodes = listOf("8800341973351"), images = listOf(ImageItem(filePath = "https://img1.coupangcdn.com/image/retail/images/2025/11/13/17/9/02f6a0b6-12e7-4796-b7cc-ddded8ed5268.jpg", imageType = "thumb")),
            coupangUrl = "https://www.coupang.com/vp/products/66873501", location = "5층-A-06"
        ),
        ScanResponse(
            skuId = "66873503", productName = "매트 그레인 마그넷 링크 실리콘 스트랩 스타스카이", category = "스트랩", brand = "스페이스쉴드",
            barcodes = listOf("8800341973368"), images = listOf(ImageItem(filePath = "https://img1.coupangcdn.com/image/retail/images/2025/11/13/17/9/02f6a0b6-12e7-4796-b7cc-ddded8ed5268.jpg", imageType = "thumb")),
            coupangUrl = "https://www.coupang.com/vp/products/66873503", location = "5층-A-07"
        ),
        ScanResponse(
            skuId = "66873505", productName = "매트 그레인 마그넷 링크 실리콘 스트랩 스타오렌지", category = "스트랩", brand = "스페이스쉴드",
            barcodes = listOf("8800341973375"), images = listOf(ImageItem(filePath = "https://img1.coupangcdn.com/image/retail/images/2025/11/13/17/9/02f6a0b6-12e7-4796-b7cc-ddded8ed5268.jpg", imageType = "thumb")),
            coupangUrl = "https://www.coupang.com/vp/products/66873505", location = "5층-A-08"
        ),
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
            .filter {
                it.productName.contains(query, ignoreCase = true) ||
                it.skuId.contains(query) ||
                it.barcodes.any { b -> b.contains(query) }
            }
            .map { SearchItem(skuId = it.skuId, productName = it.productName, category = it.category, brand = it.brand, barcode = it.barcodes.firstOrNull(), thumbnail = it.images.firstOrNull()?.filePath) }
        return Pair(Result.success(SearchResponse(total = matched.size, items = matched)), false)
    }

override suspend fun printLabel(barcode: String, skuId: String, productName: String, quantity: Int): PrintResponse {
        delay(300)
        return PrintResponse(status = "dry_run", message = "데모 모드: ${quantity}장 인쇄 시뮬레이션")
    }

    override suspend fun healthCheck(): Result<Unit> = Result.success(Unit)

    override fun getImageUrl(filePath: String): String = filePath

    override suspend fun getShelves(floor: Int, zone: String): Result<ShelfListResponse> {
        delay(300)
        val shelves = when (zone) {
            "A" -> listOf(
                ShelfItem(1, 5, "A", 1, "스트랩 보관", null, null),
                ShelfItem(2, 5, "A", 2, "케이스 보관", null, null),
                ShelfItem(3, 5, "A", 3, "충전기 보관", null, null),
                ShelfItem(4, 5, "A", 4, "패키지 자재", null, null),
                ShelfItem(5, 5, "A", 5, null, null, null)
            )
            "B" -> listOf(
                ShelfItem(6, 5, "B", 1, "실리콘 스트랩", null, null),
                ShelfItem(7, 5, "B", 2, "메탈 스트랩", null, null),
                ShelfItem(8, 5, "B", 3, null, null, null)
            )
            else -> listOf(
                ShelfItem(100, floor, zone, 1, "선반 1", null, null),
                ShelfItem(101, floor, zone, 2, null, null, null)
            )
        }
        return Result.success(ShelfListResponse(floor, zone, shelves))
    }

    override suspend fun updateMapCell(cellKey: String, data: Map<String, Any>): Result<Unit> {
        delay(300)
        return Result.success(Unit)
    }

    override suspend fun updateShelfLabel(shelfId: Int, label: String): Result<ShelfItem> {
        delay(300)
        return Result.success(ShelfItem(shelfId, 5, "A", 1, label, null, null))
    }

    override suspend fun deleteShelfLabel(shelfId: Int): Result<Unit> {
        delay(300)
        return Result.success(Unit)
    }

    override suspend fun uploadCellPhoto(cellKey: String, filePart: MultipartBody.Part): Result<String> {
        delay(500)
        return Result.success("/static/photos/demo.jpg")
    }

    override suspend fun deleteCellPhoto(cellKey: String): Result<Unit> {
        delay(300)
        return Result.success(Unit)
    }

    override suspend fun getMapLayout(): Result<MapLayout> {
        return Result.success(MapLayout(
            title = "창고 도면",
            floor = 5,
            zones = listOf(
                MapZone("A", "501호", 3, 4),
                MapZone("B", "포장다이", 3, 2),
                MapZone("C", "502호", 3, 3)
            ),
            cells = mapOf(
                "A-1-1" to MapCell(name = "1-1", label = "스트랩 보관", status = "used"),
                "A-1-2" to MapCell(name = "1-2", label = "케이스 보관", status = "used"),
                "B-1-1" to MapCell(name = "1-1", label = "포장대", status = "used")
            )
        ))
    }

    override suspend fun scanBox(qrCode: String): Result<BoxResponse> {
        delay(300)
        return Result.success(
            BoxResponse(
                qrCode = qrCode,
                boxName = "외박스 B-2026-0330",
                productMasterName = "매트 그레인 마그넷 링크 실리콘 스트랩",
                productMasterImage = "https://img1.coupangcdn.com/image/retail/images/2025/11/13/17/9/02f6a0b6-12e7-4796-b7cc-ddded8ed5268.jpg",
                location = "5층-A구역",
                members = listOf(
                    FamilyMember(skuId = "66873488", skuName = "매트그레인 블랙", barcode = "8800341973306", location = "5층-A-01"),
                    FamilyMember(skuId = "66873490", skuName = "매트그레인 미드나잇블루", barcode = "8800341973313", location = "5층-A-02"),
                    FamilyMember(skuId = "66873492", skuName = "매트그레인 그레이", barcode = "8800341973320", location = "5층-A-03"),
                    FamilyMember(skuId = "66873497", skuName = "매트그레인 스타라이트", barcode = "8800341973337", location = "5층-A-04"),
                    FamilyMember(skuId = "66873499", skuName = "매트그레인 라임", barcode = "8800341973344", location = "5층-A-05"),
                    FamilyMember(skuId = "66873501", skuName = "매트그레인 버건디블랙", barcode = "8800341973351", location = "5층-A-06"),
                    FamilyMember(skuId = "66873503", skuName = "매트그레인 스타스카이", barcode = "8800341973368", location = "5층-A-07"),
                    FamilyMember(skuId = "66873505", skuName = "매트그레인 스타오렌지", barcode = "8800341973375", location = "5층-A-08")
                )
            )
        )
    }


}
