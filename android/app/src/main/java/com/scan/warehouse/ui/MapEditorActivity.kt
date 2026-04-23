package com.scan.warehouse.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.FileChooserParams
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.scan.warehouse.R
import com.scan.warehouse.databinding.ActivityMapEditorBinding
import com.scan.warehouse.network.RetrofitClient
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MapEditorActivity : BaseActivity() {

    companion object {
        private const val EXTRA_ZONE = "extra_zone"

        fun createIntent(context: Context, zone: String? = null): Intent {
            return Intent(context, MapEditorActivity::class.java).apply {
                if (zone != null) putExtra(EXTRA_ZONE, zone)
            }
        }
    }

    private lateinit var binding: ActivityMapEditorBinding
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        fileUploadCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        fileUploadCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            if (binding.webView.canGoBack()) {
                binding.webView.goBack()
            } else {
                finishWithSlide()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    finishWithSlide()
                }
            }
        })

        setupWebView()
        loadEditor()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        binding.webView.webViewClient = WebViewClient()

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback
                fileChooserLauncher.launch("image/*")
                return true
            }
        }
    }

    private fun loadEditor() {
        val baseUrl = RetrofitClient.getBaseUrl(this)
        if (baseUrl.isBlank() || baseUrl == "http://") {
            Toast.makeText(this, "서버 연결이 필요합니다 (설정에서 서버 URL 입력)", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val editorUrl = if (baseUrl.endsWith("/")) "${baseUrl}admin/map-editor" else "$baseUrl/admin/map-editor"
        val zone = intent.getStringExtra(EXTRA_ZONE)
        val url = if (zone != null) "$editorUrl?zone=$zone" else editorUrl
        binding.webView.loadUrl(url)
    }

}
