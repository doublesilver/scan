package com.scan.warehouse.ui

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import coil.load
import com.scan.warehouse.R
import com.scan.warehouse.databinding.ActivityCellDetailBinding
import com.scan.warehouse.databinding.DialogLevelEditBinding
import com.scan.warehouse.model.MapCell
import com.scan.warehouse.model.MapLevel
import com.scan.warehouse.repository.ProductRepository
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

    // Per-level editor state
    private var pendingLevelIndex = -1
    private var pendingPhotoUri: Uri? = null
    private var activeLevelDialog: AlertDialog? = null
    private var activeLevelBinding: DialogLevelEditBinding? = null

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

        // 상태 초기화
        currentCell = null
        isEditMode = false
        pendingLevelIndex = -1
        pendingPhotoUri = null
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
                // 셀 데이터가 없으면 빈 셀 초기화
                currentCell = MapCell(levels = listOf(
                    MapLevel(index = 0, label = "하단 (1층)"),
                    MapLevel(index = 1, label = "중단 (2층)"),
                    MapLevel(index = 2, label = "상단 (3층)")
                ))
            }
            setEditMode(!isEditMode)
        }

        binding.btnAddLevel.setOnClickListener { addLevel() }
        binding.btnRemoveLevel.setOnClickListener { removeLevel() }

        loadCellData()
    }

    private fun setEditMode(edit: Boolean) {
        isEditMode = edit
        binding.btnDone.visibility = if (edit) View.VISIBLE else View.GONE
        binding.layoutEditActions.visibility = if (edit) View.VISIBLE else View.GONE
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
        val cellName = if (parts.size >= 3) "${parts[1]}-${parts[2]}" else cellKey
        binding.tvTitle.text = "${floor}층 ${zone}구역 — $cellName"
    }

    private fun renderLevels(cell: MapCell?, editMode: Boolean) {
        binding.layoutLevels.removeAllViews()
        val density = resources.displayMetrics.density

        val levels = cell?.levels?.takeIf { it.isNotEmpty() }
            ?: listOf(
                MapLevel(index = 0, label = "하단 (1층)"),
                MapLevel(index = 1, label = "중단 (2층)"),
                MapLevel(index = 2, label = "상단 (3층)")
            )

        for ((i, level) in levels.withIndex()) {
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(
                    this@CellDetailActivity,
                    if (i % 2 == 0) R.drawable.bg_info_row else R.drawable.bg_info_row_alt
                )
                val pad = (16 * density).toInt()
                setPadding(pad, pad, pad, pad)
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                    bottomMargin = (2 * density).toInt()
                }
            }

            // Header row
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            }

            header.addView(TextView(this).apply {
                text = level.label.ifEmpty { "층 ${i + 1}" }
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@CellDetailActivity, R.color.primary))
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })

            if (editMode) {
                val editBtn = ImageButton(this).apply {
                    setImageDrawable(ContextCompat.getDrawable(this@CellDetailActivity, R.drawable.ic_edit))
                    background = with(obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless))) {
                        getDrawable(0).also { recycle() }
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        (40 * density).toInt(),
                        (40 * density).toInt()
                    )
                    val levelIndex = level.index.takeIf { it >= 0 }.let { levels.indexOf(level) }
                    setOnClickListener { openLevelEditor(levelIndex, level) }
                }
                header.addView(editBtn)
            }

            container.addView(header)

            // Photo
            val photoUrl = level.photo
            if (!photoUrl.isNullOrEmpty()) {
                val imageView = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                        topMargin = (8 * density).toInt()
                    }
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                val fullUrl = if (photoUrl.startsWith("http")) photoUrl
                else repository.getImageUrl(photoUrl)
                imageView.load(fullUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_placeholder)
                    error(R.drawable.ic_placeholder)
                }
                container.addView(imageView)
            } else if (editMode) {
                container.addView(TextView(this).apply {
                    text = "사진 없음"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(ContextCompat.getColor(this@CellDetailActivity, R.color.on_surface_variant))
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                        topMargin = (8 * density).toInt()
                    }
                })
            }

            // Product name
            if (!level.itemLabel.isNullOrEmpty()) {
                container.addView(TextView(this).apply {
                    text = level.itemLabel
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTextColor(ContextCompat.getColor(this@CellDetailActivity, R.color.on_surface))
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                        topMargin = (8 * density).toInt()
                    }
                })
            }

            // SKU
            if (!level.sku.isNullOrEmpty()) {
                container.addView(TextView(this).apply {
                    text = "SKU: ${level.sku}"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(ContextCompat.getColor(this@CellDetailActivity, R.color.on_surface_variant))
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                        topMargin = (2 * density).toInt()
                    }
                })
            }

            binding.layoutLevels.addView(container)
        }
    }

    private fun openLevelEditor(levelIndex: Int, level: MapLevel) {
        pendingLevelIndex = levelIndex
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
            deleteLevelPhoto(levelIndex)
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
            activeLevelDialog = null
            activeLevelBinding = null
        }
        dialog.show()
    }

    private fun saveLevelChanges(levelIndex: Int) {
        val binding = activeLevelBinding
        val productName = binding?.etProductName?.text?.toString()?.trim() ?: ""
        val sku = binding?.etSku?.text?.toString()?.trim() ?: ""

        if (currentCell == null) {
            currentCell = MapCell(levels = listOf(MapLevel(index = 0, label = "하단 (1층)")))
        }
        val cell = currentCell ?: return
        val levels = cell.levels?.toMutableList() ?: mutableListOf(
            MapLevel(index = 0, label = "하단 (1층)"),
            MapLevel(index = 1, label = "중단 (2층)"),
            MapLevel(index = 2, label = "상단 (3층)")
        )

        if (levelIndex < levels.size) {
            val updated = levels[levelIndex].copy(
                itemLabel = productName.ifEmpty { null },
                sku = sku.ifEmpty { null }
            )
            levels[levelIndex] = updated
        }

        val levelsData = levels.map { lv ->
            mapOf(
                "index" to lv.index,
                "label" to lv.label,
                "itemLabel" to (lv.itemLabel ?: ""),
                "sku" to (lv.sku ?: ""),
                "photo" to (lv.photo ?: "")
            )
        }

        lifecycleScope.launch {
            repository.updateMapCell(cellKey, mapOf("levels" to levelsData))
                .onSuccess { loadCellData() }
                .onFailure { Toast.makeText(this@CellDetailActivity, "저장 실패", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun launchCamera() {
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
        Toast.makeText(this, "업로드 중...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val stream = contentResolver.openInputStream(uri)
                    ?: throw Exception("파일을 열 수 없습니다")
                val bytes = stream.readBytes()
                stream.close()
                val requestBody = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", "photo.jpg", requestBody)
                repository.uploadLevelPhoto(cellKey, levelIndex, part)
                    .onSuccess { loadCellData() }
                    .onFailure { e ->
                        Toast.makeText(this@CellDetailActivity, "업로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } catch (e: Exception) {
                Toast.makeText(this@CellDetailActivity, "업로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteLevelPhoto(levelIndex: Int) {
        lifecycleScope.launch {
            repository.deleteLevelPhoto(cellKey, levelIndex)
                .onSuccess {
                    activeLevelBinding?.ivLevelPhoto?.setImageResource(R.drawable.ic_placeholder)
                    loadCellData()
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

        val levelsData = levels.map { lv ->
            mapOf(
                "index" to lv.index,
                "label" to lv.label,
                "itemLabel" to (lv.itemLabel ?: ""),
                "sku" to (lv.sku ?: ""),
                "photo" to (lv.photo ?: "")
            )
        }

        lifecycleScope.launch {
            repository.updateMapCell(cellKey, mapOf("levels" to levelsData))
                .onSuccess { loadCellData() }
                .onFailure { Toast.makeText(this@CellDetailActivity, "층 추가 실패", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun removeLevel() {
        val cell = currentCell ?: return
        val levels = cell.levels?.toMutableList() ?: return
        if (levels.isEmpty()) return
        levels.removeAt(levels.size - 1)

        val levelsData = levels.map { lv ->
            mapOf(
                "index" to lv.index,
                "label" to lv.label,
                "itemLabel" to (lv.itemLabel ?: ""),
                "sku" to (lv.sku ?: ""),
                "photo" to (lv.photo ?: "")
            )
        }

        lifecycleScope.launch {
            repository.updateMapCell(cellKey, mapOf("levels" to levelsData))
                .onSuccess { loadCellData() }
                .onFailure { Toast.makeText(this@CellDetailActivity, "층 삭제 실패", Toast.LENGTH_SHORT).show() }
        }
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
