package com.scan.warehouse.network

import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.scan.warehouse.BuildConfig
import com.scan.warehouse.model.AppVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {

    suspend fun checkUpdate(context: Context): AppVersion? {
        return try {
            val api = RetrofitClient.getApiService(context)
            val version = withContext(Dispatchers.IO) { api.getAppVersion() }
            if (version.versionCode > BuildConfig.VERSION_CODE) version else null
        } catch (e: Exception) {
            null
        }
    }

    fun showUpdateDialog(context: android.app.Activity, version: AppVersion) {
        AlertDialog.Builder(context)
            .setTitle("앱 업데이트")
            .setMessage("새 버전이 있습니다\n\n현재: v${BuildConfig.VERSION_NAME}\n최신: v${version.versionName}\n\n${version.releaseNotes}")
            .setPositiveButton("지금 업데이트") { _, _ -> downloadAndInstall(context, version) }
            .apply { if (!version.forceUpdate) setNegativeButton("나중에", null) else setCancelable(false) }
            .show()
    }

    private fun downloadAndInstall(context: android.app.Activity, version: AppVersion) {
        val baseUrl = RetrofitClient.getBaseUrl(context)
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val url = if (version.downloadUrl.startsWith("http")) version.downloadUrl
                  else "${normalized}${version.downloadUrl.removePrefix("/")}"

        val destFile = File(context.cacheDir, "update.apk")
        val density = context.resources.displayMetrics.density

        // 다운로드 진행 다이얼로그
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val pad = (24 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val statusText = TextView(context).apply {
            text = "v${version.versionName} 다운로드 준비 중..."
            textSize = 15f
            gravity = Gravity.CENTER
        }
        val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (16 * density).toInt() }
            max = 100
            progress = 0
        }
        val percentText = TextView(context).apply {
            text = "0%"
            textSize = 20f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (8 * density).toInt() }
        }

        layout.addView(statusText)
        layout.addView(progressBar)
        layout.addView(percentText)

        val dialog = AlertDialog.Builder(context)
            .setTitle("업데이트 다운로드")
            .setView(layout)
            .setCancelable(false)
            .create()
        dialog.show()

        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                if (destFile.exists()) destFile.delete()

                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 30000
                connection.connect()
                val fileLength = connection.contentLength

                withContext(Dispatchers.Main) {
                    statusText.text = "v${version.versionName} 다운로드 중..."
                }

                connection.inputStream.use { input ->
                    destFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var downloaded = 0L
                        var read: Int

                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (fileLength > 0) {
                                val percent = (downloaded * 100 / fileLength).toInt()
                                withContext(Dispatchers.Main) {
                                    progressBar.progress = percent
                                    percentText.text = "${percent}%"
                                }
                            }
                        }
                    }
                }

                // 최소 1초 표시 (너무 빨리 사라지지 않게)
                withContext(Dispatchers.Main) {
                    if (dialog.isShowing) {
                        progressBar.progress = 100
                        percentText.text = "100%"
                        statusText.text = "다운로드 완료! 설치 진행 중..."
                    }
                }
                delay(1000)

                withContext(Dispatchers.Main) {
                    if (dialog.isShowing) dialog.dismiss()
                    installApk(context, destFile)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (dialog.isShowing) dialog.dismiss()
                    Toast.makeText(context, "다운로드 실패: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }.also { context.startActivity(it) }
    }
}
