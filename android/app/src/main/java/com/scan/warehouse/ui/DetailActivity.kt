package com.scan.warehouse.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.scan.warehouse.R
import com.scan.warehouse.databinding.ActivityDetailBinding
import com.scan.warehouse.network.RetrofitClient

class DetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SKU_ID = "extra_sku_id"
        const val EXTRA_PRODUCT_NAME = "extra_product_name"
        const val EXTRA_CATEGORY = "extra_category"
        const val EXTRA_BRAND = "extra_brand"
        const val EXTRA_BARCODES = "extra_barcodes"
        const val EXTRA_IMAGE_PATHS = "extra_image_paths"
        const val EXTRA_IMAGE_TYPES = "extra_image_types"
    }

    private lateinit var binding: ActivityDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "상품 상세"

        bindData()
    }

    private fun bindData() {
        val skuId = intent.getStringExtra(EXTRA_SKU_ID) ?: return
        val productName = intent.getStringExtra(EXTRA_PRODUCT_NAME) ?: ""
        val category = intent.getStringExtra(EXTRA_CATEGORY)
        val brand = intent.getStringExtra(EXTRA_BRAND)
        val barcodes = intent.getStringArrayListExtra(EXTRA_BARCODES) ?: emptyList<String>()
        val imagePaths = intent.getStringArrayListExtra(EXTRA_IMAGE_PATHS) ?: emptyList<String>()
        val imageTypes = intent.getStringArrayListExtra(EXTRA_IMAGE_TYPES) ?: emptyList<String>()

        binding.tvDetailProductName.text = productName
        binding.tvDetailSkuId.text = skuId
        binding.tvDetailCategory.text = category ?: "-"
        binding.tvDetailBrand.text = brand ?: "-"
        binding.tvDetailBarcodes.text = barcodes.joinToString("\n").ifEmpty { "-" }

        val baseUrl = RetrofitClient.getBaseUrl(this)
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        // 실사 이미지 우선, 없으면 썸네일
        val realIndex = imageTypes.indexOfFirst { it == "real" }
        val imageIndex = if (realIndex >= 0) realIndex else 0

        if (imagePaths.isNotEmpty() && imageIndex < imagePaths.size) {
            val imageUrl = "${normalized}api/image/${imagePaths[imageIndex]}"
            binding.ivDetailImage.load(imageUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_placeholder)
                error(R.drawable.ic_placeholder)
            }
        } else {
            binding.ivDetailImage.setImageResource(R.drawable.ic_placeholder)
        }

        // 썸네일 목록 표시 (실사 이미지 외 나머지)
        if (imagePaths.size > 1) {
            binding.tvImageCount.visibility = View.VISIBLE
            binding.tvImageCount.text = "이미지 ${imagePaths.size}장"
        } else {
            binding.tvImageCount.visibility = View.GONE
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
