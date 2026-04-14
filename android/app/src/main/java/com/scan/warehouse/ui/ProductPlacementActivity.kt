package com.scan.warehouse.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.scan.warehouse.R
import com.scan.warehouse.databinding.ActivityProductPlacementBinding
import com.scan.warehouse.model.BoxResponse
import com.scan.warehouse.model.MapLayout
import com.scan.warehouse.model.MapLevel
import com.scan.warehouse.model.ScanResponse
import com.scan.warehouse.repository.ProductRepository
import com.scan.warehouse.scanner.DataWedgeManager
import com.scan.warehouse.viewmodel.CellDetailViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class ProductPlacementActivity : BaseActivity() {

    private lateinit var binding: ActivityProductPlacementBinding
    @Inject lateinit var repository: ProductRepository

    private enum class ScanType { BARCODE, QR }
    private enum class PhotoTarget { CELL, BOX }

    private var scanType = ScanType.BARCODE
    private var scannedProduct: ScanResponse? = null
    private var scannedBox: BoxResponse? = null
    private var mapLayout: MapLayout? = null

    private data class PlacementTarget(
        val floor: Int, val zone: String, val seqNum: Int,
        val cellKey: String, val levels: List<MapLevel>
    )
    private var pendingTarget: PlacementTarget? = null

    private var photoTarget = PhotoTarget.CELL
    private var pendingCellPhotoUri: Uri? = null
    private var pendingBoxPhotoUri: Uri? = null

    private val keystrokeBuffer = StringBuilder()
    private var lastKeystrokeTime = 0L

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, ProductPlacementActivity::class.java)
    }

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) doLaunchCamera() else Toast.makeText(this, "카메라 권한이 필요합니다", Toast.LENGTH_SHORT).show()
    }

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (!success) return@registerForActivityResult
        val uri = if (photoTarget == PhotoTarget.CELL) pendingCellPhotoUri else pendingBoxPhotoUri
        if (uri != null) applyPhotoPreview(uri)
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        if (photoTarget == PhotoTarget.CELL) pendingCellPhotoUri = uri else pendingBoxPhotoUri = uri
        applyPhotoPreview(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductPlacementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "상품 배치"
        window.navigationBarColor = ContextCompat.getColor(this, R.color.surface)

        binding.btnSelectCell.isEnabled = false
        binding.btnSelectCell.setOnClickListener { loadMapAndSelectCell() }
        binding.btnConfirmPlacement.setOnClickListener { savePlacement() }
        binding.btnReset.setOnClickListener { resetToScan() }

        binding.btnCellCamera.setOnClickListener { launchPhoto(PhotoTarget.CELL) }
        binding.btnCellGallery.setOnClickListener { photoTarget = PhotoTarget.CELL; galleryLauncher.launch("image/*") }
        binding.btnBoxCamera.setOnClickListener { launchPhoto(PhotoTarget.BOX) }
        binding.btnBoxGallery.setOnClickListener { photoTarget = PhotoTarget.BOX; galleryLauncher.launch("image/*") }

        setupSearch()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                DataWedgeManager.scanFlow.collect { barcode -> handleScan(barcode) }
            }
        }
    }

    private fun setupSearch() {
        binding.btnSearch.setOnClickListener { performSearch() }
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else false
        }
    }

    private fun performSearch() {
        val query = binding.etSearch.text.toString().trim()
        if (query.isBlank()) return

        // 바코드/QR 형식이면 바로 스캔 처리
        if (query.matches(Regex(DataWedgeManager.BARCODE_PATTERN)) || query.startsWith("BOX-")) {
            handleScan(query)
            return
        }

        // 상품명 검색
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val (result, _) = repository.searchProducts(query)
            binding.progressBar.visibility = View.GONE
            result.onSuccess { response ->
                if (response.items.isEmpty()) {
                    Toast.makeText(this@ProductPlacementActivity, "검색 결과가 없습니다", Toast.LENGTH_SHORT).show()
                    return@onSuccess
                }
                val labels = response.items.map { item ->
                    val code = item.barcode ?: item.skuId
                    "${item.productName}\n$code"
                }.toTypedArray()
                AlertDialog.Builder(this@ProductPlacementActivity)
                    .setTitle("검색 결과 (${response.items.size}건)")
                    .setItems(labels) { _, which ->
                        val selected = response.items[which]
                        val barcode = selected.barcode
                        if (barcode != null) {
                            handleScan(barcode)
                        } else {
                            Toast.makeText(this@ProductPlacementActivity, "바코드 정보가 없습니다", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("취소", null)
                    .show()
            }.onFailure { e ->
                Toast.makeText(this@ProductPlacementActivity, "검색 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleScan(barcode: String) {
        pendingTarget = null
        pendingCellPhotoUri = null
        pendingBoxPhotoUri = null

        binding.progressBar.visibility = View.VISIBLE
        binding.layoutScanHint.visibility = View.GONE
        binding.layoutContent.visibility = View.GONE
        binding.btnSelectCell.isEnabled = false
        binding.btnSelectCell.visibility = View.VISIBLE
        binding.btnConfirmPlacement.visibility = View.GONE
        binding.btnReset.visibility = View.GONE

        lifecycleScope.launch {
            if (barcode.startsWith("BOX-")) {
                val result = repository.scanBox(barcode)
                binding.progressBar.visibility = View.GONE
                result.onSuccess { box ->
                    scannedBox = box; scannedProduct = null; scanType = ScanType.QR
                    showBoxCard(box)
                }.onFailure {
                    binding.layoutScanHint.visibility = View.VISIBLE
                    Toast.makeText(this@ProductPlacementActivity, "등록되지 않은 외박스입니다: $barcode", Toast.LENGTH_SHORT).show()
                }
            } else {
                val (result, _) = repository.scanBarcode(barcode)
                binding.progressBar.visibility = View.GONE
                result.onSuccess { product ->
                    scannedProduct = product; scannedBox = null; scanType = ScanType.BARCODE
                    showProductCard(product)
                }.onFailure {
                    binding.layoutScanHint.visibility = View.VISIBLE
                    Toast.makeText(this@ProductPlacementActivity, "등록된 상품이 없습니다: $barcode", Toast.LENGTH_SHORT).show()
                }
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
                crossfade(true); placeholder(R.drawable.ic_placeholder); error(R.drawable.ic_placeholder)
            }
        } else {
            binding.ivProduct.setImageResource(R.drawable.ic_placeholder)
        }
        showContentArea()
    }

    private fun showBoxCard(box: BoxResponse) {
        binding.tvProductName.text = box.boxName
        binding.tvSku.text = "외박스 QR: ${box.qrCode}"
        binding.tvCurrentLocation.apply {
            if (box.location.isNullOrEmpty()) {
                text = "위치 미등록"
                setTextColor(getColor(R.color.on_surface_variant))
            } else {
                text = "현재 위치: ${box.location}"
                setTextColor(getColor(R.color.primary))
            }
        }
        val imageUrl = box.productMasterImage
        if (!imageUrl.isNullOrEmpty()) {
            binding.ivProduct.load(imageUrl) {
                crossfade(true); placeholder(R.drawable.ic_placeholder); error(R.drawable.ic_placeholder)
            }
        } else {
            binding.ivProduct.setImageResource(R.drawable.ic_placeholder)
        }
        showContentArea()
    }

    private fun showContentArea() {
        binding.layoutContent.visibility = View.VISIBLE
        binding.layoutConfirm.visibility = View.GONE
        binding.layoutScanHint.visibility = View.GONE
        binding.btnSelectCell.isEnabled = true
        binding.btnSelectCell.visibility = View.VISIBLE
        binding.btnConfirmPlacement.visibility = View.GONE
        binding.btnReset.visibility = View.VISIBLE
    }

    private fun loadMapAndSelectCell() {
        val cached = mapLayout
        if (cached != null) { showMapDialog(cached); return }

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
                pendingTarget = PlacementTarget(floor, zone, seqNum, cellKey, levels)
                showConfirmationPanel()
            }
        )
    }

    private fun showConfirmationPanel() {
        val target = pendingTarget ?: return
        val name = if (scanType == ScanType.BARCODE) scannedProduct?.productName ?: "" else scannedBox?.boxName ?: ""

        binding.tvDestinationInfo.text = "배치 위치: ${target.zone}구역 ${target.zone}-${target.seqNum}\n$name"
        binding.layoutBoxPhotoSection.visibility = if (scanType == ScanType.QR) View.VISIBLE else View.GONE

        binding.ivCellPhotoPreview.visibility = View.GONE
        binding.ivBoxPhotoPreview.visibility = View.GONE
        pendingCellPhotoUri = null
        pendingBoxPhotoUri = null

        binding.layoutConfirm.visibility = View.VISIBLE
        binding.btnSelectCell.visibility = View.GONE
        binding.btnConfirmPlacement.visibility = View.VISIBLE
    }

    private fun savePlacement() {
        val target = pendingTarget ?: return
        binding.progressBar.visibility = View.VISIBLE
        binding.btnConfirmPlacement.isEnabled = false

        val (itemLabel, sku) = when (scanType) {
            ScanType.BARCODE -> Pair(scannedProduct?.productName ?: "", scannedProduct?.skuId ?: "")
            ScanType.QR -> Pair(scannedBox?.boxName ?: "", scannedBox?.qrCode ?: "")
        }

        val levels = target.levels.toMutableList()
        if (levels.isNotEmpty()) {
            levels[0] = levels[0].copy(itemLabel = itemLabel, sku = sku)
        }
        val payload = levels.map { lv ->
            mapOf("index" to lv.index, "label" to lv.label,
                "itemLabel" to (lv.itemLabel ?: ""), "sku" to (lv.sku ?: ""), "photo" to (lv.photo ?: ""))
        }

        lifecycleScope.launch {
            repository.updateMapCell(target.cellKey, mapOf("levels" to payload))
                .onSuccess {
                    if (scanType == ScanType.BARCODE) {
                        scannedProduct?.let { product ->
                            repository.updateProductLocation(product.skuId, "${target.floor}층-${target.zone}-${target.seqNum}")
                                .onFailure { e -> Log.w("ProductPlacement", "위치 동기화 실패", e) }
                        }
                    }
                    pendingCellPhotoUri?.let { uploadPhoto(it, target.cellKey, 0) }
                    if (scanType == ScanType.QR) {
                        pendingBoxPhotoUri?.let { uploadPhoto(it, target.cellKey, 0) }
                    }
                    binding.progressBar.visibility = View.GONE
                    binding.btnConfirmPlacement.isEnabled = true
                    Toast.makeText(this@ProductPlacementActivity, "배치 완료: ${target.zone}구역 ${target.zone}-${target.seqNum}", Toast.LENGTH_SHORT).show()
                    resetToScan()
                }
                .onFailure { e ->
                    binding.progressBar.visibility = View.GONE
                    binding.btnConfirmPlacement.isEnabled = true
                    Toast.makeText(this@ProductPlacementActivity, "배치 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private suspend fun uploadPhoto(uri: Uri, cellKey: String, levelIndex: Int) {
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
            val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
            val ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
            val part = MultipartBody.Part.createFormData("file", "photo.$ext", bytes.toRequestBody(mimeType.toMediaTypeOrNull()))
            repository.uploadLevelPhoto(cellKey, levelIndex, part)
                .onFailure { e ->
                    Log.w("ProductPlacement", "사진 업로드 실패", e)
                    Toast.makeText(this, "배치는 완료됐으나 사진 업로드 실패: 다시 업로드 필요", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            Log.w("ProductPlacement", "사진 업로드 실패", e)
            Toast.makeText(this, "배치는 완료됐으나 사진 업로드 실패: 다시 업로드 필요", Toast.LENGTH_LONG).show()
        }
    }

    private fun resetToScan() {
        scannedProduct = null; scannedBox = null; pendingTarget = null
        pendingCellPhotoUri = null; pendingBoxPhotoUri = null
        mapLayout = null
        binding.layoutContent.visibility = View.GONE
        binding.layoutScanHint.visibility = View.VISIBLE
        binding.btnSelectCell.isEnabled = false
        binding.btnSelectCell.visibility = View.VISIBLE
        binding.btnConfirmPlacement.visibility = View.GONE
        binding.btnReset.visibility = View.GONE
    }

    private fun launchPhoto(target: PhotoTarget) {
        photoTarget = target
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            doLaunchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun doLaunchCamera() {
        val file = File(cacheDir, "photos").also { it.mkdirs() }
            .let { File(it, "placement_${photoTarget.name.lowercase()}_${System.currentTimeMillis()}.jpg") }
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        if (photoTarget == PhotoTarget.CELL) pendingCellPhotoUri = uri else pendingBoxPhotoUri = uri
        takePicture.launch(uri)
    }

    private fun applyPhotoPreview(uri: Uri) {
        when (photoTarget) {
            PhotoTarget.CELL -> {
                binding.ivCellPhotoPreview.load(uri) { crossfade(true) }
                binding.ivCellPhotoPreview.visibility = View.VISIBLE
            }
            PhotoTarget.BOX -> {
                binding.ivBoxPhotoPreview.load(uri) { crossfade(true) }
                binding.ivBoxPhotoPreview.visibility = View.VISIBLE
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.keyCode == KeyEvent.KEYCODE_ENTER && keystrokeBuffer.length >= 4) {
                val barcode = keystrokeBuffer.toString()
                keystrokeBuffer.clear()
                handleScan(barcode)
                return true
            }
            val char = event.unicodeChar.toChar()
            if (char.isLetterOrDigit() || char == '-') {
                val now = System.currentTimeMillis()
                if (now - lastKeystrokeTime > 300) keystrokeBuffer.clear()
                lastKeystrokeTime = now
                keystrokeBuffer.append(char)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() { super.onResume(); DataWedgeManager.register(this) }
    override fun onPause() { super.onPause(); DataWedgeManager.unregister(this); DataWedgeManager.resetBuffer() }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finishWithSlide(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.layoutConfirm.visibility == View.VISIBLE) {
            pendingTarget = null
            pendingCellPhotoUri = null
            pendingBoxPhotoUri = null
            showContentArea()
            return
        }
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
