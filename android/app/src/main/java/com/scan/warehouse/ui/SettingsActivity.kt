package com.scan.warehouse.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.scan.warehouse.BuildConfig
import com.scan.warehouse.R
import com.scan.warehouse.databinding.ActivitySettingsBinding
import com.scan.warehouse.network.RetrofitClient
import com.scan.warehouse.repository.ProductRepository
import com.scan.warehouse.scanner.DataWedgeManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding
    @Inject lateinit var repository: ProductRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "서버 설정"

        binding.tvVersion.text = "v${BuildConfig.VERSION_NAME}"

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                DataWedgeManager.scanFlow.collect { barcode ->
                    val intent = Intent(this@SettingsActivity, MainActivity::class.java).apply {
                        putExtra("BARCODE", barcode)
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    startActivity(intent)
                    finish()
                }
            }
        }

        if (BuildConfig.FLAVOR == "demo") {
            binding.tvConnectionStatus.text = "데모 모드 - 서버 연결 불필요"
            binding.tvConnectionStatus.visibility = View.VISIBLE
            binding.etServerUrl.isEnabled = false
            binding.btnSave.isEnabled = false
            binding.btnTest.isEnabled = false
            binding.btnAutoDiscover.isEnabled = false
            return
        }

        binding.etServerUrl.setText(RetrofitClient.getBaseUrl(this))

        binding.btnAutoDiscover.setOnClickListener {
            binding.btnAutoDiscover.isEnabled = false
            binding.btnAutoDiscover.text = "감지 중..."
            binding.tvConnectionStatus.visibility = View.GONE
            lifecycleScope.launch {
                val found = com.scan.warehouse.network.ServerDiscovery.findServer()
                binding.btnAutoDiscover.isEnabled = true
                binding.btnAutoDiscover.text = "서버 자동 감지"
                binding.tvConnectionStatus.visibility = View.VISIBLE
                if (found != null) {
                    binding.etServerUrl.setText(found)
                    RetrofitClient.saveBaseUrl(this@SettingsActivity, found)
                    binding.tvConnectionStatus.text = "감지 완료: $found"
                    binding.tvConnectionStatus.setTextColor(getColor(R.color.success))
                } else {
                    binding.tvConnectionStatus.text = "서버를 찾을 수 없습니다"
                    binding.tvConnectionStatus.setTextColor(getColor(R.color.error))
                }
            }
        }

        binding.btnMapEditor.setOnClickListener {
            startWithSlide(MapEditorActivity.createIntent(this))
        }

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

    private val keystrokeBuffer = StringBuilder()
    private var lastKeystrokeTime = 0L

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.keyCode == KeyEvent.KEYCODE_ENTER && keystrokeBuffer.isNotBlank()) {
                val barcode = keystrokeBuffer.toString()
                keystrokeBuffer.clear()
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("BARCODE", barcode)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                finish()
                return true
            }
            val char = event.unicodeChar.toChar()
            if (char.isDigit()) {
                val now = System.currentTimeMillis()
                if (now - lastKeystrokeTime > 300) {
                    keystrokeBuffer.clear()
                }
                lastKeystrokeTime = now
                keystrokeBuffer.append(char)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        DataWedgeManager.register(this)
    }

    override fun onPause() {
        super.onPause()
        DataWedgeManager.unregister(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finishWithSlide()
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
