package com.scan.warehouse.repository

import android.content.Context
import com.scan.warehouse.model.BoxResponse
import com.scan.warehouse.model.CartResponse
import com.scan.warehouse.model.FamilyMember
import com.scan.warehouse.model.ImageItem
import com.scan.warehouse.model.MapCell
import com.scan.warehouse.model.MapLayout
import com.scan.warehouse.model.MapLevel
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

    private val IMG_STRAP = "https://img1.coupangcdn.com/image/retail/images/2025/11/13/17/9/02f6a0b6-12e7-4796-b7cc-ddded8ed5268.jpg"
    private val IMG_LADYBUG_RED = "https://img3.coupangcdn.com/image/product/image/vendoritem/2016/07/07/3013301785/ca384b18-4c33-4c9f-b10c-1e3d2b79089e.jpg"
    private val IMG_LADYBUG_GREEN = "https://img5.coupangcdn.com/image/product/image/vendoritem/2016/05/17/3013301790/2cf62628-00ab-493c-bab1-58c5ba0445ba.jpg"

    private val products = listOf(
        ScanResponse(
            skuId = "6947290001",
            productName = "쵸미세븐 무당벌레 청소기 레드 105 x 70mm",
            category = "청소용품",
            brand = "쵸미세븐",
            barcodes = listOf("8809461170008"),
            images = listOf(ImageItem(filePath = IMG_LADYBUG_RED, imageType = "thumb")),
            coupangUrl = "https://www.coupang.com/vp/products/6947290001",
            location = "5층-C-01"
        ),
        ScanResponse(
            skuId = "6947290002",
            productName = "쵸미세븐 무당벌레 청소기 그린 105 x 70mm",
            category = "청소용품",
            brand = "쵸미세븐",
            barcodes = listOf("8809461170015"),
            images = listOf(ImageItem(filePath = IMG_LADYBUG_GREEN, imageType = "thumb")),
            coupangUrl = "https://www.coupang.com/vp/products/6947290002",
            location = "5층-C-02"
        ),
        ScanResponse(
            skuId = "66873488", productName = "매트 그레인 마그넷 링크 실리콘 스트랩 블랙", category = "스트랩", brand = "스페이스쉴드",
            barcodes = listOf("8800341973306"), images = listOf(ImageItem(filePath = IMG_STRAP, imageType = "thumb")),
            coupangUrl = "https://www.coupang.com/vp/products/66873488", location = "5층-A-01"
        ),
        ScanResponse(
            skuId = "66873490", productName = "매트 그레인 마그넷 링크 실리콘 스트랩 미드나잇블루", category = "스트랩", brand = "스페이스쉴드",
            barcodes = listOf("8800341973313"), images = listOf(ImageItem(filePath = IMG_STRAP, imageType = "thumb")),
            coupangUrl = "https://www.coupang.com/vp/products/66873490", location = "5층-A-02"
        ),
        ScanResponse(
            skuId = "66873492", productName = "매트 그레인 마그넷 링크 실리콘 스트랩 그레이", category = "스트랩", brand = "스페이스쉴드",
            barcodes = listOf("8800341973320"), images = listOf(ImageItem(filePath = IMG_STRAP, imageType = "thumb")),
            coupangUrl = "https://www.coupang.com/vp/products/66873492", location = "5층-A-03"
        ),
        ScanResponse(
            skuId = "66873497", productName = "매트 그레인 마그넷 링크 실리콘 스트랩 스타라이트", category = "스트랩", brand = "스페이스쉴드",
            barcodes = listOf("8800341973337"), images = listOf(ImageItem(filePath = IMG_STRAP, imageType = "thumb")),
            coupangUrl = "https://www.coupang.com/vp/products/66873497", location = "5층-A-04"
        ),
        ScanResponse(
            skuId = "66873499", productName = "매트 그레인 마그넷 링크 실리콘 스트랩 라임", category = "스트랩", brand = "스페이스쉴드",
            barcodes = listOf("8800341973344"), images = listOf(ImageItem(filePath = IMG_STRAP, imageType = "thumb")),
            coupangUrl = "https://www.coupang.com/vp/products/66873499", location = "5층-A-05"
        ),
        ScanResponse(
            skuId = "66873501", productName = "매트 그레인 마그넷 링크 실리콘 스트랩 버건디블랙", category = "스트랩", brand = "스페이스쉴드",
            barcodes = listOf("8800341973351"), images = listOf(ImageItem(filePath = IMG_STRAP, imageType = "thumb")),
            coupangUrl = "https://www.coupang.com/vp/products/66873501", location = "5층-A-06"
        ),
        ScanResponse(
            skuId = "66873503", productName = "매트 그레인 마그넷 링크 실리콘 스트랩 스타스카이", category = "스트랩", brand = "스페이스쉴드",
            barcodes = listOf("8800341973368"), images = listOf(ImageItem(filePath = IMG_STRAP, imageType = "thumb")),
            coupangUrl = "https://www.coupang.com/vp/products/66873503", location = "5층-A-07"
        ),
        ScanResponse(
            skuId = "66873505", productName = "매트 그레인 마그넷 링크 실리콘 스트랩 스타오렌지", category = "스트랩", brand = "스페이스쉴드",
            barcodes = listOf("8800341973375"), images = listOf(ImageItem(filePath = IMG_STRAP, imageType = "thumb")),
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

    override suspend fun addToCart(barcode: String, skuId: String, productName: String, quantity: Int): CartResponse {
        delay(300)
        return CartResponse(status = "ok", message = "데모: $productName ${quantity}개 장바구니 추가")
    }

    override suspend fun healthCheck(): Result<Unit> = Result.success(Unit)

    override fun getImageUrl(filePath: String): String = filePath

    override suspend fun getShelves(floor: Int, zone: String): Result<ShelfListResponse> {
        delay(300)
        val shelves = when (zone) {
            "A" -> listOf(
                ShelfItem(1, 5, "A", 1, "스트랩 블랙계열", null, null, "A-1-1"),
                ShelfItem(2, 5, "A", 2, "스트랩 블루계열", null, null, "A-1-2"),
                ShelfItem(3, 5, "A", 3, "스트랩 밝은계열", null, null, "A-1-3"),
                ShelfItem(4, 5, "A", 4, "스트랩 레드계열", null, null, "A-2-1"),
                ShelfItem(5, 5, "A", 5, "스트랩 스타계열", null, null, "A-2-2"),
                ShelfItem(6, 5, "A", 6, "스트랩 혼합", null, null, "A-2-3"),
                ShelfItem(7, 5, "A", 7, "케이스 보관", null, null, "A-3-1"),
                ShelfItem(8, 5, "A", 8, "충전기 보관", null, null, "A-3-2"),
                ShelfItem(9, 5, "A", 9, "패키지 자재", null, null, "A-3-3"),
                ShelfItem(10, 5, "A", 10, "액세서리", null, null, "A-3-4"),
                ShelfItem(11, 5, "A", 11, "샘플 보관", null, null, "A-4-1"),
                ShelfItem(12, 5, "A", 12, null, null, null, "A-4-2")
            )
            "B" -> listOf(
                ShelfItem(13, 5, "B", 1, "포장 작업대", null, null, "B-1-1"),
                ShelfItem(14, 5, "B", 2, "테이프/완충재", null, null, "B-1-2"),
                ShelfItem(15, 5, "B", 3, "박스 보관", null, null, "B-2-1"),
                ShelfItem(16, 5, "B", 4, "라벨 프린터", null, null, "B-2-2"),
                ShelfItem(17, 5, "B", 5, "출고 대기", null, null, "B-3-1"),
                ShelfItem(18, 5, "B", 6, null, null, null, "B-3-2")
            )
            "C" -> listOf(
                ShelfItem(19, 5, "C", 1, "쵸미세븐 레드", null, null, "C-1-1"),
                ShelfItem(20, 5, "C", 2, "쵸미세븐 그린", null, null, "C-1-2"),
                ShelfItem(21, 5, "C", 3, "반품 보관", null, null, "C-1-3"),
                ShelfItem(22, 5, "C", 4, "신규 입고", null, null, "C-2-1"),
                ShelfItem(23, 5, "C", 5, "검수 대기", null, null, "C-2-2"),
                ShelfItem(24, 5, "C", 6, "불량품", null, null, "C-2-3"),
                ShelfItem(25, 5, "C", 7, "예비 자재", null, null, "C-3-1"),
                ShelfItem(26, 5, "C", 8, "시즌 재고", null, null, "C-3-2"),
                ShelfItem(27, 5, "C", 9, null, null, null, "C-3-3")
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

    override suspend fun uploadLevelPhoto(cellKey: String, levelIndex: Int, filePart: MultipartBody.Part): Result<String> {
        delay(500)
        return Result.success("/static/photos/demo_${cellKey}_L${levelIndex}.jpg")
    }

    override suspend fun deleteLevelPhoto(cellKey: String, levelIndex: Int): Result<Unit> {
        delay(300)
        return Result.success(Unit)
    }

    override suspend fun updateProductLocation(skuId: String, location: String): Result<Unit> {
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
                // === Zone A (501호) : 3cols x 4rows = 12셀 ===
                "A-1-1" to MapCell(name = "A-1", label = "스트랩 블랙계열", status = "used",
                    levels = listOf(
                        MapLevel(index = 0, label = "하단 (1층)", itemLabel = "매트그레인 블랙", sku = "66873488", photo = IMG_STRAP),
                        MapLevel(index = 1, label = "중단 (2층)", itemLabel = "매트그레인 미드나잇블루", sku = "66873490", photo = IMG_STRAP),
                        MapLevel(index = 2, label = "상단 (3층)")
                    )
                ),
                "A-1-2" to MapCell(name = "A-2", label = "스트랩 블루계열", status = "used",
                    levels = listOf(
                        MapLevel(index = 0, label = "하단 (1층)", itemLabel = "매트그레인 미드나잇블루", sku = "66873490", photo = IMG_STRAP),
                        MapLevel(index = 1, label = "중단 (2층)", itemLabel = "매트그레인 그레이", sku = "66873492", photo = IMG_STRAP),
                        MapLevel(index = 2, label = "상단 (3층)", itemLabel = "매트그레인 스타스카이", sku = "66873503", photo = IMG_STRAP)
                    )
                ),
                "A-1-3" to MapCell(name = "A-3", label = "스트랩 밝은계열", status = "used",
                    levels = listOf(
                        MapLevel(index = 0, label = "하단 (1층)", itemLabel = "매트그레인 스타라이트", sku = "66873497", photo = IMG_STRAP),
                        MapLevel(index = 1, label = "중단 (2층)", itemLabel = "매트그레인 라임", sku = "66873499", photo = IMG_STRAP),
                        MapLevel(index = 2, label = "상단 (3층)")
                    )
                ),
                "A-2-1" to MapCell(name = "A-4", label = "스트랩 레드계열", status = "used",
                    levels = listOf(
                        MapLevel(index = 0, label = "하단 (1층)", itemLabel = "매트그레인 버건디블랙", sku = "66873501", photo = IMG_STRAP),
                        MapLevel(index = 1, label = "중단 (2층)", itemLabel = "매트그레인 스타오렌지", sku = "66873505", photo = IMG_STRAP),
                        MapLevel(index = 2, label = "상단 (3층)")
                    )
                ),
                "A-2-2" to MapCell(name = "A-5", label = "스트랩 스타계열", status = "used",
                    levels = listOf(
                        MapLevel(index = 0, label = "하단 (1층)", itemLabel = "매트그레인 스타스카이", sku = "66873503", photo = IMG_STRAP),
                        MapLevel(index = 1, label = "중단 (2층)", itemLabel = "매트그레인 스타오렌지", sku = "66873505", photo = IMG_STRAP),
                        MapLevel(index = 2, label = "상단 (3층)", itemLabel = "매트그레인 스타라이트", sku = "66873497", photo = IMG_STRAP)
                    )
                ),
                "A-2-3" to MapCell(name = "A-6", label = "스트랩 혼합", status = "used",
                    levels = listOf(
                        MapLevel(index = 0, label = "하단 (1층)", itemLabel = "매트그레인 블랙", sku = "66873488", photo = IMG_STRAP),
                        MapLevel(index = 1, label = "중단 (2층)"),
                        MapLevel(index = 2, label = "상단 (3층)")
                    )
                ),
                "A-3-1" to MapCell(name = "A-7", label = "케이스 보관", status = "used",
                    levels = listOf(
                        MapLevel(index = 0, label = "하단 (1층)", itemLabel = "매트그레인 그레이", sku = "66873492", photo = IMG_STRAP),
                        MapLevel(index = 1, label = "중단 (2층)"),
                        MapLevel(index = 2, label = "상단 (3층)")
                    )
                ),
                "A-3-2" to MapCell(name = "A-8", label = "충전기 보관", status = "used",
                    levels = listOf(
                        MapLevel(index = 0, label = "하단 (1층)", itemLabel = "매트그레인 라임", sku = "66873499", photo = IMG_STRAP),
                        MapLevel(index = 1, label = "중단 (2층)"),
                        MapLevel(index = 2, label = "상단 (3층)")
                    )
                ),
                "A-3-3" to MapCell(name = "A-9", label = "패키지 자재", status = "used"),
                "A-3-4" to MapCell(name = "A-10", label = "액세서리", status = "used",
                    levels = listOf(
                        MapLevel(index = 0, label = "하단 (1층)", itemLabel = "매트그레인 버건디블랙", sku = "66873501", photo = IMG_STRAP),
                        MapLevel(index = 1, label = "중단 (2층)"),
                        MapLevel(index = 2, label = "상단 (3층)")
                    )
                ),
                "A-4-1" to MapCell(name = "A-11", label = "샘플 보관", status = "used"),
                "A-4-2" to MapCell(name = "A-12", status = "empty"),

                // === Zone B (포장다이) : 3cols x 2rows = 6셀 ===
                "B-1-1" to MapCell(name = "B-1", label = "포장 작업대", status = "used",
                    levels = listOf(
                        MapLevel(index = 0, label = "하단 (1층)", itemLabel = "포장 박스 소", photo = IMG_STRAP),
                        MapLevel(index = 1, label = "중단 (2층)", itemLabel = "포장 박스 중"),
                        MapLevel(index = 2, label = "상단 (3층)")
                    )
                ),
                "B-1-2" to MapCell(name = "B-2", label = "테이프/완충재", status = "used"),
                "B-2-1" to MapCell(name = "B-3", label = "박스 보관", status = "used"),
                "B-2-2" to MapCell(name = "B-4", label = "라벨 프린터", status = "used"),
                "B-3-1" to MapCell(name = "B-5", label = "출고 대기", status = "used",
                    levels = listOf(
                        MapLevel(index = 0, label = "하단 (1층)", itemLabel = "매트그레인 블랙 외 3종", sku = "66873488", photo = IMG_STRAP),
                        MapLevel(index = 1, label = "중단 (2층)", itemLabel = "쵸미세븐 무당벌레 레드", sku = "6947290001", photo = IMG_LADYBUG_RED),
                        MapLevel(index = 2, label = "상단 (3층)")
                    )
                ),
                "B-3-2" to MapCell(name = "B-6", status = "empty"),

                // === Zone C (502호) : 3cols x 3rows = 9셀 ===
                "C-1-1" to MapCell(name = "C-1", label = "쵸미세븐 레드", status = "used",
                    levels = listOf(
                        MapLevel(index = 0, label = "하단 (1층)", itemLabel = "무당벌레 청소기 레드", sku = "6947290001", photo = IMG_LADYBUG_RED),
                        MapLevel(index = 1, label = "중단 (2층)", itemLabel = "무당벌레 청소기 그린", sku = "6947290002", photo = IMG_LADYBUG_GREEN),
                        MapLevel(index = 2, label = "상단 (3층)")
                    )
                ),
                "C-1-2" to MapCell(name = "C-2", label = "쵸미세븐 그린", status = "used",
                    levels = listOf(
                        MapLevel(index = 0, label = "하단 (1층)", itemLabel = "무당벌레 청소기 그린", sku = "6947290002", photo = IMG_LADYBUG_GREEN),
                        MapLevel(index = 1, label = "중단 (2층)"),
                        MapLevel(index = 2, label = "상단 (3층)")
                    )
                ),
                "C-1-3" to MapCell(name = "C-3", label = "반품 보관", status = "used"),
                "C-2-1" to MapCell(name = "C-4", label = "신규 입고", status = "used"),
                "C-2-2" to MapCell(name = "C-5", label = "검수 대기", status = "used"),
                "C-2-3" to MapCell(name = "C-6", label = "불량품", status = "empty"),
                "C-3-1" to MapCell(name = "C-7", label = "예비 자재", status = "used"),
                "C-3-2" to MapCell(name = "C-8", label = "시즌 재고", status = "used"),
                "C-3-3" to MapCell(name = "C-9", status = "empty")
            )
        ))
    }

    override suspend fun scanBox(qrCode: String): Result<BoxResponse> {
        delay(300)
        val box = when (qrCode) {
            "BOX-2026-0001" -> BoxResponse(
                qrCode = qrCode,
                boxName = "외박스 B-2026-0001",
                productMasterName = "매트 그레인 마그넷 링크 실리콘 스트랩",
                productMasterImage = IMG_STRAP,
                location = "5층-A-01",
                coupangUrl = "https://www.coupang.com/vp/products/66873488",
                naverUrl = "https://smartstore.naver.com/spaceshield/products/66873488",
                url1688 = "https://detail.1688.com/offer/66873488.html",
                flowUrl = "https://flow.team/project/spaceshield/task/66873488",
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
            "BOX-2026-0002" -> BoxResponse(
                qrCode = qrCode,
                boxName = "외박스 B-2026-0002",
                productMasterName = "쵸미세븐 무당벌레 청소기",
                productMasterImage = IMG_LADYBUG_RED,
                location = "5층-C-01",
                coupangUrl = "https://www.coupang.com/vp/products/6947290001",
                naverUrl = "https://smartstore.naver.com/chomiseven/products/6947290001",
                url1688 = "https://detail.1688.com/offer/6947290001.html",
                flowUrl = "https://flow.team/project/chomiseven/task/6947290001",
                members = listOf(
                    FamilyMember(skuId = "6947290001", skuName = "무당벌레 청소기 레드", barcode = "8809461170008", location = "5층-C-01"),
                    FamilyMember(skuId = "6947290002", skuName = "무당벌레 청소기 그린", barcode = "8809461170015", location = "5층-C-02")
                )
            )
            else -> BoxResponse(
                qrCode = qrCode,
                boxName = "외박스 $qrCode",
                productMasterName = "매트 그레인 마그넷 링크 실리콘 스트랩",
                productMasterImage = IMG_STRAP,
                location = "5층-A-01",
                coupangUrl = "https://www.coupang.com/vp/products/66873488",
                naverUrl = "https://smartstore.naver.com/spaceshield/products/66873488",
                url1688 = "https://detail.1688.com/offer/66873488.html",
                flowUrl = "https://flow.team/project/spaceshield/task/66873488",
                members = listOf(
                    FamilyMember(skuId = "66873488", skuName = "매트그레인 블랙", barcode = "8800341973306", location = "5층-A-01"),
                    FamilyMember(skuId = "66873490", skuName = "매트그레인 미드나잇블루", barcode = "8800341973313", location = "5층-A-02"),
                    FamilyMember(skuId = "66873492", skuName = "매트그레인 그레이", barcode = "8800341973320", location = "5층-A-03")
                )
            )
        }
        return Result.success(box)
    }
}
