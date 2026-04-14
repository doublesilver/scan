package com.scan.warehouse.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import coil.load
import com.scan.warehouse.R
import com.scan.warehouse.databinding.ActivityCellDetailBinding
import com.scan.warehouse.model.MapLayout
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
    @Inject lateinit var repository: ProductRepository

    private val cellKey by lazy { intent.getStringExtra(EXTRA_CELL_KEY) ?: "" }
    private val floor by lazy { intent.getIntExtra(EXTRA_FLOOR, 5) }
    private val zone by lazy { intent.getStringExtra(EXTRA_ZONE) ?: "" }

    private var mapLayout: MapLayout? = null
    private var cameraUri: Uri? = null

    private var scaleFactor = 1.0f
    private lateinit var scaleDetector: ScaleGestureDetector

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) doLaunchCamera() else Toast.makeText(this, "카메라 권한이 필요합니다", Toast.LENGTH_SHORT).show()
    }

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) cameraUri?.let { uploadPhoto(it) }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { uploadPhoto(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCellDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finishWithSlide() }

        binding.btnBarMap.setOnClickListener {
            lifecycleScope.launch {
                val layout = mapLayout ?: loadLayout()
                WarehouseMapDialog.show(
                    context = this@CellDetailActivity,
                    location = cellKey,
                    mapLayout = layout,
                    repository = repository
                )
            }
        }

        binding.btnBarCamera.setOnClickListener { launchCamera() }

        binding.btnBarEdit.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("사진 변경")
                .setMessage("새 사진을 업로드하시겠습니까?")
                .setPositiveButton("확인") { _, _ -> showImagePickerDialog() }
                .setNegativeButton("취소", null)
                .show()
        }

        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(0.5f, 5.0f)
                binding.ivCellPhoto.scaleX = scaleFactor
                binding.ivCellPhoto.scaleY = scaleFactor
                return true
            }
        })

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                scaleFactor = 1.0f
                binding.ivCellPhoto.scaleX = 1.0f
                binding.ivCellPhoto.scaleY = 1.0f
                return true
            }
        })

        binding.ivCellPhoto.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            true
        }

        binding.layoutNoPhoto.setOnClickListener { launchCamera() }

        loadCellData()
    }

    private suspend fun loadLayout(): MapLayout? {
        val result = repository.getMapLayout()
        return result.getOrNull()?.also { mapLayout = it }
    }

    private fun loadCellData() {
        lifecycleScope.launch {
            val result = repository.getMapLayout()
            result.onSuccess { layout ->
                mapLayout = layout
                val cell = layout.cells[cellKey]
                val serverLabel = cell?.label.orEmpty()
                val displayLabel = if (serverLabel.isNotEmpty()) "${zone}구역 $serverLabel" else "${zone}구역 $cellKey"
                binding.tvCellLabel.text = displayLabel

                val photoPath = cell?.levels?.firstOrNull { !it.photo.isNullOrEmpty() }?.photo
                if (!photoPath.isNullOrEmpty()) {
                    val fullUrl = if (photoPath.startsWith("http")) photoPath
                    else repository.getImageUrl(photoPath)
                    binding.ivCellPhoto.load(fullUrl) {
                        crossfade(true)
                        placeholder(R.drawable.ic_placeholder)
                        error(R.drawable.ic_placeholder)
                    }
                    binding.layoutNoPhoto.visibility = View.GONE
                } else {
                    binding.ivCellPhoto.setImageResource(R.drawable.ic_placeholder)
                    binding.layoutNoPhoto.visibility = View.VISIBLE
                }
            }.onFailure {
                binding.tvCellLabel.text = "${zone}구역 $cellKey"
                binding.layoutNoPhoto.visibility = View.VISIBLE
            }
        }
    }

    private fun showImagePickerDialog() {
        AlertDialog.Builder(this)
            .setTitle("사진 업로드")
            .setItems(arrayOf("카메라", "갤러리")) { _, which ->
                if (which == 0) launchCamera() else galleryLauncher.launch("image/*")
            }
            .show()
    }

    private fun launchCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            doLaunchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun doLaunchCamera() {
        val photoFile = File(cacheDir, "photos").apply { mkdirs() }
            .let { File(it, "cell_${cellKey}_${System.currentTimeMillis()}.jpg") }
        cameraUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
        takePicture.launch(cameraUri!!)
    }

    private fun uploadPhoto(uri: Uri) {
        lifecycleScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.readBytes()
                } ?: return@launch
                val body = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                val filePart = MultipartBody.Part.createFormData("file", "photo.jpg", body)
                val result = repository.uploadCellPhoto(cellKey, filePart)
                result.onSuccess {
                    Toast.makeText(this@CellDetailActivity, "업로드 완료", Toast.LENGTH_SHORT).show()
                    loadCellData()
                }.onFailure {
                    Toast.makeText(this@CellDetailActivity, "업로드 실패", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@CellDetailActivity, "업로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEditLabelDialog() {
        val cell = mapLayout?.cells?.get(cellKey)
        val currentLabel = cell?.label.orEmpty()
        val input = EditText(this).apply {
            setText(currentLabel)
            hint = "셀 라벨 입력"
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            textSize = 16f
        }
        AlertDialog.Builder(this)
            .setTitle("셀 라벨 편집")
            .setView(input)
            .setPositiveButton("저장") { _, _ ->
                val newLabel = input.text.toString().trim()
                if (newLabel.isEmpty()) return@setPositiveButton
                lifecycleScope.launch {
                    val result = repository.updateMapCell(cellKey, mapOf("label" to newLabel))
                    result.onSuccess {
                        Toast.makeText(this@CellDetailActivity, "저장 완료", Toast.LENGTH_SHORT).show()
                        loadCellData()
                    }.onFailure {
                        Toast.makeText(this@CellDetailActivity, "저장 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
