package com.scan.warehouse.ui

import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.scan.warehouse.R
import com.scan.warehouse.databinding.ActivityDetailBinding
import com.scan.warehouse.model.ScanResponse
import com.scan.warehouse.network.RetrofitClient

class DetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DATA = "extra_data"
    }

    private lateinit var binding: ActivityDetailBinding
    private var showingThumbnail = true
    private var thumbnailUrl: String? = null
    private var realImageUrl: String? = null

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

        val barcodeText = SpannableStringBuilder()
        data.barcodes.forEachIndexed { index, barcode ->
            if (index > 0) barcodeText.append("\n")
            barcodeText.append(BarcodeUtils.formatBold(barcode))
        }
        binding.tvDetailBarcodes.text = if (barcodeText.isNotEmpty()) barcodeText else SpannableStringBuilder("-")

        val baseUrl = RetrofitClient.getBaseUrl(this)
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        val thumbImg = data.images.firstOrNull { it.filePath.startsWith("img/") }
        val realImg = data.images.firstOrNull { it.filePath.startsWith("real_image/") }

        thumbnailUrl = thumbImg?.let { "${normalized}api/image/${it.filePath}" }
        realImageUrl = realImg?.let { "${normalized}api/image/${it.filePath}" }

        val initialUrl = thumbnailUrl ?: realImageUrl
        if (initialUrl != null) {
            showingThumbnail = thumbnailUrl != null
            binding.ivDetailImage.load(initialUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_placeholder)
                error(R.drawable.ic_placeholder)
            }
        }

        if (thumbnailUrl != null && realImageUrl != null) {
            binding.ivDetailImage.setOnClickListener {
                showingThumbnail = !showingThumbnail
                val url = if (showingThumbnail) thumbnailUrl else realImageUrl
                binding.ivDetailImage.load(url) {
                    crossfade(true)
                    placeholder(R.drawable.ic_placeholder)
                    error(R.drawable.ic_placeholder)
                }
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
