package com.scan.warehouse.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import coil.load
import com.scan.warehouse.R
import com.scan.warehouse.databinding.ActivityCellDetailBinding
import com.scan.warehouse.databinding.DialogLevelEditBinding
import com.scan.warehouse.model.MapCell
import com.scan.warehouse.model.MapLevel
import com.scan.warehouse.model.ScanResponse
import com.scan.warehouse.scanner.DataWedgeManager
import com.scan.warehouse.viewmodel.CellDetailViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

@AndroidEntryPoint
class CellDetailActivity : BaseActivity() {

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
    private val viewModel: CellDetailViewModel by viewModels()

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

        pendingLevelIndex = -1
        pendingPhotoUri = null
        uploadingLevelIndex = -1
        activeLevelDialog?.dismiss()
        activeLevelDialog = null
        activeLevelBinding = null

        binding.btnBack.setOnClickListener { finishWithSlide() }

        binding.btnDone.setOnClickListener {
            viewModel.setEditMode(false)
        }

        binding.btnBarMap.setOnClickListener { finishWithSlide() }

        binding.btnBarEdit.setOnClickListener {
            if (viewModel.cellData.value == null) {
                viewModel.saveLevels(CellDetailViewModel.DEFAULT_LEVELS)
            }
            viewModel.setEditMode(viewModel.isEditMode.value != true)
        }

        binding.btnBarPrev.setOnClickListener {
            val keys = viewModel.allCellKeys.value ?: return@setOnClickListener
            val idx = viewModel.currentCellIndex.value ?: return@setOnClickListener
            if (idx > 0) navigateToCell(keys, idx, idx - 1)
        }
        binding.btnBarNext.setOnClickListener {
            val keys = viewModel.allCellKeys.value ?: return@setOnClickListener
            val idx = viewModel.currentCellIndex.value ?: return@setOnClickListener
            if (idx < keys.size - 1) navigateToCell(keys, idx, idx + 1)
        }

        binding.btnAddLevel.setOnClickListener { viewModel.addLevel() }
        binding.btnRemoveLevel.setOnClickListener { confirmRemoveLevel() }

