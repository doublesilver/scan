package com.scan.warehouse.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import coil.load
import com.scan.warehouse.R
import com.scan.warehouse.databinding.ActivityBoxDetailBinding
import com.scan.warehouse.model.BoxResponse
import com.scan.warehouse.model.MapLayout
import com.scan.warehouse.model.ParsedLocation
import com.scan.warehouse.repository.ProductRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class BoxDetailActivity : BaseActivity() {

    companion object {
        const val EXTRA_BOX_DATA = "extra_box_data"

        fun createIntent(context: Context, box: BoxResponse): Intent {
            return Intent(context, BoxDetailActivity::class.java).apply {
                putExtra(EXTRA_BOX_DATA, box)
            }
        }
    }

    private lateinit var binding: ActivityBoxDetailBinding
    @Inject lateinit var repository: ProductRepository
    private var mapLayout: MapLayout? = null
    private var currentLocation: String? = null
    private val animators = mutableListOf<ObjectAnimator>()
    private var currentZoomDialog: Dialog? = null
    private var pendingImageType: String? = null
    private var cameraUri: Uri? = null
    private var currentBox: BoxResponse? = null

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else Toast.makeText(this, "카메라 권한이 필요합니다", Toast.LENGTH_SHORT).show()
    }

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) cameraUri?.let { uploadImage(it) }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { uploadImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBoxDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val box = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_BOX_DATA, BoxResponse::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_BOX_DATA)
        } ?: run {
            Toast.makeText(this, "박스 정보 오류", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        currentBox = box

        binding.btnBack.setOnClickListener { finishWithSlide() }

        binding.tvProductMasterName.text = box.productMasterName
        currentLocation = box.location
        setupLocationTags(box)
        setupLinkButtons(box)
        setupBottomBar(box)
        setupImageZoom()

        setupOptionImages(box)
        setupSourcingImages(box)

        loadMapAndPhotos(box)
    }

    private fun setupImageZoom() {
        binding.ivZonePhoto.setOnClickListener { showZoomDialog(binding.ivZonePhoto) }
        binding.ivShelfPhoto.setOnClickListener { showZoomDialog(binding.ivShelfPhoto) }
    }

    private fun showZoomDialog(sourceImage: ImageView) {
        currentZoomDialog?.dismiss()
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val iv = ImageView(this).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
            setImageDrawable(sourceImage.drawable)
            setOnClickListener { dialog.dismiss() }
        }
        dialog.setContentView(iv)
        dialog.setOnDismissListener { if (currentZoomDialog == dialog) currentZoomDialog = null }
        currentZoomDialog = dialog
        dialog.show()
    }

    private fun setupLocationTags(box: BoxResponse) {
        val loc = box.location ?: return
        val parsed = ParsedLocation.parse(loc)

        if (parsed.zone.isNotEmpty() && parsed.shelf.isNotEmpty()) {
            binding.tvZoneTag.text = "${parsed.zone}구역 ${parsed.zone}-${parsed.shelf}"
        } else if (parsed.zone.isNotEmpty()) {
            binding.tvZoneTag.text = "${parsed.zone}구역"
        }
        if (parsed.shelf.isNotEmpty()) {
            binding.tvShelfTag.text = "${parsed.shelf}번 선반"
        }
    }

    private fun loadMapAndPhotos(box: BoxResponse) {
        lifecycleScope.launch {
            repository.getMapLayout().onSuccess { layout ->
                mapLayout = layout
                renderInlineMap(layout, box.location)
                loadCellPhotos(layout, box.location)
            }.onFailure {
                binding.layoutInlineMap.addView(TextView(this@BoxDetailActivity).apply {
                    text = "도면 로드 실패"
                    setTextColor(ContextCompat.getColor(this@BoxDetailActivity, R.color.on_surface_variant))
                    gravity = Gravity.CENTER
                })
            }
        }
    }

    private fun renderInlineMap(layout: MapLayout, location: String?) {
        animators.forEach { it.cancel() }
        animators.clear()
        binding.layoutInlineMap.removeAllViews()
        binding.layoutInlineMap.orientation = LinearLayout.VERTICAL
        val density = resources.displayMetrics.density
        val parsed = ParsedLocation.parse(location)
        val zones = layout.zones.ifEmpty { return }

        val locationZone = zones.find { it.code == parsed.zone }
        val zoneHeader = locationZone?.name ?: "${parsed.zone}구역"

        // 위치 텍스트 (큰 글씨)
        binding.layoutInlineMap.addView(TextView(this).apply {
            text = "📍 $zoneHeader · ${parsed.zone}-${parsed.shelf}"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@BoxDetailActivity, R.color.on_surface))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (6 * density).toInt())
        })

        // 해당 zone만 그리드로 표시
        if (locationZone != null) {
            val expectedLabel = if (parsed.zone.isNotEmpty() && parsed.shelf.isNotEmpty()) {
                "${parsed.zone}-${parsed.shelf}"
            } else null
            val grid = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
            }
            for (r in 1..locationZone.rows) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
                }
                for (c in 1..locationZone.cols) {
                    val cellData = layout.cells["${locationZone.code}-$r-$c"]
                    val cellLabel = cellData?.label.orEmpty()
                    val cellStatus = cellData?.status ?: "empty"
                    val isHighlight = expectedLabel != null && cellLabel == expectedLabel
                    val cellView = TextView(this).apply {
                        text = cellLabel
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                        gravity = Gravity.CENTER
                        setTextColor(Color.WHITE)
                        when {
                            isHighlight -> {
                                val gd = GradientDrawable().apply {
                                    setColor(ContextCompat.getColor(this@BoxDetailActivity, R.color.cell_highlight))
                                    setStroke((2 * density).toInt(), ContextCompat.getColor(this@BoxDetailActivity, R.color.cell_highlight_stroke))
                                    cornerRadius = 4 * density
                                }
                                background = gd
                                setTextColor(Color.BLACK)
                                setTypeface(null, Typeface.BOLD)
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                            }
                            cellStatus == "aisle" -> {
                                setBackgroundColor(Color.parseColor("#1a1a1a"))
                                setTextColor(Color.parseColor("#666666"))
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 7f)
                            }
                            cellStatus == "table" -> {
                                setBackgroundColor(Color.parseColor("#5a3e28"))
                                setTextColor(Color.WHITE)
                                setTypeface(null, Typeface.BOLD)
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
                            }
                            cellStatus == "pc" -> {
                                setBackgroundColor(Color.parseColor("#2e4a6b"))
                                setTextColor(Color.WHITE)
                                setTypeface(null, Typeface.BOLD)
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
                            }
                            cellStatus == "used" -> {
                                setBackgroundColor(ContextCompat.getColor(this@BoxDetailActivity, R.color.cell_used))
                            }
                            else -> {
                                // 빈 공간: 투명 배경 + 텍스트 없음
                                setBackgroundColor(Color.TRANSPARENT)
                                text = ""
                            }
                        }
                        val margin = (1 * density).toInt()
                        layoutParams = LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply {
                            setMargins(margin, margin, margin, margin)
                        }
                    }
                    if (isHighlight) {
                        ObjectAnimator.ofFloat(cellView, "alpha", 1f, 0.3f).apply {
                            duration = 600
                            repeatCount = ValueAnimator.INFINITE
                            repeatMode = ValueAnimator.REVERSE
                            start()
                            animators.add(this)
                        }
                    }
                    row.addView(cellView)
                }
                grid.addView(row)
            }
            binding.layoutInlineMap.addView(grid)
        }

        // 전체 도면 안내
        binding.layoutInlineMap.addView(TextView(this).apply {
            text = "터치 → 전체 도면"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            setTextColor(ContextCompat.getColor(this@BoxDetailActivity, R.color.on_surface_variant))
            gravity = Gravity.CENTER
            setPadding(0, (4 * density).toInt(), 0, 0)
        })

        binding.blockMap.setOnClickListener {
            mapLayout?.let { ml -> showMapZoomDialog(ml, location) }
        }
    }

    private fun loadCellPhotos(layout: MapLayout, location: String?) {
        val parsed = ParsedLocation.parse(location)
        if (parsed.zone.isEmpty() || parsed.shelf.isEmpty()) return
        val zone = layout.zones.find { it.code == parsed.zone } ?: return
        val shelfNum = parsed.shelf.toIntOrNull() ?: return
        val r = (shelfNum - 1) / zone.cols + 1
        val c = (shelfNum - 1) % zone.cols + 1
        val cell = layout.cells["${zone.code}-$r-$c"] ?: return
        val photo = cell.levels?.firstOrNull { !it.photo.isNullOrEmpty() }?.photo ?: return
        binding.ivShelfPhoto.load(repository.getImageUrl(photo)) {
            crossfade(true)
            placeholder(R.drawable.ic_placeholder)
            error(R.drawable.ic_placeholder)
        }
    }

    private fun setupOptionImages(box: BoxResponse) {
        binding.layoutOptionImages.removeAllViews()
        val density = resources.displayMetrics.density

        box.productMasterImage?.let { url ->
            addImageView(binding.layoutOptionImages, url, density)
        }

        for (img in box.optionImages) {
            addImageView(binding.layoutOptionImages, img.filePath, density, img.id, box.productMasterId)
        }

        if (box.productMasterId != null) {
            addPlaceholder(binding.layoutOptionImages, "옵션 이미지 추가", "option")
        }
    }

    private fun setupSourcingImages(box: BoxResponse) {
        binding.layoutSourcingImages.removeAllViews()
        val density = resources.displayMetrics.density

        if (box.sourcingImages.isEmpty() && box.productMasterId == null) {
            binding.tvSourcingPlaceholder.visibility = View.VISIBLE
            return
        }
        binding.tvSourcingPlaceholder.visibility = View.GONE

        for (img in box.sourcingImages) {
            addImageView(binding.layoutSourcingImages, img.filePath, density, img.id, box.productMasterId)
        }

        if (box.productMasterId != null) {
            addPlaceholder(binding.layoutSourcingImages, "발주 이미지 추가", "sourcing")
        }
    }

    private fun addImageView(
        container: LinearLayout, url: String, density: Float,
        imageId: Int? = null, masterId: Int? = null
    ) {
        val iv = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = (3 * density).toInt()
            }
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        iv.load(if (url.startsWith("http")) url else repository.getImageUrl(url)) {
            crossfade(true)
            placeholder(R.drawable.ic_placeholder)
            error(R.drawable.ic_placeholder)
        }
        iv.setOnClickListener { showZoomDialog(iv) }
        if (imageId != null && masterId != null) {
            iv.setOnLongClickListener {
                AlertDialog.Builder(this)
                    .setTitle("이미지 삭제")
                    .setMessage("이 이미지를 삭제하시겠습니까?")
                    .setPositiveButton("삭제") { _, _ -> deleteImage(masterId, imageId) }
                    .setNegativeButton("취소", null)
                    .show()
                true
            }
        }
        container.addView(iv)
    }

    private fun addPlaceholder(container: LinearLayout, text: String, imageType: String) {
        val density = resources.displayMetrics.density
        val tv = TextView(this).apply {
            this.text = "+ $text"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(ContextCompat.getColor(this@BoxDetailActivity, R.color.primary))
            gravity = Gravity.CENTER
            setPadding(0, (12 * density).toInt(), 0, (12 * density).toInt())
            background = ContextCompat.getDrawable(this@BoxDetailActivity, R.drawable.bg_block_border)
            isClickable = true
            isFocusable = true
        }
        tv.setOnClickListener { showImagePickerDialog(imageType) }
        container.addView(tv)
    }

    private fun showImagePickerDialog(imageType: String) {
        pendingImageType = imageType
        AlertDialog.Builder(this)
            .setTitle("이미지 추가")
            .setItems(arrayOf("카메라", "갤러리")) { _, which ->
                when (which) {
                    0 -> {
                        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            launchCamera()
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                    1 -> galleryLauncher.launch("image/*")
                }
            }
            .show()
    }

    private fun launchCamera() {
        val photoFile = File(cacheDir, "photos").apply { mkdirs() }
            .let { File(it, "master_${System.currentTimeMillis()}.jpg") }
        cameraUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
        takePicture.launch(cameraUri!!)
    }

    private fun uploadImage(uri: Uri) {
        val masterId = currentBox?.productMasterId ?: return
        val imageType = pendingImageType ?: return

        lifecycleScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.readBytes()
                } ?: return@launch

                val body = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                val filePart = MultipartBody.Part.createFormData("file", "photo.jpg", body)
                val typePart = imageType.toRequestBody("text/plain".toMediaTypeOrNull())

                val result = repository.uploadProductMasterImage(masterId, filePart, typePart)
                result.onSuccess {
                    Toast.makeText(this@BoxDetailActivity, "업로드 완료", Toast.LENGTH_SHORT).show()
                    refreshBox()
                }.onFailure {
                    Toast.makeText(this@BoxDetailActivity, "업로드 실패", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@BoxDetailActivity, "업로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteImage(masterId: Int, imageId: Int) {
        lifecycleScope.launch {
            try {
                val result = repository.deleteProductMasterImage(masterId, imageId)
                result.onSuccess {
                    Toast.makeText(this@BoxDetailActivity, "삭제 완료", Toast.LENGTH_SHORT).show()
                    refreshBox()
                }.onFailure {
                    Toast.makeText(this@BoxDetailActivity, "삭제 실패", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@BoxDetailActivity, "삭제 실패", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshBox() {
        val qrCode = currentBox?.qrCode ?: return
        lifecycleScope.launch {
            repository.scanBox(qrCode).onSuccess { box ->
                currentBox = box
                setupOptionImages(box)
                setupSourcingImages(box)
            }
        }
    }

    private fun setupLinkButtons(box: BoxResponse) {
        setupLinkButton(binding.btnNaver, box.naverUrl)
        setupLinkButton(binding.btnCoupang, box.coupangUrl)
        setupLinkButton(binding.btn1688, box.url1688)
        setupLinkButton(binding.btnFlow, box.flowUrl)
    }

    private fun setupLinkButton(button: View, url: String?) {
        if (url.isNullOrEmpty()) {
            button.alpha = 0.3f
            button.isClickable = false
        } else {
            button.setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {
                    Toast.makeText(this, "링크를 열 수 없습니다", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupBottomBar(box: BoxResponse) {
        binding.btnBarMap.setOnClickListener {
            lifecycleScope.launch {
                val layout = mapLayout ?: repository.getMapLayout().getOrNull()
                if (layout != null) {
                    WarehouseMapDialog.show(this@BoxDetailActivity, box.location, layout) { floor, zoneCode, _, _, cellKey ->
                        startWithSlide(CellDetailActivity.createIntent(this@BoxDetailActivity, floor, zoneCode, cellKey))
                    }
                }
            }
        }

        binding.btnBarBuy.setOnClickListener {
            val url = box.coupangUrl
            if (!url.isNullOrEmpty()) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {
                    Toast.makeText(this, "링크를 열 수 없습니다", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "구매 링크가 없습니다", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnBarPrint.visibility = View.GONE
    }

    private fun showMapZoomDialog(layout: MapLayout, location: String?) {
        val parsed = ParsedLocation.parse(location)
        val density = resources.displayMetrics.density
        val dialogAnimators = mutableListOf<ObjectAnimator>()
        var zoomDialog: Dialog? = null

        val scroll = android.widget.ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * density).toInt()
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#1a1a2e"))
        }

        for (zone in layout.zones) {
            container.addView(TextView(this).apply {
                text = zone.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                setPadding(0, (8 * density).toInt(), 0, (6 * density).toInt())
            })

            val grid = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                    bottomMargin = (12 * density).toInt()
                }
            }

            val expectedLabel = if (parsed.zone.isNotEmpty() && parsed.shelf.isNotEmpty()) {
                "${parsed.zone}-${parsed.shelf}"
            } else null

            for (r in 1..zone.rows) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                }
                for (c in 1..zone.cols) {
                    val cellKey = "${zone.code}-$r-$c"
                    val cell = layout.cells[cellKey]
                    val cellLabel = cell?.label.orEmpty()
                    val cellStatus = cell?.status ?: "empty"
                    val isHighlight = expectedLabel != null && zone.code == parsed.zone && cellLabel == expectedLabel

                    val cellView = TextView(this).apply {
                        text = cellLabel
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        gravity = Gravity.CENTER
                        setTextColor(Color.WHITE)
                        when {
                            isHighlight -> {
                                val gd = GradientDrawable().apply {
                                    setColor(ContextCompat.getColor(this@BoxDetailActivity, R.color.cell_highlight))
                                    setStroke((3 * density).toInt(), ContextCompat.getColor(this@BoxDetailActivity, R.color.cell_highlight_stroke))
                                    cornerRadius = 6 * density
                                }
                                background = gd
                                setTextColor(Color.BLACK)
                                setTypeface(null, Typeface.BOLD)
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                            }
                            cellStatus == "aisle" -> {
                                setBackgroundColor(Color.parseColor("#1a1a1a"))
                                setTextColor(Color.parseColor("#666666"))
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                            }
                            cellStatus == "table" -> {
                                setBackgroundColor(Color.parseColor("#5a3e28"))
                                setTextColor(Color.WHITE)
                                setTypeface(null, Typeface.BOLD)
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                            }
                            cellStatus == "pc" -> {
                                setBackgroundColor(Color.parseColor("#2e4a6b"))
                                setTextColor(Color.WHITE)
                                setTypeface(null, Typeface.BOLD)
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                            }
                            cellStatus == "used" -> {
                                setBackgroundColor(ContextCompat.getColor(this@BoxDetailActivity, R.color.cell_used))
                            }
                            else -> {
                                // 빈 슬롯 — 렌더링은 하되 투명
                                setBackgroundColor(Color.TRANSPARENT)
                                text = ""
                            }
                        }
                        val size = (48 * density).toInt()
                        val margin = (2 * density).toInt()
                        layoutParams = LinearLayout.LayoutParams(0, size, 1f).apply {
                            setMargins(margin, margin, margin, margin)
                        }
                        // 실제 선반만 클릭 가능
                        if (cellStatus == "used") {
                            isClickable = true
                            isFocusable = true
                            foreground = with(obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))) {
                                getDrawable(0).also { recycle() }
                            }
                            setOnClickListener {
                                zoomDialog?.dismiss()
                                startWithSlide(CellDetailActivity.createIntent(
                                    this@BoxDetailActivity, layout.floor, zone.code, cellKey
                                ))
                            }
                        }
                    }
                    if (isHighlight) {
                        ObjectAnimator.ofFloat(cellView, "alpha", 1f, 0.3f).apply {
                            duration = 600
                            repeatCount = ValueAnimator.INFINITE
                            repeatMode = ValueAnimator.REVERSE
                            start()
                            dialogAnimators.add(this)
                        }
                    }
                    row.addView(cellView)
                }
                grid.addView(row)
            }
            container.addView(grid)
        }

        if (location != null) {
            container.addView(TextView(this).apply {
                text = "현재 위치: $location"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@BoxDetailActivity, R.color.cell_highlight))
                gravity = Gravity.CENTER
                setPadding(0, (8 * density).toInt(), 0, 0)
            })
        }

        scroll.addView(container)

        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen).also { zoomDialog = it }
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(scroll)
        dialog.setOnDismissListener {
            dialogAnimators.forEach { it.cancel() }
            dialogAnimators.clear()
        }
        scroll.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    override fun onDestroy() {
        animators.forEach { it.cancel() }
        animators.clear()
        currentZoomDialog?.dismiss()
        currentZoomDialog = null
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
