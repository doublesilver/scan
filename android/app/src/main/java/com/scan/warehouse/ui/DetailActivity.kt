package com.scan.warehouse.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.scan.warehouse.R
import com.scan.warehouse.databinding.ActivityDetailBinding
import com.scan.warehouse.model.ScanResponse
import com.scan.warehouse.repository.ProductRepository

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

        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

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

        val barcodeString = data.barcodes.joinToString(", ")
        binding.tvDetailBarcodes.setOnLongClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("barcode", barcodeString))
            Toast.makeText(this, R.string.barcode_copied, Toast.LENGTH_SHORT).show()
            true
        }

        val repository = ProductRepository(this)

        val thumbImg = data.images.firstOrNull { it.filePath.startsWith("img/") }
        val realImg = data.images.firstOrNull { it.filePath.startsWith("real_image/") }

        thumbnailUrl = thumbImg?.let { repository.getImageUrl(it.filePath) }
        realImageUrl = realImg?.let { repository.getImageUrl(it.filePath) }

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
            binding.tvImageTypeChip.visibility = View.VISIBLE
            updateImageTypeChip()

            binding.ivDetailImage.setOnClickListener {
                showingThumbnail = !showingThumbnail
                val url = if (showingThumbnail) thumbnailUrl else realImageUrl
                binding.ivDetailImage.load(url) {
                    crossfade(true)
                    placeholder(R.drawable.ic_placeholder)
                    error(R.drawable.ic_placeholder)
                }
                updateImageTypeChip()
            }
        }
    }

    private fun updateImageTypeChip() {
        val typeText = if (showingThumbnail) {
            getString(R.string.image_type_thumbnail)
        } else {
            getString(R.string.image_type_real)
        }
        binding.tvImageTypeChip.text = "$typeText | ${getString(R.string.tap_to_switch_image)}"
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
