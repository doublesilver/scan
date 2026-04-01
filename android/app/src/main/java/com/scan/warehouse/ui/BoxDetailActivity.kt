package com.scan.warehouse.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.content.Intent
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import coil.load
import com.google.gson.Gson
import com.scan.warehouse.R
import com.scan.warehouse.databinding.ActivityBoxDetailBinding
import com.scan.warehouse.model.BoxResponse
import com.scan.warehouse.model.MapLayout
import com.scan.warehouse.repository.ProductRepository
import kotlinx.coroutines.launch

class BoxDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BOX_DATA = "extra_box_data"

        fun createIntent(context: Context, boxJson: String): Intent {
            return Intent(context, BoxDetailActivity::class.java).apply {
                putExtra(EXTRA_BOX_DATA, boxJson)
            }
        }
    }

    private lateinit var binding: ActivityBoxDetailBinding
    private lateinit var repository: ProductRepository
    private var mapLayout: MapLayout? = null
    private var currentLocation: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBoxDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val json = intent.getStringExtra(EXTRA_BOX_DATA) ?: run { finish(); return }
        val box = Gson().fromJson(json, BoxResponse::class.java)
        repository = ProductRepository(this)

        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        binding.tvProductMasterName.text = box.productMasterName
        currentLocation = box.location
        setupLocationTags(box)
        setupOptionImages(box)
        setupLinkButtons(box)
        setupBottomBar(box)
        setupImageZoom()

        loadMapAndPhotos(box)
    }

    private fun setupImageZoom() {
        binding.ivZonePhoto.setOnClickListener { showZoomDialog(binding.ivZonePhoto) }
        binding.ivShelfPhoto.setOnClickListener { showZoomDialog(binding.ivShelfPhoto) }
    }

    private fun showZoomDialog(sourceImage: ImageView) {
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
        dialog.show()
    }

    private fun setupLocationTags(box: BoxResponse) {
        val loc = box.location ?: return
        val parts = loc.replace("층", "").split("-").map { it.trim() }

        if (parts.size >= 2) {
            val zone = parts[1]
            binding.tvZoneTag.text = "${zone}구역"
        }
        if (parts.size >= 3) {
            binding.tvShelfTag.text = "${parts[2]}번째 줄"
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
        binding.layoutInlineMap.removeAllViews()
        val density = resources.displayMetrics.density

        val parsedZone = parseLocationZone(location)
        val parsedShelf = parseLocationShelf(location)

        val zones = layout.zones.ifEmpty { return }

        for (zone in zones) {
            val zoneLabel = TextView(this).apply {
                text = zone.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@BoxDetailActivity, R.color.on_surface))
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            }
            binding.layoutInlineMap.addView(zoneLabel)

            val grid = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                    bottomMargin = (4 * density).toInt()
                }
            }

            for (r in 1..zone.rows) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                }

                for (c in 1..zone.cols) {
                    val cellKey = "${zone.code}-$r-$c"
                    val cell = layout.cells[cellKey]
                    val cellNum = (r - 1) * zone.cols + c
                    val isHighlight = zone.code == parsedZone && cellNum.toString() == parsedShelf

                    val cellView = TextView(this).apply {
                        text = "${zone.code}-$cellNum"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 7f)
                        gravity = Gravity.CENTER
                        setTextColor(Color.WHITE)
                        if (isHighlight) {
                            val gd = GradientDrawable().apply {
                                setColor(Color.parseColor("#FFD700"))
                                setStroke((2 * density).toInt(), Color.parseColor("#FF6A00"))
                                cornerRadius = 4 * density
                            }
                            background = gd
                            setTextColor(Color.BLACK)
                            setTypeface(null, Typeface.BOLD)
                        } else {
                            val bgColor = if (cell?.status == "used") "#2e7d32" else "#45474c"
                            setBackgroundColor(Color.parseColor(bgColor))
                        }
                        val size = (22 * density).toInt()
                        val margin = (1 * density).toInt()
                        layoutParams = LinearLayout.LayoutParams(0, size, 1f).apply {
                            setMargins(margin, margin, margin, margin)
                        }
                        setOnClickListener {
                            startActivity(CellDetailActivity.createIntent(
                                this@BoxDetailActivity,
                                layout.floor,
                                zone.code,
                                cellKey
                            ))
                            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                        }
                    }
                    if (isHighlight) {
                        ObjectAnimator.ofFloat(cellView, "alpha", 1f, 0.3f).apply {
                            duration = 600
                            repeatCount = ValueAnimator.INFINITE
                            repeatMode = ValueAnimator.REVERSE
                            start()
                        }
                    }
                    row.addView(cellView)
                }
                grid.addView(row)
            }
            binding.layoutInlineMap.addView(grid)
        }

        binding.blockMap.setOnClickListener {
            val loc = mapLayout?.let { layout ->
                WarehouseMapDialog.show(this, location, layout) { floor, zoneCode, _, _, cellKey ->
                    startActivity(CellDetailActivity.createIntent(this, floor, zoneCode, cellKey))
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                }
            }
        }
    }

    private fun loadCellPhotos(layout: MapLayout, location: String?) {
        val parsedZone = parseLocationZone(location) ?: return
        val parsedShelf = parseLocationShelf(location) ?: return

        val zone = layout.zones.find { it.code == parsedZone } ?: return

        for (r in 1..zone.rows) {
            for (c in 1..zone.cols) {
                val cellNum = (r - 1) * zone.cols + c
                if (cellNum.toString() != parsedShelf) continue

                val cellKey = "$parsedZone-$r-$c"
                val cell = layout.cells[cellKey] ?: continue
                val levels = cell.levels ?: continue

                if (levels.isNotEmpty()) {
                    val photo = levels.firstOrNull { !it.photo.isNullOrEmpty() }?.photo
                    if (photo != null) {
                        binding.ivShelfPhoto.load(repository.getImageUrl(photo)) {
                            crossfade(true)
                            placeholder(R.drawable.ic_placeholder)
                            error(R.drawable.ic_placeholder)
                        }
                    }
                }
            }
        }
    }

    private fun setupOptionImages(box: BoxResponse) {
        if (box.productMasterImage == null) return

        binding.layoutOptionImages.removeAllViews()
        val density = resources.displayMetrics.density

        val mainImage = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, (140 * density).toInt()).apply {
                bottomMargin = (6 * density).toInt()
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = ContextCompat.getDrawable(this@BoxDetailActivity, R.drawable.bg_photo_placeholder)
        }
        mainImage.load(box.productMasterImage) {
            crossfade(true)
            placeholder(R.drawable.ic_placeholder)
            error(R.drawable.ic_placeholder)
        }
        binding.layoutOptionImages.addView(mainImage)

        val label = TextView(this).apply {
            text = "한국 옵션"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(ContextCompat.getColor(this@BoxDetailActivity, R.color.on_surface_variant))
            gravity = Gravity.CENTER
        }
        binding.layoutOptionImages.addView(label)
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
                        startActivity(CellDetailActivity.createIntent(this@BoxDetailActivity, floor, zoneCode, cellKey))
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
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

        binding.btnBarPrint.setOnClickListener {
            Toast.makeText(this, "인쇄 기능 준비 중", Toast.LENGTH_SHORT).show()
        }
    }

    private fun parseLocationZone(location: String?): String? {
        val loc = location ?: return null
        val parts = loc.replace("층", "").split("-").map { it.trim() }
        return if (parts.size >= 2) parts[1] else null
    }

    private fun parseLocationShelf(location: String?): String? {
        val loc = location ?: return null
        val parts = loc.replace("층", "").split("-").map { it.trim() }
        return if (parts.size >= 3) parts[2] else null
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
