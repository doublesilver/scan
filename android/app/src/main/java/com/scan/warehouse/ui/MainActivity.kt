package com.scan.warehouse.ui

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
import com.scan.warehouse.model.ScanResponse
import com.scan.warehouse.network.RetrofitClient
import com.scan.warehouse.network.UpdateManager
import com.scan.warehouse.repository.ProductRepository
import com.scan.warehouse.scanner.DataWedgeManager
import com.scan.warehouse.WarehouseApp
import com.scan.warehouse.viewmodel.ScanViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: ScanViewModel by viewModels()
    private lateinit var productAdapter: ProductAdapter
    private val keystrokeBuffer = StringBuilder()
    private var lastKeystrokeTime = 0L
    private var lastIntentScanTime = 0L
    private var serverStatusJob: Job? = null
    private var currentScanResult: ScanResponse? = null

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

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                val update = UpdateManager.checkUpdate(this@MainActivity)
                if (update != null) {
                    UpdateManager.showUpdateDialog(this@MainActivity, update)
                }
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
        binding.btnSettings.setOnClickListener {
            startWithSlide(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun setupBottomNav() {
        binding.btnNavPlacement.setOnClickListener {
            startWithSlide(ProductPlacementActivity.createIntent(this))
        }
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

        val density = resources.displayMetrics.density
        val pad = (8 * density).toInt()

        if (mapLayout == null || mapLayout.zones.isEmpty()) {
            val errorView = TextView(this).apply {
                text = "도면을 불러올 수 없습니다\n서버 연결을 확인하고 화면을 탭해 다시 시도하세요"
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.on_surface_variant))
                setPadding(pad * 4, pad * 8, pad * 4, pad * 8)
                isClickable = true
                isFocusable = true
                setOnClickListener { loadMainMap() }
            }
            binding.mapContainer.addView(errorView)
            return
        }

        val zones = mapLayout.zones
        val floor = mapLayout.floor

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
                text = zone.name
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
        // 셀 크기를 고정해서 구역마다 col 폭이 달라지지 않게 함 — 통로가 같은 x 위치에 정렬됨
        val cellSize = (36 * density).toInt()
        val margin = (2 * density).toInt()

        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((8 * density).toInt(), 0, (8 * density).toInt(), (4 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (!zone.borderColor.isNullOrEmpty()) {
                val bw = ((zone.borderWidth ?: 1) * density).toInt()
                val gd = android.graphics.drawable.GradientDrawable().apply {
                    setStroke(bw, android.graphics.Color.parseColor(zone.borderColor))
                    cornerRadius = 4 * density
                }
                background = gd
                setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
            }
        }

        for (row in 1..zone.rows) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            for (col in 1..zone.cols) {
                val cellKey = "${zone.code}-$row-$col"
                val cellData = mapLayout?.cells?.get(cellKey)
                val cellLabel = cellData?.label.orEmpty()
                val cellStatus = cellData?.status ?: "empty"

                // 통로 세로 연결 여부만 확인 — 테이블/PC는 병합 안 함
                val aboveStatus = mapLayout?.cells?.get("${zone.code}-${row - 1}-$col")?.status
                val belowStatus = mapLayout?.cells?.get("${zone.code}-${row + 1}-$col")?.status

                val cell = TextView(this).apply {
                    text = cellLabel
                    textSize = 9f
                    gravity = android.view.Gravity.CENTER

                    fun applyMargins(l: Int, t: Int, r: Int, b: Int) {
                        layoutParams = LinearLayout.LayoutParams(cellSize, cellSize).apply {
                            setMargins(l, t, r, b)
                        }
                    }

                    applyMargins(margin, margin, margin, margin)

                    when (cellStatus) {
                        "used" -> {
                            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.cell_used_light))
                            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.cell_used_dark))
                            isClickable = true
                            isFocusable = true
                            setOnClickListener {
                                startWithSlide(CellDetailActivity.createIntent(this@MainActivity, floor, zone.code, cellKey))
                            }
                        }
                        "full" -> {
                            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.cell_full_light))
                            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.cell_full_dark))
                            isClickable = true
                            isFocusable = true
                            setOnClickListener {
                                startWithSlide(CellDetailActivity.createIntent(this@MainActivity, floor, zone.code, cellKey))
                            }
                        }
                        "aisle" -> {
                            // 통로: 세로로 연결된 실제 공간처럼 하나의 막대로 보이게
                            val connectedAbove = aboveStatus == "aisle"
                            val connectedBelow = belowStatus == "aisle"
                            applyMargins(margin, if (connectedAbove) 0 else margin, margin, if (connectedBelow) 0 else margin)
                            setBackgroundColor(android.graphics.Color.parseColor("#2a2a2a"))
                            setTextColor(android.graphics.Color.parseColor("#999999"))
                            textSize = 8f
                            // 연결된 막대의 맨 위 셀에만 "통로" 텍스트
                            text = if (connectedAbove) "" else "통로"
                        }
                        "table" -> {
                            setBackgroundColor(android.graphics.Color.parseColor("#3d2e1e"))
                            setTextColor(android.graphics.Color.parseColor("#d4a574"))
                            typeface = Typeface.DEFAULT_BOLD
                            textSize = 8f
                            text = "테이블"
                        }
                        "pc" -> {
                            setBackgroundColor(android.graphics.Color.parseColor("#1e3a5f"))
                            setTextColor(android.graphics.Color.parseColor("#9fc5e8"))
                            typeface = Typeface.DEFAULT_BOLD
                            textSize = 8f
                            text = "물류PC"
                        }
                        else -> {
                            // 빈 슬롯: 투명, 텍스트 없음, 비클릭
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            text = ""
                        }
                    }
                    if (!cellData?.borderColor.isNullOrEmpty()) {
                        val bw = ((cellData?.borderWidth ?: 1) * density).toInt()
                        val bg = background
                        val gd = android.graphics.drawable.GradientDrawable().apply {
                            if (bg is android.graphics.drawable.ColorDrawable) setColor(bg.color)
                            setStroke(bw, android.graphics.Color.parseColor(cellData?.borderColor))
                            cornerRadius = 2 * density
                        }
                        background = gd
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
            resetToMap()
        }
    }

    private fun resetToMap() {
        binding.layoutMainMap.visibility = View.VISIBLE
        binding.layoutScanResult.visibility = View.GONE
        binding.rvProducts.visibility = View.GONE
        binding.bottomBar.visibility = View.VISIBLE
        currentScanResult = null
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
                binding.bottomBar.visibility = View.GONE
                showScanResult(result)
            } else {
                resetToMap()
            }
        }

        viewModel.searchResults.observe(this) { response ->
            if (response != null) {
                binding.layoutMainMap.visibility = View.GONE
                binding.layoutScanResult.visibility = View.GONE
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
        startWithSlide(BoxDetailActivity.createIntent(this, box))
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

    private fun showScanResult(result: ScanResponse) {
        currentScanResult = result
        binding.rvProducts.visibility = View.GONE
        binding.layoutScanResult.visibility = View.VISIBLE

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

        // 그룹 필드는 UI에서 숨김 (서버·DB는 그대로, 나중에 다시 켤 수 있음)
        binding.tvProductMasterName.visibility = View.GONE

        binding.btnQuickPrint.setOnClickListener {
            val barcode = result.barcodes.firstOrNull() ?: return@setOnClickListener
            binding.btnQuickPrint.isEnabled = false
            Toast.makeText(this, "인쇄 요청 중...", Toast.LENGTH_SHORT).show()
            (application as WarehouseApp).appScope.launch {
                val (msg, isError) = try {
                    val r = repository.printLabel(barcode, result.skuId, result.productName, 1)
                    r.message to false
                } catch (e: Exception) {
                    "인쇄 실패: ${e.message?.take(80) ?: "연결 오류"}" to true
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, msg, if (isError) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
                    binding.btnQuickPrint.isEnabled = true
                }
            }
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
            val found = com.scan.warehouse.network.ServerDiscovery.findServer(this@MainActivity)
            if (found != null) {
                RetrofitClient.saveBaseUrl(this@MainActivity, found)
                Toast.makeText(this@MainActivity, "서버 연결됨", Toast.LENGTH_SHORT).show()
            }
            checkServerStatus()
        }
    }

    private fun checkServerStatus() {
        serverStatusJob?.cancel()
        serverStatusJob = lifecycleScope.launch {
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
            if (char.isLetterOrDigit() || char == '-') {
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
            resetToMap()
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
        DataWedgeManager.resetBuffer()
    }

    override fun onDestroy() {
        serverStatusJob?.cancel()
        super.onDestroy()
    }
}
