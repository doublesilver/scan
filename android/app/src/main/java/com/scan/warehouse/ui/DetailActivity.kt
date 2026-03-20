package com.scan.warehouse.ui

import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.scan.warehouse.databinding.ActivityDetailBinding
import com.scan.warehouse.model.ScanResponse
import com.scan.warehouse.network.RetrofitClient

class DetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DATA = "extra_data"
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
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_DATA, ScanResponse::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_DATA)
        } ?: return

        binding.tvDetailProductName.text = data.productName
        binding.tvDetailSkuId.text = data.skuId
        binding.tvDetailCategory.text = data.category ?: "-"
        binding.tvDetailBrand.text = data.brand ?: "-"
        binding.tvDetailBarcodes.text = data.barcodes.joinToString("\n").ifEmpty { "-" }

        val baseUrl = RetrofitClient.getBaseUrl(this)
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        val imageUrls = data.images.map { "${normalized}api/image/${it.filePath}" }

        if (imageUrls.isNotEmpty()) {
            binding.vpDetailImages.adapter = ImagePagerAdapter(imageUrls)

            if (imageUrls.size > 1) {
                binding.tvImageCount.visibility = View.VISIBLE
                binding.tvImageCount.text = "이미지 ${imageUrls.size}장"
            }
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