        observeViewModel()
        viewModel.loadCellData()
    }

    private fun observeViewModel() {
        viewModel.cellData.observe(this) { cell ->
            updateTitle()
            renderLevels(cell, viewModel.isEditMode.value == true)
        }

        viewModel.isEditMode.observe(this) { edit ->
            binding.btnDone.visibility = if (edit) View.VISIBLE else View.GONE
            binding.btnDone.setTextColor(if (edit) Color.BLACK else Color.WHITE)
            binding.headerBar.setBackgroundColor(
                ContextCompat.getColor(this, if (edit) R.color.secondary_container else R.color.primary)
            )
            binding.tvTitle.setTextColor(if (edit) Color.BLACK else Color.WHITE)
            binding.layoutEditActions.visibility = if (edit) View.VISIBLE else View.GONE
            binding.btnBarEdit.text = if (edit) "편집 중" else "편집"
            renderLevels(viewModel.cellData.value, edit)
        }

        viewModel.allCellKeys.observe(this) { updateNavButtons() }
        viewModel.currentCellIndex.observe(this) { updateNavButtons() }

        viewModel.loadError.observe(this) { error ->
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                renderLevels(null, viewModel.isEditMode.value == true)
            }
        }
    }

    private fun updateTitle() {
        val cellKey = viewModel.cellKey
        val zone = viewModel.zone
        val parts = cellKey.split("-")
        if (parts.size >= 3) {
            val row = parts[1].toIntOrNull() ?: 1
            val col = parts[2].toIntOrNull() ?: 1
            val seqNum = (row - 1) * viewModel.zoneColCount + col
            binding.tvTitle.text = "${zone}구역 ${zone}-$seqNum"
        } else {
            binding.tvTitle.text = "${zone}구역 — $cellKey"
        }
    }

    private fun renderLevels(cell: MapCell?, editMode: Boolean) {
        binding.layoutLevels.removeAllViews()
        val inflater = LayoutInflater.from(this)

        val levels = cell?.levels?.takeIf { it.isNotEmpty() } ?: CellDetailViewModel.DEFAULT_LEVELS

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
            val btnAddProduct = card.findViewById<Button>(R.id.btnAddProduct)

            tvLabel.text = level.label.ifEmpty { "층 ${i + 1}" }

            val levelIndex = i

            if (editMode) {
                btnEdit.visibility = View.VISIBLE
                btnEdit.setOnClickListener { openLevelEditor(levelIndex, level) }
                btnAddProduct.visibility = View.VISIBLE
                btnAddProduct.setOnClickListener { openLevelEditor(levelIndex, level) }
            }

            val photoUrl = level.photo
            if (!photoUrl.isNullOrEmpty()) {
                layoutPhoto.visibility = View.VISIBLE
                val fullUrl = if (photoUrl.startsWith("http")) photoUrl
                else viewModel.getImageUrl(photoUrl)
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

            if (!level.itemLabel.isNullOrEmpty()) {
                tvItemLabel.visibility = View.VISIBLE
                tvItemLabel.text = level.itemLabel
            }

            if (!level.sku.isNullOrEmpty()) {
                tvSku.visibility = View.VISIBLE
                tvSku.text = "SKU: ${level.sku}"
            }

            if (editMode && !level.itemLabel.isNullOrEmpty()) {
                card.setOnLongClickListener {
                    confirmDeleteProduct(levelIndex, level)
                    true
                }
            }

            binding.layoutLevels.addView(card)
        }
    }

    private fun levelToViewIndex(levelIndex: Int): Int {
        val levelCount = binding.layoutLevels.childCount
        return levelCount - 1 - levelIndex
    }

    private fun showUploadSuccess(levelIndex: Int) {
        val viewIndex = levelToViewIndex(levelIndex)
        val levelCount = binding.layoutLevels.childCount
        if (viewIndex < 0 || viewIndex >= levelCount) return
        val card = binding.layoutLevels.getChildAt(viewIndex) ?: return

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
        val viewIndex = levelToViewIndex(levelIndex)
        val levelCount = binding.layoutLevels.childCount
        if (viewIndex < 0 || viewIndex >= levelCount) return
        val card = binding.layoutLevels.getChildAt(viewIndex) ?: return

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
            else viewModel.getImageUrl(photoUrl)
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

        val dialog = AlertDialog.Builder(this)
            .setTitle("${level.label} 편집")
            .setView(dialogBinding.root)
            .setPositiveButton("저장") { _, _ ->
                saveLevelChanges(levelIndex)
            }
            .setNegativeButton("취소", null)
            .create()

        activeLevelDialog = dialog
        scanCollectJob?.cancel()
        scanCollectJob = lifecycleScope.launch {
            DataWedgeManager.scanFlow.collect { barcode ->
                matchProductFromBarcode(barcode)
            }
        }
        dialog.setOnDismissListener {
            scanCollectJob?.cancel()
            scanCollectJob = null
            activeLevelDialog = null
            activeLevelBinding = null
            pendingLevelIndex = -1
            pendingPhotoUri = null
        }
        dialog.show()
    }

    private fun matchProductFromBarcode(barcode: String) {
        viewModel.scanBarcode(barcode) { result ->
            result.onSuccess { scan -> applyMatchedProduct(scan) }
                .onFailure {
                    Toast.makeText(this, "상품을 찾을 수 없습니다: $barcode", Toast.LENGTH_SHORT).show()
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
            val fullUrl = viewModel.getImageUrl(imageUrl)
            dlg.ivLevelPhoto.load(fullUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_placeholder)
                error(R.drawable.ic_placeholder)
            }
        }
        Toast.makeText(this, "상품 매칭 완료: ${scan.productName}", Toast.LENGTH_SHORT).show()
    }

    private fun showProductSearchDialog() {
        val density = resources.displayMetrics.density
        val input = EditText(this).apply {
            hint = "바코드, SKU, 상품명 검색"
            setPadding((24 * density).toInt(), (16 * density).toInt(), (24 * density).toInt(), (8 * density).toInt())
            textSize = 16f
        }

        AlertDialog.Builder(this)
            .setTitle("상품 검색")
            .setView(input)
            .setPositiveButton("검색") { _, _ ->
                val query = input.text.toString().trim()
                if (query.isEmpty()) return@setPositiveButton
                val barcodePattern = Regex("^\\d{8,13}$")
                if (barcodePattern.matches(query)) {
                    matchProductFromBarcode(query)
                } else {
                    viewModel.searchProducts(query) { result ->
                        result.onSuccess { response ->
                            if (response.items.isEmpty()) {
                                Toast.makeText(this, "검색 결과 없음", Toast.LENGTH_SHORT).show()
                                return@onSuccess
                            }
                            val names = response.items.map { "${it.productName} (${it.skuId})" }
                            AlertDialog.Builder(this)
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
                            Toast.makeText(this, "검색 실패", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun saveLevelChanges(levelIndex: Int) {
        val binding = activeLevelBinding
        val productName = binding?.etProductName?.text?.toString()?.trim() ?: ""
        val sku = binding?.etSku?.text?.toString()?.trim() ?: ""
        viewModel.saveLevelChanges(levelIndex, productName, sku, matchedPhotoUrl, matchedSkuId)
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
            File(it, "level_${viewModel.cellKey}_${pendingLevelIndex}_${System.currentTimeMillis()}.jpg")
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
                val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
                val ext = android.webkit.MimeTypeMap.getSingleton()
                    .getExtensionFromMimeType(mimeType) ?: "jpg"
                val bytes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: throw Exception("파일을 열 수 없습니다")
                val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", "photo.$ext", requestBody)
                if (viewModel.cellKey.isEmpty()) return@launch
                viewModel.uploadLevelPhoto(levelIndex, part,
                    onSuccess = {
                        if (!isFinishing && !isDestroyed) {
                            hideUploadProgress()
                            updateTitle()
                            renderLevels(viewModel.cellData.value, viewModel.isEditMode.value == true)
                            showUploadSuccess(levelIndex)
                            Toast.makeText(this@CellDetailActivity, "업로드 완료", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onFailure = { msg ->
                        hideUploadProgress()
                        renderLevels(viewModel.cellData.value, viewModel.isEditMode.value == true)
                        Toast.makeText(this@CellDetailActivity, "업로드 실패: $msg", Toast.LENGTH_SHORT).show()
                    }
                )
            } catch (e: Exception) {
                hideUploadProgress()
                renderLevels(viewModel.cellData.value, viewModel.isEditMode.value == true)
                Toast.makeText(this@CellDetailActivity, "업로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmDeletePhoto(levelIndex: Int) {
        AlertDialog.Builder(this)
            .setTitle("사진 삭제")
            .setMessage("이 사진을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                viewModel.deleteLevelPhoto(levelIndex,
                    onSuccess = {
                        activeLevelBinding?.ivLevelPhoto?.setImageResource(R.drawable.ic_placeholder)
                        Toast.makeText(this, "사진 삭제 완료", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { msg ->
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                )
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun confirmDeleteProduct(levelIndex: Int, level: MapLevel) {
        AlertDialog.Builder(this)
            .setTitle("상품 삭제")
            .setMessage("'${level.itemLabel}'을(를) 이 층에서 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ -> viewModel.deleteProduct(levelIndex) }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun confirmRemoveLevel() {
        val cell = viewModel.cellData.value ?: return
        val levels = cell.levels ?: return
        if (levels.isEmpty()) return
        val lastLevel = levels.last()
        AlertDialog.Builder(this)
            .setTitle("층 삭제")
            .setMessage("'${lastLevel.label}'을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ -> viewModel.removeLevel() }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun navigateToCell(cellKeys: ArrayList<String>, currentIndex: Int, newIndex: Int) {
        val newCellKey = cellKeys[newIndex]
        val parts = newCellKey.split("-")
        val newZone = if (parts.isNotEmpty()) parts[0] else viewModel.zone
        val forward = newIndex > currentIndex
        val intent = createIntent(this, viewModel.floor, newZone, newCellKey)
        startActivity(intent)
        finish()
        overridePendingTransition(
            if (forward) R.anim.slide_in_right else R.anim.slide_in_left,
            if (forward) R.anim.slide_out_left else R.anim.slide_out_right
        )
    }

    private fun updateNavButtons() {
        val keys = viewModel.allCellKeys.value
        val idx = viewModel.currentCellIndex.value ?: -1
        val hasPrev = keys != null && idx > 0
        val hasNext = keys != null && idx < (keys.size - 1)
        binding.btnBarPrev.alpha = if (hasPrev) 1f else 0.3f
        binding.btnBarPrev.isEnabled = hasPrev
        binding.btnBarNext.alpha = if (hasNext) 1f else 0.3f
        binding.btnBarNext.isEnabled = hasNext
    }

    override fun onResume() { super.onResume(); DataWedgeManager.register(this) }
    override fun onPause() { super.onPause(); DataWedgeManager.unregister(this); DataWedgeManager.resetBuffer() }

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
