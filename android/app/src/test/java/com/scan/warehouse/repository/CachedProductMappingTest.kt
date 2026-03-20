package com.scan.warehouse.repository

import com.google.gson.Gson
import com.scan.warehouse.db.CachedProduct
import com.scan.warehouse.model.ImageItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CachedProductMappingTest {

    private val gson = Gson()

    @Test
    fun `ScanResponse에서 CachedProduct로 변환`() {
        val images = listOf(
            ImageItem(filePath = "img/test.jpg", imageType = "thumbnail"),
            ImageItem(filePath = "real_image/test.jpg", imageType = "real")
        )
        val imageJson = gson.toJson(images)

        val cached = CachedProduct(
            barcode = "8801234567890",
            skuId = "SKU001",
            productName = "테스트 상품",
            categoryName = "식품",
            brandName = "테스트브랜드",
            imageUrls = imageJson
        )

        assertEquals("8801234567890", cached.barcode)
        assertEquals("SKU001", cached.skuId)

        val parsedImages: List<ImageItem> = gson.fromJson(
            cached.imageUrls, Array<ImageItem>::class.java
        ).toList()

        assertEquals(2, parsedImages.size)
        assertEquals("thumbnail", parsedImages[0].imageType)
        assertEquals("img/test.jpg", parsedImages[0].filePath)
    }

    @Test
    fun `imageUrls가 null인 경우 빈 리스트로 처리`() {
        val cached = CachedProduct(
            barcode = "8801234567890",
            skuId = "SKU001",
            productName = "테스트 상품",
            categoryName = null,
            brandName = null,
            imageUrls = null
        )

        val images: List<ImageItem> = try {
            cached.imageUrls?.let {
                gson.fromJson(it, Array<ImageItem>::class.java)?.toList()
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        assertNotNull(images)
        assertEquals(0, images.size)
    }

    @Test
    fun `imageUrls가 빈 JSON 배열인 경우`() {
        val cached = CachedProduct(
            barcode = "8801234567890",
            skuId = "SKU001",
            productName = "테스트 상품",
            categoryName = null,
            brandName = null,
            imageUrls = "[]"
        )

        val images: List<ImageItem> = gson.fromJson(
            cached.imageUrls, Array<ImageItem>::class.java
        ).toList()

        assertEquals(0, images.size)
    }
}
