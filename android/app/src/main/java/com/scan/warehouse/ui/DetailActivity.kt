package com.scan.warehouse.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.SpannableStringBuilder
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import coil.load
import com.scan.warehouse.R
import com.scan.warehouse.WarehouseApp
import com.scan.warehouse.databinding.ActivityDetailBinding
import com.scan.warehouse.model.ScanResponse
import com.scan.warehouse.repository.ProductRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class DetailActivity : BaseActivity() {

    companion object {
        const val EXTRA_DATA = "extra_data"
    }

    private lateinit var binding: ActivityDetailBinding
    @Inject lateinit var repository: ProductRepository
    private var showingThumbnail = true
    private var thumbnailUrl: String? = null
    private var realImageUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finishWithSlide() }

        bindData()
    }

    private fun showQuantityDialog(data: ScanResponse) {
        val density = resources.displayMetrics.density
        val pad = (20 * density).toInt()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
        }

        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "수량 입력"
            setText("1")
            selectAll()
            textSize = 20f
            gravity = android.view.Gravity.CENTER
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            val mb = (12 * density).toInt()
            setPadding(0, 0, 0, mb)
        }

        listOf(1, 5, 10, 100).forEach { qty ->
            val btn = com.google.android.material.button.MaterialButton(
                this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = qty.toString()
                textSize = 16f
                minimumWidth = 0
                minWidth = 0
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (4 * density).toInt()
                    marginEnd = (4 * density).toInt()
                }
                layoutParams = lp
                setOnClickListener { input.setText(qty.toString()); input.selectAll() }
            }
            btnRow.addView(btn)
        }

        layout.addView(btnRow)
        layout.addView(input)

        AlertDialog.Builder(this)
            .setTitle("라벨 인쇄")
            .setMessage("수량을 선택하거나 직접 입력하세요")
            .setView(layout)
            .setPositiveButton("인쇄") { _, _ ->
                val qty = input.text.toString().toIntOrNull() ?: 1
                if (qty in 1..100) {
                    printLabel(data, qty)
                } else {
                    Toast.makeText(this, "1~100 사이 수량을 입력하세요", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun printLabel(data: ScanResponse, quantity: Int) {
        val barcode = data.barcodes.firstOrNull() ?: return
        binding.btnBarPrint.isEnabled = false
        Toast.makeText(this, "인쇄 요청 중...", Toast.LENGTH_SHORT).show()
        val appCtx = applicationContext
        // Activity 라이프사이클과 분리된 appScope 사용 — 뒤로가기 해도 결과 Toast가 뜸
        (application as WarehouseApp).appScope.launch {
            val (resultMsg, isError) = try {
                val result = repository.printLabel(barcode, data.skuId, data.productName, quantity)
                result.message to false
            } catch (e: retrofit2.HttpException) {
                val detail = try {
                    val body = e.response()?.errorBody()?.string().orEmpty()
                    org.json.JSONObject(body).optString("detail", "")
                } catch (_: Exception) { "" }
                val msg = if (detail.isNotBlank()) "인쇄 실패: $detail" else "인쇄 실패 (HTTP ${e.code()})"
                msg to true
            } catch (e: Exception) {
                val reason = e.message?.take(80) ?: "연결 오류"
                "인쇄 요청 실패: $reason" to true
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    appCtx,
                    resultMsg,
                    if (isError) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
                ).show()
                if (!isFinishing && !isDestroyed) {
                    binding.btnBarPrint.isEnabled = true
                }
            }
        }
    }


    private fun bindData() {
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_DATA, ScanResponse::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_DATA)
        } ?: return

        binding.tvDetailProductName.text = BarcodeUtils.applyColorKeywords(data.productName)
        binding.tvDetailSkuId.text = data.skuId
        binding.tvDetailCategory.text = data.category ?: "-"

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

        if (data.location != null) {
            binding.layoutLocation.visibility = View.VISIBLE
            binding.tvDetailLocation.text = data.location
        }

        binding.btnBarMap.visibility = View.VISIBLE
        binding.btnBarMap.setOnClickListener {
            lifecycleScope.launch {
                val layout = repository.getMapLayout().getOrNull()
                WarehouseMapDialog.show(this@DetailActivity, data.location, layout) { floor, zone, _, _, cellKey ->
                    startWithSlide(CellDetailActivity.createIntent(this@DetailActivity, floor, zone, cellKey))
                }
            }
        }

        binding.btnBarBuy.visibility = View.VISIBLE
        binding.btnBarBuy.setOnClickListener {
            if (data.coupangUrl != null) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(data.coupangUrl)))
            } else {
                Toast.makeText(this, "구매 링크가 없습니다", Toast.LENGTH_SHORT).show()
            }
        }

        val thumbImg = data.images.firstOrNull { it.filePath.startsWith("img/") }
            ?: data.images.firstOrNull()
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

        binding.btnBarPrint.setOnClickListener { showQuantityDialog(data) }

        binding.btnBarEdit.visibility = View.GONE

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
