package com.scan.warehouse.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.scan.warehouse.R
import com.scan.warehouse.databinding.ActivitySplashBinding
import com.scan.warehouse.db.AppDatabase
import com.scan.warehouse.repository.ProductRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var repository: ProductRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = ProductRepository(applicationContext)

        binding.btnRetry.setOnClickListener { checkServer() }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        checkServer()
    }

    override fun onResume() {
        super.onResume()
        if (binding.layoutButtons.visibility == View.VISIBLE) {
            checkServer()
        }
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
}
