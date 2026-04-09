package com.scan.warehouse.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.scan.warehouse.BuildConfig
import com.scan.warehouse.R
import com.scan.warehouse.databinding.ActivitySplashBinding
import com.scan.warehouse.db.AppDatabase
import com.scan.warehouse.repository.ProductRepository
import com.scan.warehouse.scanner.DataWedgeManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    @Inject lateinit var repository: ProductRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRetry.setOnClickListener { checkServer() }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        DataWedgeManager.setupProfile(this)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                DataWedgeManager.scanFlow.collect { barcode ->
                    goToMainWithBarcode(barcode)
                }
            }
        }

        if (BuildConfig.FLAVOR == "demo") {
            goToMain()
        } else {
            checkServer()
        }
    }

    override fun onResume() {
        super.onResume()
        DataWedgeManager.register(this)
        if (BuildConfig.FLAVOR != "demo" && binding.layoutButtons.visibility == View.VISIBLE) {
            checkServer()
        }
    }

    override fun onPause() {
        super.onPause()
        DataWedgeManager.unregister(this)
        DataWedgeManager.resetBuffer()
    }

    private fun checkServer() {
        binding.tvStatus.text = getString(R.string.splash_connecting)
        binding.progressBar.visibility = View.VISIBLE
        binding.layoutButtons.visibility = View.GONE

        lifecycleScope.launch {
            val result = withTimeoutOrNull(3000L) {
                repository.healthCheck()
            }

            if (result?.isSuccess == true) {
                goToMain()
                return@launch
            }

            val cacheCount = AppDatabase.getInstance(this@SplashActivity)
                .productDao().getCount()

            if (cacheCount > 0) {
                Toast.makeText(this@SplashActivity, "오프라인 모드로 진입합니다", Toast.LENGTH_SHORT).show()
                goToMain()
            } else {
                binding.tvStatus.text = getString(R.string.splash_error)
                binding.progressBar.visibility = View.GONE
                binding.layoutButtons.visibility = View.VISIBLE
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun goToMainWithBarcode(barcode: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("BARCODE", barcode)
        }
        startActivity(intent)
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
