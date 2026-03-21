package com.scan.warehouse.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.scan.warehouse.R
import com.scan.warehouse.databinding.ActivitySettingsBinding
import com.scan.warehouse.network.RetrofitClient
import com.scan.warehouse.repository.ProductRepository
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var repository: ProductRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "서버 설정"

        repository = ProductRepository(applicationContext)

        binding.etServerUrl.setText(RetrofitClient.getBaseUrl(this))

        binding.btnSave.setOnClickListener {
            val url = binding.etServerUrl.text.toString().trim()
            if (url.isEmpty()) {
                binding.etServerUrl.error = "서버 URL을 입력하세요"
                return@setOnClickListener
            }
            try {
                java.net.URL(url)
            } catch (_: Exception) {
                Toast.makeText(this, "올바른 URL을 입력하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            RetrofitClient.saveBaseUrl(this, url)
            Toast.makeText(this, "저장되었습니다", Toast.LENGTH_SHORT).show()
        }

        binding.btnTest.setOnClickListener {
            val url = binding.etServerUrl.text.toString().trim()
            if (url.isEmpty()) {
                binding.etServerUrl.error = "서버 URL을 입력하세요"
                return@setOnClickListener
            }
            try {
                java.net.URL(url)
            } catch (_: Exception) {
                Toast.makeText(this, "올바른 URL을 입력하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            testConnection(url)
        }
    }

    private fun testConnection(urlToSave: String) {
        binding.progressBarSettings.visibility = View.VISIBLE
        binding.tvConnectionStatus.visibility = View.GONE

        val previousUrl = RetrofitClient.getBaseUrl(this)
        RetrofitClient.saveBaseUrl(this, urlToSave)

        lifecycleScope.launch {
            val result = repository.healthCheck()
            binding.progressBarSettings.visibility = View.GONE
            binding.tvConnectionStatus.visibility = View.VISIBLE

            result.onSuccess {
                binding.tvConnectionStatus.text = "연결 성공"
                binding.tvConnectionStatus.setTextColor(getColor(R.color.success))
            }.onFailure { e ->
                RetrofitClient.saveBaseUrl(this@SettingsActivity, previousUrl)
                binding.tvConnectionStatus.text = "연결 실패: ${e.message}"
                binding.tvConnectionStatus.setTextColor(getColor(R.color.error))
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
