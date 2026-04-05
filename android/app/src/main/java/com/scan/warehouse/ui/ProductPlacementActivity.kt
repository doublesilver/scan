package com.scan.warehouse.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.scan.warehouse.R
import com.scan.warehouse.databinding.ActivityProductPlacementBinding
import com.scan.warehouse.model.MapLayout
import com.scan.warehouse.model.MapLevel
import com.scan.warehouse.model.ScanResponse
import com.scan.warehouse.repository.ProductRepository
import com.scan.warehouse.scanner.DataWedgeManager
import com.scan.warehouse.viewmodel.CellDetailViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ProductPlacementActivity : BaseActivity() {

    private lateinit var binding: ActivityProductPlacementBinding
    @Inject lateinit var repository: ProductRepository

    private var scannedProduct: ScanResponse? = null
    private var mapLayout: MapLayout? = null
    private val keystrokeBuffer = StringBuilder()
    private var lastKeystrokeTime = 0L

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, ProductPlacementActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductPlacementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "상품 배치"

        binding.btnSelectCell.isEnabled = false
        binding.btnSelectCell.setOnClickListener { loadMapAndSelectCell() }
        binding.btnReset.setOnClickListener { resetToScan() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                DataWedgeManager.scanFlow.collect { barcode ->
                    handleScan(barcode)
                }
            }
        }
    }

    private fun handleScan(barcode: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.cardProduct.visibility = View.GONE
        binding.layoutScanHint.visibility = View.GONE
        binding.btnSelectCell.isEnabled = false

        lifecycleScope.launch {
            val (result, _) = repository.scanBarcode(barcode)
            binding.progressBar.visibility = View.GONE
            result.onSuccess { product ->
                scannedProduct = product
                showProductCard(product)
            }.onFailure {
                binding.layoutScanHint.visibility = View.VISIBLE
                Toast.makeText(this@ProductPlacementActivity, "등록된 상품이 없습니다: $barcode", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showProductCard(product: ScanResponse) {
        binding.tvProductName.text = product.productName
        binding.tvSku.text = "SKU: ${product.skuId}"
        binding.tvCurrentLocation.apply {
            if (product.location.isNullOrEmpty()) {
                text = "위치 미등록"
                setTextColor(getColor(R.color.on_surface_variant))
            } else {
                text = "현재 위치: ${product.location}"
                setTextColor(getColor(R.color.primary))
            }
        }

        val imageUrl = product.images.firstOrNull()?.filePath
        if (imageUrl != null) {
            binding.ivProduct.load(repository.getImageUrl(imageUrl)) {
                crossfade(true)
                placeholder(R.drawable.ic_placeholder)
                error(R.drawable.ic_placeholder)
            }
        } else {
            binding.ivProduct.setImageResource(R.drawable.ic_placeholder)
        }

        binding.layoutScanHint.visibility = View.GONE
        binding.cardProduct.visibility = View.VISIBLE
        binding.btnSelectCell.isEnabled = true
        binding.btnReset.visibility = View.VISIBLE
    }

    private fun loadMapAndSelectCell() {
        val cached = mapLayout
        if (cached != null) {
            showMapDialog(cached)
            return
        }
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            repository.getMapLayout().onSuccess { layout ->
                mapLayout = layout
                binding.progressBar.visibility = View.GONE
                showMapDialog(layout)
            }.onFailure {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@ProductPlacementActivity, "도면을 불러올 수 없습니다", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showMapDialog(layout: MapLayout) {
        WarehouseMapDialog.show(
            context = this,
            location = null,
            mapLayout = layout,
            onCellClick = { floor, zone, row, col, cellKey ->
                val cell = layout.cells[cellKey]
                val zoneColCount = layout.zones.find { it.code == zone }?.cols ?: 4
                val seqNum = (row - 1) * zoneColCount + col
                val levels = cell?.levels?.takeIf { it.isNotEmpty() } ?: CellDetailViewModel.DEFAULT_LEVELS
                showLevelPickerDialog(floor, zone, seqNum, cellKey, levels)
            }
        )
    }

    private fun showLevelPickerDialog(
        floor: Int, zone: String, seqNum: Int, cellKey: String, levels: List<MapLevel>
    ) {
        val displayLevels = levels.reversed()
        val labels = displayLevels.map { level ->
            val current = if (!level.itemLabel.isNullOrEmpty()) "  (현재: ${level.itemLabel})" else "  비어있음"
            "${level.label}$current"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("${zone}구역 ${zone}-$seqNum — 층 선택")
            .setItems(labels) { _, which ->
                val selectedLevel = displayLevels[which]
                val product = scannedProduct ?: return@setItems
                AlertDialog.Builder(this)
                    .setTitle("배치 확인")
                    .setMessage("'${product.productName}'\n\n→ ${zone}구역 ${zone}-$seqNum\n→ ${selectedLevel.label}")
                    .setPositiveButton("배치") { _, _ ->
                        savePlacement(floor, zone, seqNum, cellKey, levels, selectedLevel.index, product)
                    }
                    .setNegativeButton("취소", null)
                    .show()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun savePlacement(
        floor: Int, zone: String, seqNum: Int, cellKey: String,
        currentLevels: List<MapLevel>, levelIndex: Int, product: ScanResponse
    ) {
        binding.progressBar.visibility = View.VISIBLE
        val levels = currentLevels.toMutableList()
        if (levelIndex < levels.size) {
            levels[levelIndex] = levels[levelIndex].copy(
                itemLabel = product.productName,
                sku = product.skuId
            )
        }
        val payload = levels.map { lv ->
            mapOf(
                "index" to lv.index,
                "label" to lv.label,
                "itemLabel" to (lv.itemLabel ?: ""),
                "sku" to (lv.sku ?: ""),
                "photo" to (lv.photo ?: "")
            )
        }

        lifecycleScope.launch {
            repository.updateMapCell(cellKey, mapOf("levels" to payload))
                .onSuccess {
                    repository.updateProductLocation(product.skuId, "${floor}층-$zone-$seqNum")
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@ProductPlacementActivity, "배치 완료: ${zone}구역 ${zone}-$seqNum", Toast.LENGTH_SHORT).show()
                    resetToScan()
                }
                .onFailure { e ->
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@ProductPlacementActivity, "배치 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun resetToScan() {
        scannedProduct = null
        binding.cardProduct.visibility = View.GONE
        binding.layoutScanHint.visibility = View.VISIBLE
        binding.btnSelectCell.isEnabled = false
        binding.btnReset.visibility = View.GONE
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.keyCode == KeyEvent.KEYCODE_ENTER && keystrokeBuffer.isNotBlank()) {
                val barcode = keystrokeBuffer.toString()
                keystrokeBuffer.clear()
                handleScan(barcode)
                return true
            }
            val char = event.unicodeChar.toChar()
            if (char.isDigit()) {
                val now = System.currentTimeMillis()
                if (now - lastKeystrokeTime > 300) keystrokeBuffer.clear()
                lastKeystrokeTime = now
                keystrokeBuffer.append(char)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        DataWedgeManager.register(this)
    }

    override fun onPause() {
        super.onPause()
        DataWedgeManager.unregister(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finishWithSlide(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
