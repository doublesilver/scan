package com.scan.warehouse.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
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
import com.scan.warehouse.scanner.DataWedgeManager
import com.scan.warehouse.viewmodel.ScanViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: ScanViewModel by viewModels()
    private lateinit var productAdapter: ProductAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        DataWedgeManager.setupProfile(this)

        setupRecyclerView()
        setupSearch()
        observeViewModel()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                DataWedgeManager.scanFlow.collect { barcode ->
                    viewModel.scanBarcode(barcode)
                }
            }
        }
    }

    private fun setupRecyclerView() {
        productAdapter = ProductAdapter { item ->
            viewModel.scanBarcode(item.skuId)
        }
        binding.rvProducts.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = productAdapter
        }
    }

    private fun setupSearch() {
        binding.btnSearch.setOnClickListener {
            val query = binding.etSearch.text.toString().trim()
            viewModel.searchProducts(query)
        }

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etSearch.text.toString().trim()
                viewModel.searchProducts(query)
                true
            } else false
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG)
                    .setAction("재시도") {
                        val query = binding.etSearch.text.toString().trim()
                        if (query.isNotBlank()) {
                            viewModel.searchProducts(query)
                        }
                    }
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
        binding.tvCategory.text = result.category ?: "-"
        binding.tvBrand.text = result.brand ?: "-"

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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
