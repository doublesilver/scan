package com.scan.warehouse.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
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
import com.scan.warehouse.repository.ProductRepository
import com.scan.warehouse.scanner.DataWedgeManager
import com.scan.warehouse.viewmodel.ScanViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: ScanViewModel by viewModels()
    private lateinit var productAdapter: ProductAdapter
    private var lastKeystrokeTime = 0L

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

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                DataWedgeManager.scanFlow.collect { barcode ->
                    binding.etSearch.setText(barcode)
                    binding.etSearch.setSelection(barcode.length)
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
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    private fun setupRecyclerView() {
        productAdapter = ProductAdapter { item ->
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
            result?.let {
                binding.layoutScanWaiting.visibility = View.GONE
                showScanResult(it)
            }
        }

        viewModel.searchResults.observe(this) { response ->
            response?.let {
                binding.layoutScanWaiting.visibility = View.GONE
                binding.layoutScanResult.visibility = View.GONE
                binding.rvProducts.visibility = View.VISIBLE
                productAdapter.submitList(it.items)
            }
        }

        viewModel.isOffline.observe(this) { offline ->
            binding.tvOfflineBanner.visibility = if (offline) View.VISIBLE else View.GONE
        }
    }

    private fun showScanResult(result: ScanResponse) {
        binding.rvProducts.visibility = View.GONE
        binding.layoutScanResult.visibility = View.VISIBLE

        binding.tvProductName.text = result.productName
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
            val intent = Intent(this, DetailActivity::class.java).apply {
                putExtra(DetailActivity.EXTRA_DATA, result)
            }
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
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
            if (event.keyCode == KeyEvent.KEYCODE_ENTER) {
                val query = binding.etSearch.text.toString().trim()
                if (query.isNotBlank()) {
                    performSearch(query)
                }
                return true
            }
            val char = event.unicodeChar.toChar()
            if (char.isDigit() || char.isLetter()) {
                val now = System.currentTimeMillis()
                if (now - lastKeystrokeTime > 300) {
                    binding.etSearch.setText("")
                }
                lastKeystrokeTime = now
                if (!binding.etSearch.hasFocus()) {
                    binding.etSearch.requestFocus()
                }
                binding.etSearch.append(char.toString())
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("BARCODE")?.let { barcode ->
            binding.etSearch.setText(barcode)
            binding.etSearch.setSelection(barcode.length)
            performSearch(barcode)
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
