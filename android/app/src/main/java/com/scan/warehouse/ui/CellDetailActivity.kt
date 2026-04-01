package com.scan.warehouse.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.scan.warehouse.R
import com.scan.warehouse.databinding.ActivityCellDetailBinding
import com.scan.warehouse.databinding.DialogLevelEditBinding
import com.scan.warehouse.model.MapCell
import com.scan.warehouse.model.MapLevel
import com.scan.warehouse.model.ScanResponse
import com.scan.warehouse.repository.ProductRepository
import com.scan.warehouse.scanner.DataWedgeManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class CellDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CELL_KEY = "cell_key"
        const val EXTRA_FLOOR = "floor"
        const val EXTRA_ZONE = "zone"

        fun createIntent(context: Context, floor: Int, zone: String, cellKey: String): Intent {
            return Intent(context, CellDetailActivity::class.java).apply {
                putExtra(EXTRA_CELL_KEY, cellKey)
                putExtra(EXTRA_FLOOR, floor)
                putExtra(EXTRA_ZONE, zone)
            }
        }
    }

    private lateinit var binding: ActivityCellDetailBinding
    private lateinit var repository: ProductRepository
    private var cellKey = ""
    private var floor = 0
    private var zone = ""
    private var currentCell: MapCell? = null
    private var isEditMode = false
    private var zoneColCount = 4

    private var pendingLevelIndex = -1
    private var pendingPhotoUri: Uri? = null
    private var activeLevelDialog: AlertDialog? = null
    private var activeLevelBinding: DialogLevelEditBinding? = null
    private var uploadingLevelIndex = -1
    private var matchedSkuId: String? = null
    private var matchedPhotoUrl: String? = null
    private var scanCollectJob: Job? = null

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) doLaunchCamera()
        else Toast.makeText(this, "카메라 권한이 필요합니다", Toast.LENGTH_SHORT).show()
    }

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            val uri = pendingPhotoUri ?: return@registerForActivityResult
            activeLevelBinding?.ivLevelPhoto?.load(uri) { crossfade(true) }
            uploadLevelPhotoAndRefreshDialog(uri)
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            activeLevelBinding?.ivLevelPhoto?.load(uri) { crossfade(true) }
            uploadLevelPhotoAndRefreshDialog(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCellDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentCell = null
        isEditMode = false
        pendingLevelIndex = -1
        pendingPhotoUri = null
        uploadingLevelIndex = -1
        activeLevelDialog?.dismiss()
        activeLevelDialog = null
        activeLevelBinding = null

        cellKey = intent.getStringExtra(EXTRA_CELL_KEY) ?: run { finish(); return }
        floor = intent.getIntExtra(EXTRA_FLOOR, 5)
        zone = intent.getStringExtra(EXTRA_ZONE) ?: ""

        repository = ProductRepository(this)

        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        binding.btnDone.setOnClickListener {
            setEditMode(false)
        }

        binding.btnBarMap.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        binding.btnBarEdit.setOnClickListener {
            if (currentCell == null) {
                currentCell = MapCell(levels = DEFAULT_LEVELS)
            }
            setEditMode(!isEditMode)
        }

        binding.btnAddLevel.setOnClickListener { addLevel() }
        binding.btnRemoveLevel.setOnClickListener { confirmRemoveLevel() }

        loadCellData()
    }

    private fun setEditMode(edit: Boolean) {
        isEditMode = edit
        binding.btnDone.visibility = if (edit) View.VISIBLE else View.GONE
        binding.editModeIndicator.visibility = if (edit) View.VISIBLE else View.GONE
        binding.layoutEditActions.visibility = if (edit) View.VISIBLE else View.GONE
        binding.btnBarEdit.text = if (edit) "편집 중" else "편집"
        renderLevels(currentCell, isEditMode)
    }

    private fun loadCellData() {
        if (isFinishing || isDestroyed) return
        lifecycleScope.launch {
            try {
                repository.getMapLayout().onSuccess { layout ->
                    if (!isFinishing && !isDestroyed) {
                        val cell = layout.cells[cellKey]
                        currentCell = cell
                        layout.zones.find { it.code == zone }?.let { zoneColCount = it.cols }
                        updateTitle()
                        renderLevels(cell, isEditMode)
                    }
                }.onFailure {
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(this@CellDetailActivity, "셀 정보를 불러오지 못했습니다", Toast.LENGTH_SHORT).show()
                        renderLevels(null, isEditMode)
                    }
                }
            } catch (e: Exception) {
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this@CellDetailActivity, "데이터 로드 오류", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateTitle() {
        val parts = cellKey.split("-")
        if (parts.size >= 3) {
            val row = parts[1].toIntOrNull() ?: 1
            val col = parts[2].toIntOrNull() ?: 1
            val seqNum = (row - 1) * zoneColCount + col
            binding.tvTitle.text = "${zone}구역 ${zone}-$seqNum"
        } else {
            binding.tvTitle.text = "${zone}구역 — $cellKey"
        }
    }

    private fun renderLevels(cell: MapCell?, editMode: Boolean) {
        binding.layoutLevels.removeAllViews()
        val inflater = LayoutInflater.from(this)

        val levels = cell?.levels?.takeIf { it.isNotEmpty() } ?: DEFAULT_LEVELS

        for (i in levels.indices.reversed()) {
            val level = levels[i]
            val card = inflater.inflate(R.layout.item_level_card, binding.layoutLevels, false)

            val tvLabel = card.findViewById<TextView>(R.id.tvLevelLabel)
            val btnEdit = card.findViewById<ImageButton>(R.id.btnLevelEdit)
            val layoutPhoto = card.findViewById<FrameLayout>(R.id.layoutPhoto)
            val ivPhoto = card.findViewById<ImageView>(R.id.ivLevelPhoto)
            val layoutUploadOverlay = card.findViewById<FrameLayout>(R.id.layoutUploadOverlay)
            val progressUpload = card.findViewById<ProgressBar>(R.id.progressUpload)
            val ivUploadSuccess = card.findViewById<ImageView>(R.id.ivUploadSuccess)
            val layoutNoPhoto = card.findViewById<LinearLayout>(R.id.layoutNoPhoto)
            val tvItemLabel = card.findViewById<TextView>(R.id.tvItemLabel)
            val tvSku = card.findViewById<TextView>(R.id.tvSku)

            tvLabel.text = level.label.ifEmpty { "층 ${i + 1}" }

            val levelIndex = i

            // 편집 버튼
            if (editMode) {
                btnEdit.visibility = View.VISIBLE
                btnEdit.setOnClickListener { openLevelEditor(levelIndex, level) }
            }

            // 사진
            val photoUrl = level.photo
            if (!photoUrl.isNullOrEmpty()) {
                layoutPhoto.visibility = View.VISIBLE
                val fullUrl = if (photoUrl.startsWith("http")) photoUrl
                else repository.getImageUrl(photoUrl)
                ivPhoto.load(fullUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_placeholder)
                    error(R.drawable.ic_placeholder)
                }

                if (uploadingLevelIndex == levelIndex) {
                    layoutUploadOverlay.visibility = View.VISIBLE
                    progressUpload.visibility = View.VISIBLE
                    ivUploadSuccess.visibility = View.GONE
                }
            } else if (editMode) {
                layoutNoPhoto.visibility = View.VISIBLE
            }

            // 상품명
            if (!level.itemLabel.isNullOrEmpty()) {
                tvItemLabel.visibility = View.VISIBLE
                tvItemLabel.text = level.itemLabel
            }

            // SKU
            if (!level.sku.isNullOrEmpty()) {
                tvSku.visibility = View.VISIBLE
                tvSku.text = "SKU: ${level.sku}"
            }

            binding.layoutLevels.addView(card)
        }
    }

    private fun showUploadSuccess(levelIndex: Int) {
        val levelCount = binding.layoutLevels.childCount
        if (levelIndex >= levelCount) return
        val card = binding.layoutLevels.getChildAt(levelIndex) ?: return

        val layoutOverlay = card.findViewById<FrameLayout>(R.id.layoutUploadOverlay) ?: return
        val progress = card.findViewById<ProgressBar>(R.id.progressUpload) ?: return
        val successIcon = card.findViewById<ImageView>(R.id.ivUploadSuccess) ?: return

        progress.visibility = View.GONE
        successIcon.visibility = View.VISIBLE

        Handler(Looper.getMainLooper()).postDelayed({
            layoutOverlay.visibility = View.GONE
            successIcon.visibility = View.GONE
        }, 1500)
    }

    private fun showUploadProgress(levelIndex: Int) {
        uploadingLevelIndex = levelIndex
        val levelCount = binding.layoutLevels.childCount
        if (levelIndex >= levelCount) return
        val card = binding.layoutLevels.getChildAt(levelIndex) ?: return

        val layoutPhoto = card.findViewById<FrameLayout>(R.id.layoutPhoto) ?: return
        val layoutOverlay = card.findViewById<FrameLayout>(R.id.layoutUploadOverlay) ?: return
        val progress = card.findViewById<ProgressBar>(R.id.progressUpload) ?: return
        val successIcon = card.findViewById<ImageView>(R.id.ivUploadSuccess) ?: return

        layoutPhoto.visibility = View.VISIBLE
        layoutOverlay.visibility = View.VISIBLE
        progress.visibility = View.VISIBLE
        successIcon.visibility = View.GONE
    }

    private fun hideUploadProgress() {
        uploadingLevelIndex = -1
    }

    private fun openLevelEditor(levelIndex: Int, level: MapLevel) {
        pendingLevelIndex = levelIndex
        matchedSkuId = null
        matchedPhotoUrl = null
        val dialogBinding = DialogLevelEditBinding.inflate(layoutInflater)
        activeLevelBinding = dialogBinding

        val photoUrl = level.photo
        if (!photoUrl.isNullOrEmpty()) {
            val fullUrl = if (photoUrl.startsWith("http")) photoUrl
            else repository.getImageUrl(photoUrl)
            dialogBinding.ivLevelPhoto.load(fullUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_placeholder)
                error(R.drawable.ic_placeholder)
            }
        }

        dialogBinding.etProductName.setText(level.itemLabel ?: "")
        dialogBinding.etSku.setText(level.sku ?: "")

        dialogBinding.btnCamera.setOnClickListener { launchCamera() }
        dialogBinding.btnGallery.setOnClickListener { galleryLauncher.launch("image/*") }
        dialogBinding.btnDeletePhoto.setOnClickListener {
            confirmDeletePhoto(levelIndex)
        }
        dialogBinding.btnSearchProduct.setOnClickListener { showProductSearchDialog() }

        scanCollectJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                DataWedgeManager.scanFlow.collect { barcode ->
                    matchProductFromBarcode(barcode)
                }
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("${level.label} 편집")
            .setView(dialogBinding.root)
            .setPositiveButton("저장") { _, _ ->
                saveLevelChanges(levelIndex)
            }
            .setNegativeButton("취소", null)
            .create()

        activeLevelDialog = dialog
        dialog.setOnDismissListener {
            scanCollectJob?.cancel()
            scanCollectJob = null
            activeLevelDialog = null
            activeLevelBinding = null
        }
        dialog.show()
    }

    private fun matchProductFromBarcode(barcode: String) {
        lifecycleScope.launch {
            val (result, _) = repository.scanBarcode(barcode)
            result.onSuccess { scan -> applyMatchedProduct(scan) }
                .onFailure {
                    Toast.makeText(this@CellDetailActivity, "상품을 찾을 수 없습니다: $barcode", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun applyMatchedProduct(scan: ScanResponse) {
        val dlg = activeLevelBinding ?: return
        matchedSkuId = scan.skuId
        dlg.etProductName.setText(scan.productName)
        dlg.etSku.setText(scan.skuId)
        dlg.tvMatchedInfo.text = "✓ 매칭됨: ${scan.productName}"
        dlg.tvMatchedInfo.visibility = View.VISIBLE

        val imageUrl = scan.images.firstOrNull()?.filePath
        if (imageUrl != null) {
            matchedPhotoUrl = imageUrl
            val fullUrl = repository.getImageUrl(imageUrl)
            dlg.ivLevelPhoto.load(fullUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_placeholder)
                error(R.drawable.ic_placeholder)
            }
        }
        Toast.makeText(this, "상품 매칭 완료: ${scan.productName}", Toast.LENGTH_SHORT).show()
    }

    private fun showProductSearchDialog() {
        val input = EditText(this).apply {
            hint = "바코드, SKU, 상품명 검색"
            setPadding(48, 32, 48, 16)
            textSize = 16f
        }

        AlertDialog.Builder(this)
            .setTitle("상품 검색")
            .setView(input)
            .setPositiveButton("검색") { _, _ ->
                val query = input.text.toString().trim()
                if (query.isEmpty()) return@setPositiveButton
                lifecycleScope.launch {
                    val barcodePattern = Regex("^\\d{8,13}$")
                    if (barcodePattern.matches(query)) {
                        matchProductFromBarcode(query)
                    } else {
                        val (result, _) = repository.searchProducts(query)
                        result.onSuccess { response ->
                            if (response.items.isEmpty()) {
                                Toast.makeText(this@CellDetailActivity, "검색 결과 없음", Toast.LENGTH_SHORT).show()
                                return@onSuccess
                            }
                            val names = response.items.map { "${it.productName} (${it.skuId})" }
                            AlertDialog.Builder(this@CellDetailActivity)
                                .setTitle("상품 선택 (${response.total}건)")
                                .setItems(names.toTypedArray()) { _, which ->
                                    val selected = response.items[which]
                                    if (selected.barcode != null) {
                                        matchProductFromBarcode(selected.barcode)
                                    } else {
                                        activeLevelBinding?.etProductName?.setText(selected.productName)
                                        activeLevelBinding?.etSku?.setText(selected.skuId)
                                        matchedSkuId = selected.skuId
                                        activeLevelBinding?.tvMatchedInfo?.text = "✓ 매칭됨: ${selected.productName}"
                                        activeLevelBinding?.tvMatchedInfo?.visibility = View.VISIBLE
                                    }
                                }
                                .setNegativeButton("취소", null)
                                .show()
                        }.onFailure {
                            Toast.makeText(this@CellDetailActivity, "검색 실패", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun List<MapLevel>.toApiPayload(): List<Map<String, Any>> = map { lv ->
        mapOf(
            "index" to lv.index,
            "label" to lv.label,
            "itemLabel" to (lv.itemLabel ?: ""),
            "sku" to (lv.sku ?: ""),
            "photo" to (lv.photo ?: "")
        )
    }

    private fun saveLevels(levels: List<MapLevel>, errorMsg: String = "저장 실패") {
        lifecycleScope.launch {
            repository.updateMapCell(cellKey, mapOf("levels" to levels.toApiPayload()))
                .onSuccess { loadCellData() }
                .onFailure { Toast.makeText(this@CellDetailActivity, errorMsg, Toast.LENGTH_SHORT).show() }
        }
    }

    private fun saveLevelChanges(levelIndex: Int) {
        val binding = activeLevelBinding
        val productName = binding?.etProductName?.text?.toString()?.trim() ?: ""
        val sku = binding?.etSku?.text?.toString()?.trim() ?: ""

        if (currentCell == null) {
            currentCell = MapCell(levels = listOf(MapLevel(index = 0, label = "하단 (1층)")))
        }
        val cell = currentCell ?: return
        val levels = cell.levels?.toMutableList() ?: DEFAULT_LEVELS.toMutableList()

        if (levelIndex < levels.size) {
            val updated = levels[levelIndex].copy(
                itemLabel = productName.ifEmpty { null },
                sku = sku.ifEmpty { null },
                photo = matchedPhotoUrl ?: levels[levelIndex].photo
            )
            levels[levelIndex] = updated
        }

        saveLevels(levels)

        val skuId = matchedSkuId
        if (skuId != null) {
            val parts = cellKey.split("-")
            val shelfNum = if (parts.size >= 3) parts[2] else "01"
            val location = "${floor}층-$zone-${shelfNum.padStart(2, '0')}"
            lifecycleScope.launch {
                repository.updateProductLocation(skuId, location)
            }
        }
    }

    private fun launchCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            doLaunchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun doLaunchCamera() {
        val photoFile = File(cacheDir, "photos").also { it.mkdirs() }.let {
            File(it, "level_${cellKey}_${pendingLevelIndex}_${System.currentTimeMillis()}.jpg")
        }
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
        pendingPhotoUri = uri
        takePicture.launch(uri)
    }

    private fun uploadLevelPhotoAndRefreshDialog(uri: Uri) {
        val levelIndex = pendingLevelIndex
        if (levelIndex < 0) return
        showUploadProgress(levelIndex)
        lifecycleScope.launch {
            try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw Exception("파일을 열 수 없습니다")
                val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
                val ext = android.webkit.MimeTypeMap.getSingleton()
                    .getExtensionFromMimeType(mimeType) ?: "jpg"
                val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", "photo.$ext", requestBody)
                repository.uploadLevelPhoto(cellKey, levelIndex, part)
                    .onSuccess {
                        hideUploadProgress()
                        repository.getMapLayout().onSuccess { layout ->
                            if (!isFinishing && !isDestroyed) {
                                currentCell = layout.cells[cellKey]
                                updateTitle()
                                renderLevels(currentCell, isEditMode)
                                showUploadSuccess(levelIndex)
                            }
                        }
                        Toast.makeText(this@CellDetailActivity, "업로드 완료", Toast.LENGTH_SHORT).show()
                    }
                    .onFailure { e ->
                        hideUploadProgress()
                        renderLevels(currentCell, isEditMode)
                        Toast.makeText(this@CellDetailActivity, "업로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } catch (e: Exception) {
                hideUploadProgress()
                renderLevels(currentCell, isEditMode)
                Toast.makeText(this@CellDetailActivity, "업로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmDeletePhoto(levelIndex: Int) {
        AlertDialog.Builder(this)
            .setTitle("사진 삭제")
            .setMessage("이 사진을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ -> deleteLevelPhoto(levelIndex) }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun deleteLevelPhoto(levelIndex: Int) {
        lifecycleScope.launch {
            repository.deleteLevelPhoto(cellKey, levelIndex)
                .onSuccess {
                    activeLevelBinding?.ivLevelPhoto?.setImageResource(R.drawable.ic_placeholder)
                    loadCellData()
                    Toast.makeText(this@CellDetailActivity, "사진 삭제 완료", Toast.LENGTH_SHORT).show()
                }
                .onFailure { Toast.makeText(this@CellDetailActivity, "사진 삭제 실패", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun addLevel() {
        val cell = currentCell ?: return
        val levels = cell.levels?.toMutableList() ?: mutableListOf()
        val newIndex = levels.size
        val label = when (newIndex) {
            0 -> "하단 (1층)"
            1 -> "중단 (2층)"
            2 -> "상단 (3층)"
            else -> "${newIndex + 1}층"
        }
        levels.add(MapLevel(index = newIndex, label = label))
        saveLevels(levels, "층 추가 실패")
    }

    private fun confirmRemoveLevel() {
        val cell = currentCell ?: return
        val levels = cell.levels ?: return
        if (levels.isEmpty()) return
        val lastLevel = levels.last()
        AlertDialog.Builder(this)
            .setTitle("층 삭제")
            .setMessage("'${lastLevel.label}'을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ -> removeLevel() }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun removeLevel() {
        val cell = currentCell ?: return
        val levels = cell.levels?.toMutableList() ?: return
        if (levels.isEmpty()) return
        levels.removeAt(levels.size - 1)
        saveLevels(levels, "층 삭제 실패")
    }

    override fun onDestroy() {
        activeLevelDialog?.dismiss()
        activeLevelDialog = null
        activeLevelBinding = null
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}

private val DEFAULT_LEVELS = listOf(
    MapLevel(index = 0, label = "하단 (1층)"),
    MapLevel(index = 1, label = "중단 (2층)"),
    MapLevel(index = 2, label = "상단 (3층)")
)
