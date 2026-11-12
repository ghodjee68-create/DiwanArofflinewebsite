package com.diwanoffline

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var web: WebView
    private lateinit var download: Button
    private val startUrl = "https://www.diwanalarabia.com/Default.aspx"
    private val offlineDir by lazy { File(filesDir, "diwan_offline") }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 8)
            layoutParams = ViewGroup.LayoutParams(-1, -1)
        }
        val title = TextView(this).apply {
            text = "ديوان اللغة العربية — نسخة بلا إنترنت"
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(4, 8, 4, 14)
        }
        status = TextView(this).apply {
            text = "حمّل محتوى الموقع أولاً أثناء الاتصال بالإنترنت."
            textSize = 14f
            setPadding(4, 4, 4, 8)
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        download = Button(this).apply {
            text = "تنزيل الموقع"
            setOnClickListener { startDownload() }
        }
        val open = Button(this).apply {
            text = "فتح النسخة المحفوظة"
            setOnClickListener { openOffline() }
        }
        row.addView(download, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(open, LinearLayout.LayoutParams(0, -2, 1f))
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            webViewClient = WebViewClient()
        }
        layout.addView(title)
        layout.addView(row)
        layout.addView(status)
        layout.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(layout)
        if (File(offlineDir, "pages/index.html").exists()) openOffline()
        else web.loadUrl(startUrl)
    }

    private fun startDownload() {
        download.isEnabled = false
        status.text = "جارٍ فحص الموقع وتنزيل الصفحات والملفات…"
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    SiteCrawler(offlineDir) { message -> runOnUiThread { status.text = message } }
                        .crawl(startUrl, maxPages = 10000)
                }
                status.text = "اكتمل التنزيل: ${result.pages} صفحة، ${result.assets} ملف. يمكنك الآن فصل الإنترنت."
                openOffline()
            } catch (e: Exception) {
                status.text = "تعذّر إكمال التنزيل: ${e.message ?: "خطأ غير معروف"}"
            } finally {
                download.isEnabled = true
            }
        }
    }

    private fun openOffline() {
        val home = File(offlineDir, "pages/index.html")
        if (home.exists()) web.loadUrl(home.toURI().toString())
        else status.text = "لا توجد نسخة محفوظة بعد. اتصل بالإنترنت واضغط «تنزيل الموقع»."
    }

    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
