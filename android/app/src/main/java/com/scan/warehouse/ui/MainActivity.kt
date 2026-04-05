package com.scan.warehouse.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.google.android.material.snackbar.Snackbar
import com.scan.warehouse.R
import com.scan.warehouse.databinding.ActivityMainBinding
import com.scan.warehouse.model.ScanResponse
import com.scan.warehouse.network.RetrofitClient
import com.scan.warehouse.network.UpdateManager
import com.scan.warehouse.repository.ProductRepository
import com.scan.warehouse.scanner.DataWedgeManager
import com.scan.warehouse.viewmodel.ScanViewModel
import android.view.inputmethod.InputMethodManager
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

    @Inject lateinit var repository: ProductRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        DataWedgeManager.setupProfile(this)

        setupRecyclerView()
        setupSearch()
        setupHeader()
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
            binding.layoutScanWaiting.visibility = View.VISIBLE
            binding.layoutScanResult.visibility = View.GONE
            binding.bottomBar.visibility = View.GONE
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
                binding.layoutScanWaiting.visibility = View.GONE
                showScanResult(result)
            } else {
                binding.layoutScanResult.visibility = View.GONE
                binding.bottomBar.visibility = View.GONE
            }
        }

        viewModel.searchResults.observe(this) { response ->
            if (response != null) {
                binding.layoutScanWaiting.visibility = View.GONE
                binding.layoutScanResult.visibility = View.GONE
                binding.bottomBar.visibility = View.GONE
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
        binding.bottomBar.visibility = View.VISIBLE

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

        binding.layoutScanResult.setOnClickListener {
            startWithSlide(Intent(this, DetailActivity::class.java).apply {
                putExtra(DetailActivity.EXTRA_DATA, result)
            })
        }
    }

    private fun checkServerOnce() {
        val url = RetrofitClient.getBaseUrl(this)
        if (url.isNotBlank() && url != "http://") return

        lifecycleScope.launch {
            val found = com.scan.warehouse.network.ServerDiscovery.findServer()
            if (found != null) {
                RetrofitClient.saveBaseUrl(this@MainActivity, found)
                Toast.makeText(this@MainActivity, "서버 연결됨", Toast.LENGTH_SHORT).show()
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
            binding.bottomBar.visibility = View.GONE
            binding.layoutScanWaiting.visibility = View.VISIBLE
            currentScanResult = null
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        DataWedgeManager.register(this)
    }

    override fun onPause() {
        super.onPause()
        DataWedgeManager.unregister(this)
    }
}
