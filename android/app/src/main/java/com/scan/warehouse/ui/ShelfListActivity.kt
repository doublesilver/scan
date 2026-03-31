package com.scan.warehouse.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.scan.warehouse.R
import com.scan.warehouse.databinding.ActivityShelfListBinding
import com.scan.warehouse.model.ShelfItem
import com.scan.warehouse.repository.ProductRepository
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class ShelfListActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_FLOOR = "extra_floor"
        private const val EXTRA_ZONE = "extra_zone"
        private const val EXTRA_CURRENT_LOCATION = "extra_current_location"

        fun createIntent(context: Context, floor: Int, zone: String, currentLocation: String?): Intent {
            return Intent(context, ShelfListActivity::class.java).apply {
                putExtra(EXTRA_FLOOR, floor)
                putExtra(EXTRA_ZONE, zone)
                putExtra(EXTRA_CURRENT_LOCATION, currentLocation)
            }
        }

        fun parseShelfNumber(location: String?): Int? {
            if (location == null) return null
            val parts = location.replace("층", "").split("-")
            return parts.getOrNull(2)?.toIntOrNull()
        }
    }

    private lateinit var binding: ActivityShelfListBinding
    private lateinit var adapter: ShelfAdapter
    private lateinit var repository: ProductRepository
    private var floor = 0
    private var zone = ""
    private var currentShelfNumber: Int? = null
    private var pendingUploadShelfId: Int? = null
    private var photoUri: Uri? = null

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            val shelfId = pendingUploadShelfId ?: return@registerForActivityResult
            val uri = photoUri ?: return@registerForActivityResult
            uploadPhoto(shelfId, uri)
        }
        pendingUploadShelfId = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShelfListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        floor = intent.getIntExtra(EXTRA_FLOOR, 5)
        zone = intent.getStringExtra(EXTRA_ZONE) ?: ""
        val currentLocation = intent.getStringExtra(EXTRA_CURRENT_LOCATION)
        currentShelfNumber = parseShelfNumber(currentLocation)

        repository = ProductRepository(this)

        binding.tvZoneTitle.text = "${floor}층 ${zone}구역"

        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        adapter = ShelfAdapter(
            currentShelfNumber = currentShelfNumber,
            onEditLabel = { shelf -> showEditLabelDialog(shelf) },
            onCamera = { shelf -> launchCamera(shelf) },
            onDeletePhoto = { shelf -> showDeletePhotoDialog(shelf) }
        )

        binding.rvShelves.layoutManager = LinearLayoutManager(this)
        binding.rvShelves.adapter = adapter

        loadShelves()
    }

    private fun loadShelves() {
        lifecycleScope.launch {
            val result = repository.getShelves(floor, zone)
            result.onSuccess { response ->
                adapter.submitList(response.shelves)
                binding.tvShelfCount.text = "선반 ${response.shelves.size}개"
            }.onFailure {
                Toast.makeText(this@ShelfListActivity, "선반 정보를 불러오지 못했습니다", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEditLabelDialog(shelf: ShelfItem) {
        val density = resources.displayMetrics.density
        val pad = (20 * density).toInt()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
        }

        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "라벨 입력"
            setText(shelf.label ?: "")
            selectAll()
            textSize = 16f
        }
        layout.addView(input)

        AlertDialog.Builder(this)
            .setTitle("라벨 편집")
            .setView(layout)
            .setPositiveButton("저장") { _, _ ->
                val newLabel = input.text.toString().trim()
                if (newLabel.isNotEmpty()) {
                    updateLabel(shelf.id, newLabel)
                }
            }
            .setNeutralButton("라벨 삭제") { _, _ ->
                showDeleteLabelConfirm(shelf)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showDeleteLabelConfirm(shelf: ShelfItem) {
        AlertDialog.Builder(this)
            .setTitle("라벨 삭제")
            .setMessage("선반 ${shelf.shelfNumber}의 라벨을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ -> deleteLabel(shelf.id) }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun updateLabel(shelfId: Int, label: String) {
        lifecycleScope.launch {
            repository.updateShelfLabel(shelfId, label)
                .onSuccess { loadShelves() }
                .onFailure { Toast.makeText(this@ShelfListActivity, "라벨 저장 실패", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun deleteLabel(shelfId: Int) {
        lifecycleScope.launch {
            repository.deleteShelfLabel(shelfId)
                .onSuccess { loadShelves() }
                .onFailure { Toast.makeText(this@ShelfListActivity, "라벨 삭제 실패", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun launchCamera(shelf: ShelfItem) {
        val photoFile = File(cacheDir, "photos").also { it.mkdirs() }.let {
            File(it, "shelf_${shelf.id}_${System.currentTimeMillis()}.jpg")
        }
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
        photoUri = uri
        pendingUploadShelfId = shelf.id
        takePicture.launch(uri)
    }

    private fun uploadPhoto(shelfId: Int, uri: Uri) {
        Toast.makeText(this, "업로드 중...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val stream = contentResolver.openInputStream(uri)
                    ?: throw Exception("파일을 열 수 없습니다")
                val bytes = stream.readBytes()
                stream.close()

                val requestBody = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", "photo.jpg", requestBody)

                repository.uploadShelfPhoto(shelfId, part)
                    .onSuccess { loadShelves() }
                    .onFailure { e ->
                        Toast.makeText(this@ShelfListActivity, "업로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } catch (e: Exception) {
                Toast.makeText(this@ShelfListActivity, "업로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDeletePhotoDialog(shelf: ShelfItem) {
        AlertDialog.Builder(this)
            .setTitle("사진 삭제")
            .setMessage("선반 ${shelf.shelfNumber}의 사진을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                val photoId = shelf.id
                lifecycleScope.launch {
                    repository.deleteShelfPhoto(photoId)
                        .onSuccess { loadShelves() }
                        .onFailure { Toast.makeText(this@ShelfListActivity, "사진 삭제 실패", Toast.LENGTH_SHORT).show() }
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
