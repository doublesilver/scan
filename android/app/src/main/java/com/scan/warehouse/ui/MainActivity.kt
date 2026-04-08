package com.scan.warehouse.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.google.android.material.snackbar.Snackbar
import com.scan.warehouse.R
import com.scan.warehouse.databinding.ActivityMainBinding
import com.scan.warehouse.model.MapLayout
import com.scan.warehouse.model.MapZone
import com.scan.warehouse.model.ParsedLocation
import com.scan.warehouse.model.ScanResponse
import com.scan.warehouse.network.RetrofitClient
import com.scan.warehouse.network.UpdateManager
import com.scan.warehouse.repository.ProductRepository
import com.scan.warehouse.scanner.DataWedgeManager
import com.scan.warehouse.viewmodel.ScanViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: ScanViewModel by viewModels()
    private lateinit var productAdapter: ProductAdapter
    private val keystrokeBuffer = StringBuilder()
    private var lastKeystrokeTime = 0L
    private var lastIntentScanTime = 0L
    private val mapAnimators = mutableListOf<ObjectAnimator>()

    @Inject lateinit var repository: ProductRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        DataWedgeManager.setupProfile(this)

        setupRecyclerView()
        setupSearch()
        setupHeader()
        setupBottomNav()
        loadMainMap()
        observeViewModel()
        checkServerOnce()

        binding.etSearch.requestFocus()

        lifecycleScope.launch {
            val update = UpdateManager.checkUpdate(this@MainActivity)
            if (update != null) {
                UpdateManager.showUpdateDialog(this@MainActivity, update)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                DataWedgeManager.scanFlow.collect { barcode ->
                    lastIntentScanTime = System.currentTimeMillis()
                    binding.etSearch.setText(barcode)
                    binding.etSearch.setSelection(barcode.length)
                    hideKeyboard()
                    performSearch(barcode)
                }
            }
        }

        intent.getStringExtra("BARCODE")?.let { barcode ->
            binding.etSearch.setText(barcode)
            binding.etSearch.setSelection(barcode.length)
            performSearch(barcode)
            intent.removeExtra("BARCODE")
        }
    }

    private fun setupHeader() {
        binding.btnBarCart.setOnClickListener {
            currentScanResult?.let { addToCart(it) }
        }

        binding.btnSettings.setOnClickListener {
            startWithSlide(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun setupBottomNav() {
        binding.btnNavPlacement.setOnClickListener {
            startWithSlide(ProductPlacementActivity.createIntent(this))
        }
        binding.btnBarCart.isEnabled = false
    }

    private fun loadMainMap() {
        binding.progressMap.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = repository.getMapLayout()
            binding.progressMap.visibility = View.GONE
            result.onSuccess { layout ->
                renderMap(layout)
            }.onFailure {
                renderMap(null)
            }
        }
    }

    private fun renderMap(mapLayout: MapLayout?) {
        binding.mapContainer.removeAllViews()
        mapAnimators.forEach { it.cancel() }
        mapAnimators.clear()

        val density = resources.displayMetrics.density
        val pad = (8 * density).toInt()

        val fallbackZones = listOf(
            MapZone("A", "501호", 3, 4),
            MapZone("B", "포장다이", 3, 2),
            MapZone("C", "502호", 3, 3),
        )
        val zones = if (mapLayout != null && mapLayout.zones.isNotEmpty()) mapLayout.zones else fallbackZones
        val floor = mapLayout?.floor ?: 1

        val headerText = TextView(this).apply {
            text = "${floor}층 창고 도면"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.on_surface))
            setPadding(pad, pad, pad, (4 * density).toInt())
        }
        binding.mapContainer.addView(headerText)

        for (zone in zones) {
            val zoneLabel = TextView(this).apply {
                text = "${zone.name} (${zone.code}구역)"
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.on_surface_variant))
                setPadding(pad, (6 * density).toInt(), pad, (2 * density).toInt())
            }
            binding.mapContainer.addView(zoneLabel)

            val grid = createMapGrid(zone, mapLayout, floor, density)
            binding.mapContainer.addView(grid)
        }
    }

    private fun createMapGrid(zone: MapZone, mapLayout: MapLayout?, floor: Int, density: Float): LinearLayout {
        val cellSize = (44 * density).toInt()
        val margin = (3 * density).toInt()

        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((8 * density).toInt(), 0, (8 * density).toInt(), (4 * density).toInt())
        }

        for (row in 1..zone.rows) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            for (col in 1..zone.cols) {
                val cellKey = "${zone.code}-$row-$col"
                val cellData = mapLayout?.cells?.get(cellKey)
                val cellNum = (row - 1) * zone.cols + col
                val cellText = cellData?.name ?: "${zone.code}-$cellNum"

                val cell = TextView(this).apply {
                    text = cellText
                    textSize = 9f
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(cellSize, cellSize).apply {
                        setMargins(margin, margin, margin, margin)
                    }
                    when (cellData?.status) {
                        "full" -> {
                            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.cell_full_light))
                            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.cell_full_dark))
                        }
                        "used" -> {
                            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.cell_used_light))
                            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.cell_used_dark))
                        }
                        else -> {
                            val gd = GradientDrawable().apply {
                                setColor(ContextCompat.getColor(this@MainActivity, R.color.surface_container_high))
                                cornerRadius = 4 * density
                            }
                            background = gd
                            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.on_surface_variant))
                        }
                    }
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        startWithSlide(CellDetailActivity.createIntent(this@MainActivity, floor, zone.code, cellKey))
                    }
                }
                rowLayout.addView(cell)
            }
            grid.addView(rowLayout)
        }
        return grid
    }

    private fun setupRecyclerView() {
        productAdapter = ProductAdapter(repository) { item ->
            val barcode = item.barcode
            if (barcode != null) {
                viewModel.scanBarcode(barcode)
            } else {
                Toast.makeText(this, "바코드 정보가 없습니다", Toast.LENGTH_SHORT).show()
            }
        }
        binding.rvProducts.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = productAdapter
        }
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) return
        if (query.matches(Regex(DataWedgeManager.BARCODE_PATTERN))) {
            viewModel.scanBarcode(query)
        } else if (query.startsWith("BOX-")) {
            viewModel.scanBox(query)
        } else {
            viewModel.searchProducts(query)
        }
    }

    private fun setupSearch() {
        binding.btnSearch.setOnClickListener {
            performSearch(binding.etSearch.text.toString().trim())
        }

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(binding.etSearch.text.toString().trim())
                true
            } else false
        }

        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                binding.btnClearSearch.visibility =
                    if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.setText("")
            binding.layoutMainMap.visibility = View.VISIBLE
            binding.layoutScanResult.visibility = View.GONE
            binding.btnBarCart.isEnabled = false
            binding.rvProducts.visibility = View.GONE
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                Snackbar.make(binding.root, it, 2000)
                    .setAction("닫기") {}
                    .show()
                viewModel.clearError()
            }
        }

        viewModel.scanResult.observe(this) { result ->
            if (result != null) {
                binding.layoutMainMap.visibility = View.GONE
                showScanResult(result)
            } else {
                binding.layoutScanResult.visibility = View.GONE
                binding.btnBarCart.isEnabled = false
            }
        }

        viewModel.searchResults.observe(this) { response ->
            if (response != null) {
                binding.layoutMainMap.visibility = View.GONE
                binding.layoutScanResult.visibility = View.GONE
                binding.btnBarCart.isEnabled = false
                binding.rvProducts.visibility = View.VISIBLE
                productAdapter.submitList(response.items)
            } else {
                binding.rvProducts.visibility = View.GONE
            }
        }

        viewModel.isOffline.observe(this) { offline ->
            binding.tvOfflineBanner.visibility = if (offline) View.VISIBLE else View.GONE
        }

        viewModel.boxResult.observe(this) { box ->
            if (box != null) {
                showBoxDialog(box)
                viewModel.clearBoxResult()
            }
        }

        viewModel.boxNotFound.observe(this) { qrCode ->
            if (qrCode != null) {
                showBoxNotFoundDialog(qrCode)
                viewModel.clearBoxNotFound()
            }
        }
    }

    private fun showBoxDialog(box: com.scan.warehouse.model.BoxResponse) {
        val json = com.google.gson.Gson().toJson(box)
        startWithSlide(BoxDetailActivity.createIntent(this, json))
    }

    private fun showBoxNotFoundDialog(qrCode: String) {
        AlertDialog.Builder(this)
            .setTitle("등록되지 않은 박스")
            .setMessage("등록되지 않은 박스입니다. 등록하시겠습니까?")
            .setPositiveButton("등록하기") { _, _ ->
                startWithSlide(BoxRegisterActivity.createIntent(this, qrCode))
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private var currentScanResult: ScanResponse? = null

    private fun showScanResult(result: ScanResponse) {
        currentScanResult = result
        binding.rvProducts.visibility = View.GONE
        binding.layoutScanResult.visibility = View.VISIBLE
        binding.btnBarCart.isEnabled = true

        binding.tvProductName.text = BarcodeUtils.applyColorKeywords(result.productName)
        binding.tvSkuId.text = "SKU: ${result.skuId}"

        val barcode = result.barcodes.firstOrNull() ?: ""
        binding.tvBarcode.text = BarcodeUtils.formatBold(barcode)

        val thumbImage = result.images.firstOrNull { it.imageType == "thumbnail" }
            ?: result.images.firstOrNull()

        if (thumbImage != null) {
            binding.ivProductImage.load(viewModel.getImageUrl(thumbImage.filePath)) {
                crossfade(true)
                placeholder(R.drawable.ic_placeholder)
                error(R.drawable.ic_placeholder)
            }
        } else {
            binding.ivProductImage.setImageResource(R.drawable.ic_placeholder)
        }

        if (!result.productMasterName.isNullOrBlank()) {
            binding.tvProductMasterName.text = "그룹: ${result.productMasterName}"
            binding.tvProductMasterName.visibility = View.VISIBLE
        } else {
            binding.tvProductMasterName.visibility = View.GONE
        }

        val displayLocation = result.productMasterLocation?.takeIf { it.isNotBlank() }
            ?: result.location?.takeIf { it.isNotBlank() }
        if (displayLocation != null) {
            binding.tvLocation.text = displayLocation
            binding.tvLocation.visibility = View.VISIBLE
        } else {
            binding.tvLocation.visibility = View.GONE
        }

        binding.layoutScanResult.setOnClickListener {
            startWithSlide(Intent(this, DetailActivity::class.java).apply {
                putExtra(DetailActivity.EXTRA_DATA, result)
            })
        }
    }

    private fun checkServerOnce() {
        val url = RetrofitClient.getBaseUrl(this)
        if (url.isNotBlank() && url != "http://") {
            checkServerStatus()
            return
        }

        lifecycleScope.launch {
            val found = com.scan.warehouse.network.ServerDiscovery.findServer()
            if (found != null) {
                RetrofitClient.saveBaseUrl(this@MainActivity, found)
                Toast.makeText(this@MainActivity, "서버 연결됨", Toast.LENGTH_SHORT).show()
            }
            checkServerStatus()
        }
    }

    private fun checkServerStatus() {
        lifecycleScope.launch {
            val url = RetrofitClient.getBaseUrl(this@MainActivity)
            val result = repository.healthCheck()
            result.onSuccess {
                val ip = url.removePrefix("http://").removePrefix("https://").substringBefore(":")
                binding.tvServerStatus.text = "🟢 연결됨 ($ip)"
                binding.tvServerStatus.setTextColor(Color.parseColor("#A5D6A7"))
            }.onFailure {
                if (url.isBlank() || url == "http://") {
                    binding.tvServerStatus.text = "🔴 서버 연결 안 됨"
                    binding.tvServerStatus.setTextColor(Color.parseColor("#EF9A9A"))
                } else {
                    binding.tvServerStatus.text = "🟡 오프라인 모드"
                    binding.tvServerStatus.setTextColor(Color.parseColor("#FFCC80"))
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (System.currentTimeMillis() - lastIntentScanTime < 1000) {
                return super.dispatchKeyEvent(event)
            }
            if (event.keyCode == KeyEvent.KEYCODE_ENTER) {
                val barcode = keystrokeBuffer.toString().trim()
                keystrokeBuffer.clear()
                lastKeystrokeTime = 0L
                if (barcode.matches(Regex(DataWedgeManager.BARCODE_PATTERN)) || barcode.startsWith("BOX-")) {
                    binding.etSearch.setText(barcode)
                    binding.etSearch.setSelection(barcode.length)
                    hideKeyboard()
                    performSearch(barcode)
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
            val char = event.unicodeChar.toChar()
            if (char.isDigit() || char.isLetter()) {
                val now = System.currentTimeMillis()
                if (now - lastKeystrokeTime > 2000) {
                    keystrokeBuffer.clear()
                }
                lastKeystrokeTime = now
                keystrokeBuffer.append(char)
                if (!binding.etSearch.hasFocus()) binding.etSearch.requestFocus()
                hideKeyboard()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun addToCart(data: ScanResponse) {
        val barcode = data.barcodes.firstOrNull() ?: return
        binding.btnBarCart.isEnabled = false
        Toast.makeText(this, "장바구니 추가 중...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val result = repository.addToCart(barcode, data.skuId, data.productName, 1)
                Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                val msg = if (e is retrofit2.HttpException) {
                    e.response()?.errorBody()?.string()?.let {
                        try { org.json.JSONObject(it).optString("detail", "장바구니 추가 실패") }
                        catch (_: Exception) { "장바구니 추가 실패" }
                    } ?: "장바구니 추가 실패"
                } else "장바구니 추가 실패: ${e.message}"
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnBarCart.isEnabled = true
            }
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("BARCODE")?.let { barcode ->
            binding.etSearch.setText(barcode)
            binding.etSearch.setSelection(barcode.length)
            performSearch(barcode)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.layoutScanResult.visibility == View.VISIBLE || binding.rvProducts.visibility == View.VISIBLE) {
            binding.etSearch.setText("")
            binding.layoutScanResult.visibility = View.GONE
            binding.rvProducts.visibility = View.GONE
            binding.btnBarCart.isEnabled = false
            binding.layoutMainMap.visibility = View.VISIBLE
            currentScanResult = null
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        DataWedgeManager.register(this)
        checkServerStatus()
    }

    override fun onPause() {
        super.onPause()
        DataWedgeManager.unregister(this)
    }

    override fun onDestroy() {
        mapAnimators.forEach { it.cancel() }
        mapAnimators.clear()
        super.onDestroy()
    }
}
