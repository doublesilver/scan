package com.scan.warehouse.ui

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.scan.warehouse.R
import com.scan.warehouse.databinding.ActivityBoxRegisterBinding
import com.scan.warehouse.model.SearchItem
import com.scan.warehouse.repository.ProductRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BoxRegisterActivity : BaseActivity() {

    companion object {
        private const val EXTRA_QR_CODE = "extra_qr_code"

        fun createIntent(context: Context, qrCode: String): Intent {
            return Intent(context, BoxRegisterActivity::class.java).apply {
                putExtra(EXTRA_QR_CODE, qrCode)
            }
        }
    }

    private lateinit var binding: ActivityBoxRegisterBinding
    @Inject lateinit var repository: ProductRepository
    private var qrCode = ""
    private var selectedLocation: String? = null
    private val members = mutableListOf<SearchItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBoxRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        qrCode = intent.getStringExtra(EXTRA_QR_CODE) ?: run { finish(); return }

        binding.tvQrCode.text = qrCode
        binding.btnBack.setOnClickListener { finishWithSlide() }
        binding.btnSelectLocation.setOnClickListener { openMapDialog() }
        binding.btnAddSku.setOnClickListener { showSkuSearchDialog() }
        binding.btnRegister.setOnClickListener { registerBox() }
    }

    private fun openMapDialog() {
        lifecycleScope.launch {
            repository.getMapLayout().onSuccess { layout ->
                WarehouseMapDialog.show(this@BoxRegisterActivity, selectedLocation, layout) { floor, zoneCode, row, col, _ ->
                    val zone = layout.zones.find { it.code == zoneCode }
                    if (zone == null) {
                        Toast.makeText(this@BoxRegisterActivity, "도면이 변경되었습니다. 다시 시도해주세요", Toast.LENGTH_SHORT).show()
                        return@show
                    }
                    val cellNum = (row - 1) * zone.cols + col
                    selectedLocation = "${floor}층-${zoneCode}-${cellNum}"
                    binding.tvLocation.text = selectedLocation
                    binding.tvLocation.setTextColor(ContextCompat.getColor(this@BoxRegisterActivity, R.color.primary))
                }
            }.onFailure {
                Toast.makeText(this@BoxRegisterActivity, "도면 로드 실패", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSkuSearchDialog() {
        val inputLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
        }
        val editText = EditText(this).apply {
            hint = "바코드 또는 상품명 검색"
            textSize = 14f
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        inputLayout.addView(editText)

        AlertDialog.Builder(this)
            .setTitle("SKU 검색")
            .setView(inputLayout)
            .setPositiveButton("검색") { _, _ ->
                val query = editText.text.toString().trim()
                if (query.isNotBlank()) searchAndSelectSku(query)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun searchAndSelectSku(query: String) {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            val (result, _) = repository.searchProducts(query)
            binding.progressBar.visibility = View.GONE
            result.onSuccess { response ->
                if (response.items.isEmpty()) {
                    Toast.makeText(this@BoxRegisterActivity, "검색 결과가 없습니다", Toast.LENGTH_SHORT).show()
                    return@onSuccess
                }
                val items = response.items.filter { item -> members.none { it.skuId == item.skuId } }
                if (items.isEmpty()) {
                    Toast.makeText(this@BoxRegisterActivity, "이미 추가된 SKU입니다", Toast.LENGTH_SHORT).show()
                    return@onSuccess
                }
                val names = items.map { "${it.productName} (${it.skuId})" }.toTypedArray()
                AlertDialog.Builder(this@BoxRegisterActivity)
                    .setTitle("SKU 선택")
                    .setItems(names) { _, which ->
                        addMember(items[which])
                    }
                    .setNegativeButton("취소", null)
                    .show()
            }.onFailure {
                Toast.makeText(this@BoxRegisterActivity, "검색 실패: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addMember(item: SearchItem) {
        members.add(item)
        refreshMemberList()
    }

    private fun removeMember(index: Int) {
        members.removeAt(index)
        refreshMemberList()
    }

    private fun refreshMemberList() {
        binding.layoutMembers.removeAllViews()
        binding.tvMemberEmpty.visibility = if (members.isEmpty()) View.VISIBLE else View.GONE
        val density = resources.displayMetrics.density

        members.forEachIndexed { index, item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val pad = (8 * density).toInt()
                setPadding(pad, pad, pad, pad)
                background = ContextCompat.getDrawable(this@BoxRegisterActivity, R.drawable.bg_info_row)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (4 * density).toInt()
                }
            }

            val textContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textContainer.addView(TextView(this).apply {
                text = item.productName
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@BoxRegisterActivity, R.color.on_surface))
                setTypeface(null, Typeface.BOLD)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            textContainer.addView(TextView(this).apply {
                text = "SKU: ${item.skuId}"
                textSize = 11f
                setTextColor(ContextCompat.getColor(this@BoxRegisterActivity, R.color.on_surface_variant))
            })
            row.addView(textContainer)

            val btnRemove = ImageButton(this).apply {
                val size = (32 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size)
                setImageResource(R.drawable.ic_close)
                setColorFilter(ContextCompat.getColor(this@BoxRegisterActivity, R.color.error))
                setBackgroundResource(android.R.color.transparent)
                contentDescription = "삭제"
                setOnClickListener { removeMember(index) }
            }
            row.addView(btnRemove)

            binding.layoutMembers.addView(row)
        }
    }

    private fun registerBox() {
        val productMasterName = binding.etProductMasterName.text.toString().trim()
        if (productMasterName.isBlank()) {
            Toast.makeText(this, "상품 마스터명을 입력해주세요", Toast.LENGTH_SHORT).show()
            return
        }
        if (members.isEmpty()) {
            Toast.makeText(this, "멤버 SKU를 1개 이상 추가해주세요", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnRegister.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        val data = mutableMapOf<String, Any>(
            "qr_code" to qrCode,
            "box_name" to "외박스 $qrCode",
            "product_master_name" to productMasterName,
            "members" to members.map { mapOf(
                "sku_id" to it.skuId,
                "sku_name" to it.productName,
                "barcode" to (it.barcode ?: ""),
                "location" to ""
            ) }
        )
        if (selectedLocation != null) {
            data["location"] = selectedLocation!!
        }

        lifecycleScope.launch {
            repository.createBox(data).onSuccess { box ->
                Toast.makeText(this@BoxRegisterActivity, "등록 완료", Toast.LENGTH_SHORT).show()
                startWithSlide(BoxDetailActivity.createIntent(this@BoxRegisterActivity, box))
                finish()
            }.onFailure { e ->
                Toast.makeText(this@BoxRegisterActivity, "등록 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            binding.btnRegister.isEnabled = true
            binding.progressBar.visibility = View.GONE
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
