// MainActivity.kt
package com.rtechon.ismotbrawser
import android.Manifest
import android.app.AlertDialog
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlInput: EditText
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var loadingBar: ProgressBar
    private lateinit var tabButton: ImageButton
    private lateinit var menuButton: ImageButton


    private val homeUrl = "https://www.google.com"
    private val bookmarks = mutableListOf<String>()
    private val history = mutableListOf<String>()
    private val tabs = mutableListOf<String>()
    private var currentTab = 0
    private var darkMode = false
    private var erudaEnabled = false

    private val PREFS_NAME = "BrowserPrefs"
    private val BOOKMARKS_KEY = "bookmarks"
    private val TABS_KEY = "tabs"
    private val CHANNEL_ID = "download_channel"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 🔐 Request permissions if needed (for older Androids)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ), 100
                )
            }
        }

        // UI Bindings
        webView = findViewById(R.id.webView)
        urlInput = findViewById(R.id.urlInput)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        loadingBar = findViewById(R.id.loadingBar)
        tabButton = findViewById(R.id.tabButton)
        menuButton = findViewById(R.id.menuButton)

        createNotificationChannel()
        loadBookmarksFromPrefs()
        loadTabsFromPrefs()

        if (tabs.isEmpty()) tabs.add(homeUrl)

        // WebView Configuration
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            allowFileAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()
                return when {
                    url.startsWith("intent://") || url.startsWith("market://") -> {
                        try {
                            val intent = if (url.startsWith("intent://")) {
                                Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                            } else {
                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            }
                            if (intent.resolveActivity(packageManager) != null) {
                                startActivity(intent)
                            } else {
                                val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                                if (!fallbackUrl.isNullOrEmpty()) {
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)))
                                } else {
                                    Toast.makeText(this@MainActivity, "App not found for this intent.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(this@MainActivity, "Failed to open link", Toast.LENGTH_SHORT).show()
                        }
                        true
                    }
                    else -> false
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                loadingBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                loadingBar.visibility = View.GONE
                url?.let {
                    if (!history.contains(it)) history.add(it)
                    urlInput.setText(it)
                    tabs[currentTab] = it
                    saveTabsToPrefs()
                }
                if (erudaEnabled) enableEruda()
            }
        }

        webView.webChromeClient = WebChromeClient()

        webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            val filename = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url))
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            showDownloadNotification(filename)
        }

        swipeRefresh.setOnRefreshListener {
            webView.reload()
            swipeRefresh.isRefreshing = false
        }

        // ✅ Start Ktor Server
        com.rtechon.ismotbrawser.server.KtorServer.start(8080)

        val customHtml = """
    <!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <title>ISMOT Browser Welcome</title>
  <style>
    body {
      margin: 0;
      padding: 20px;
      font-family: 'Segoe UI', sans-serif;
      background: linear-gradient(to right, #e3f2fd, #fce4ec);
      color: #333;
    }

    .container {
      max-width: 800px;
      margin: auto;
      background-color: #ffffffcc;
      border-radius: 12px;
      padding: 24px;
      box-shadow: 0 4px 10px rgba(0,0,0,0.1);
    }

    h1 {
      text-align: center;
      color: #0077cc;
    }

    p {
      font-size: 16px;
      line-height: 1.6;
    }

    .info-box, .usage-box, .collab-box {
      background: #f1f8e9;
      border-left: 6px solid #8bc34a;
      padding: 12px 18px;
      margin: 20px 0;
      border-radius: 8px;
    }

    ul {
      padding-left: 20px;
    }

    .button {
      display: inline-block;
      padding: 12px 24px;
      background-color: #1976d2;
      color: white;
      text-decoration: none;
      border-radius: 6px;
      margin-top: 20px;
      font-weight: bold;
      cursor: pointer;
    }

    .button:hover {
      background-color: #1565c0;
    }

    .social-icons {
      display: flex;
      justify-content: center;
      gap: 16px;
      margin-top: 24px;
    }

    .social-icons a {
      text-decoration: none;
      color: #1976d2;
      font-weight: bold;
      font-size: 14px;
    }

    footer {
      text-align: center;
      margin-top: 40px;
      font-size: 12px;
      color: #666;
    }
  </style>
</head>
<body>
  <div class="container">
    <h1>Welcome to ISMOT Brawser</h1>
    <p>
      ISMOT Brawser is a lightweight, secure, and fast Android browser tailored for advanced users, ethical hackers, and developers.
    </p>

    <div class="info-box">
      <strong>Key Features:</strong>
      <ul>
        <li>Built-in Ktor Local Web Server (8080)</li>
        <li>Host & Serve Static HTML (e.g., <code>index.html</code>)</li>
        <li>Bookmark and Tab Management</li>
        <li>Built-in Developer Tools via Eruda</li>
        <li>Custom Commands (://about, ://devtools, ://hackbar)</li>
      </ul>
    </div>

    <div class="usage-box">
      <strong>Usage:</strong>
      <p>
        The browser is ideal for loading local HTML apps, testing frontend code, viewing web projects offline, or hosting simple content using Ktor.
      </p>
    </div>

    <div class="collab-box">
      <strong>Open for Collaborators:</strong>
      <p>
        We're actively improving ISMOT Brawser. If you're a developer, UI/UX designer, or tester, you're welcome to collaborate and contribute on GitHub.
      </p>
    </div>

    <div onclick="alert('Launching ISMOT Brawser Features...')" class="button">
      Explore Features
    </div>

    <div class="social-icons">
      <a href="https://facebook.com/CoderSigma" target="_blank">Facebook</a>
      <a href="https://github.com/RtechonDev" target="_blank">GitHub</a>
      <a href="mailto:robbyroda.00@gmail.com">Email</a>
      <a href="https://x.com/SoftwareDev2002" target="_blank">X.com</a>
    </div>

    <footer>
      &copy; 2025 ISMOT Brawser by RTechon. All rights reserved.
    </footer>
  </div>
</body>
</html>


""".trimIndent()

        webView.loadDataWithBaseURL(null, customHtml, "text/html", "UTF-8", null)

        menuButton.setOnClickListener {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_navigation, null)
            val dialog = AlertDialog.Builder(this).setView(dialogView).create()

            dialogView.findViewById<Button>(R.id.navHome).setOnClickListener {
                webView.loadUrl(homeUrl)
                dialog.dismiss()
            }

            dialogView.findViewById<Button>(R.id.navAddBookmark).setOnClickListener {
                val currentUrl = webView.url ?: return@setOnClickListener
                if (!bookmarks.contains(currentUrl)) {
                    bookmarks.add(currentUrl)
                    saveBookmarksToPrefs()
                    Toast.makeText(this, "Bookmarked: $currentUrl", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Already bookmarked.", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }

            dialogView.findViewById<Button>(R.id.navBookmarks).setOnClickListener {
                showBookmarkDialog()
                dialog.dismiss()
            }

            dialogView.findViewById<Button>(R.id.navHistory).setOnClickListener {
                val message = if (history.isEmpty()) "No history yet." else history.joinToString("\n")
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                dialog.dismiss()
            }

            dialogView.findViewById<Button>(R.id.navTheme).setOnClickListener {
                dialog.dismiss()
                showThemeDialog()
            }

            dialogView.findViewById<Button>(R.id.navDownloads).setOnClickListener {
                startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
                dialog.dismiss()
            }

            dialog.show()
        }

        tabButton.setOnClickListener {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Open Tabs")

            val tabArray = tabs.toTypedArray()
            builder.setItems(tabArray) { dialog, which ->
                currentTab = which
                webView.loadUrl(tabs[currentTab])
                urlInput.setText(tabs[currentTab])
                saveTabsToPrefs()
                dialog.dismiss()
            }

            builder.setPositiveButton("New Tab") { dialog, _ ->
                tabs.add(homeUrl)
                currentTab = tabs.lastIndex
                webView.loadUrl(homeUrl)
                urlInput.setText(homeUrl)
                saveTabsToPrefs()
                dialog.dismiss()
            }

            builder.setNegativeButton("Close Current Tab") { dialog, _ ->
                if (tabs.size > 1) {
                    tabs.removeAt(currentTab)
                    currentTab = 0
                    webView.loadUrl(tabs[currentTab])
                    urlInput.setText(tabs[currentTab])
                    saveTabsToPrefs()
                } else {
                    Toast.makeText(this, "Cannot close the last tab.", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }

            builder.show()
        }

        urlInput.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                val input = urlInput.text.toString().trim()
                val urlToLoad = when {
                    input == "://about" -> {
                        showAboutDialog(); return@setOnKeyListener true
                    }
                    input == "://devtools" -> {
                        showErudaDevTools(); return@setOnKeyListener true
                    }
                    input == "://hackbar" -> {
                        showHackbarDialog(); return@setOnKeyListener true
                    }
                    input == "://ktor" -> {
                        showKtorDialog()
                        return@setOnKeyListener true
                    }
                    input.startsWith("http") -> input
                    Regex("^[a-z0-9.-]+\\.[a-z]{2,}(/.*)?$").matches(input) -> "http://$input"
                    else -> "https://www.google.com/search?q=${Uri.encode(input)}"
                }
                webView.loadUrl(urlToLoad)
                tabs[currentTab] = urlToLoad
                saveTabsToPrefs()
                true
            } else false
        }

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val rootCause = throwable.cause ?: throwable
            rootCause.printStackTrace()
            runOnUiThread {
                Toast.makeText(this, "Crash: ${rootCause::class.java.simpleName}: ${rootCause.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showKtorDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_ktor, null)
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()

        // 🔁 Ensure /storage/emulated/0/ismot_brawser exists
        val rootDir = File(Environment.getExternalStorageDirectory(), "ismot_brawser")
        val directoryCreated = if (!rootDir.exists()) rootDir.mkdirs() else true

        if (directoryCreated) {
            Toast.makeText(this, "Directory ready at ${rootDir.absolutePath}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to create directory", Toast.LENGTH_SHORT).show()
        }

        // ✅ Ensure index.html exists
        val indexFile = File(rootDir, "index.html")
        if (!indexFile.exists()) {
            indexFile.writeText(
                """
            <html>
              <head><title>ISMOT Brawser</title></head>
              <body>
                <h2>Welcome to ISMOT Brawser</h2>
                <p>This is the default index.html</p>
              </body>
            </html>
            """.trimIndent()
            )
            Toast.makeText(this, "index.html created", Toast.LENGTH_SHORT).show()
        }

        // 📌 Display root directory path
        val rootTextView = view.findViewById<TextView>(R.id.rootDirectoryPath)
        rootTextView.text = "Root Path: ${rootDir.absolutePath}"

        // 📦 View bindings
        val startButton = view.findViewById<Button>(R.id.startKtorBtn)
        val stopButton = view.findViewById<Button>(R.id.stopKtorBtn)
        val customButton = view.findViewById<Button>(R.id.customButton)
        val customUrlInput = view.findViewById<EditText>(R.id.customUrlInput)
        val closeButton = view.findViewById<Button>(R.id.closeKtorDialogBtn)

        // ▶ Start Ktor Server
        startButton.setOnClickListener {
            com.rtechon.ismotbrawser.server.KtorServer.start(8080)
            webView.loadUrl("http://localhost:8080/index.html")
            Toast.makeText(this, "Ktor server started", Toast.LENGTH_SHORT).show()
        }

        // ⏹ Stop Ktor Server
        stopButton.setOnClickListener {
            com.rtechon.ismotbrawser.server.KtorServer.stop()
            Toast.makeText(this, "Ktor server stopped", Toast.LENGTH_SHORT).show()
        }

        // 🌐 Load custom URL
        customButton.setOnClickListener {
            val url = customUrlInput.text.toString().trim()
            if (url.isNotEmpty()) {
                webView.loadUrl(url)
                dialog.dismiss()
            }
        }

        // ❌ Close Dialog
        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    private fun showBookmarkDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_bookmarks, null)
        val bookmarkListLayout = dialogView.findViewById<LinearLayout>(R.id.bookmarkListLayout)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        bookmarkListLayout.removeAllViews()

        bookmarks.forEach { url ->
            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(16, 16, 16, 16)
            }

            val faviconView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(48, 48).apply { marginEnd = 16 }
            }

            Glide.with(this).load("https://www.google.com/s2/favicons?sz=64&domain_url=$url")
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(faviconView)

            val textView = TextView(this).apply {
                text = url
                textSize = 16f
                setTextColor(Color.BLACK)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    webView.loadUrl(url)
                    dialog.dismiss()
                }
            }

            val deleteIcon = ImageView(this).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener {
                    bookmarks.remove(url)
                    saveBookmarksToPrefs()
                    dialog.dismiss()
                    showBookmarkDialog()
                }
            }

            itemLayout.addView(faviconView)
            itemLayout.addView(textView)
            itemLayout.addView(deleteIcon)
            bookmarkListLayout.addView(itemLayout)
        }

        dialog.show()
    }

    private fun showThemeDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_theme_mode, null)
        val radioGroup = view.findViewById<RadioGroup>(R.id.themeRadioGroup)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("Apply") { _, _ ->
                when (radioGroup.checkedRadioButtonId) {
                    R.id.lightModeRadio -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                        webView.setBackgroundColor(Color.WHITE)
                    }
                    R.id.darkModeRadio -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                        webView.setBackgroundColor(Color.BLACK)
                    }
                    R.id.dimModeRadio -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                        webView.setBackgroundColor(Color.parseColor("#333747"))
                    }
                    R.id.slightDarkModeRadio -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                        webView.setBackgroundColor(Color.parseColor("#b8b8b8"))
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Download Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for completed downloads"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showDownloadNotification(filename: String) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download started")
            .setContentText("$filename is downloading...")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun saveBookmarksToPrefs() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(BOOKMARKS_KEY, bookmarks.toSet()).apply()
    }

    private fun loadBookmarksFromPrefs() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getStringSet(BOOKMARKS_KEY, emptySet())?.let {
            bookmarks.clear()
            bookmarks.addAll(it)
        }
    }

    private fun saveTabsToPrefs() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(TABS_KEY, tabs.toSet()).apply()
    }

    private fun loadTabsFromPrefs() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getStringSet(TABS_KEY, null)?.let {
            tabs.clear()
            tabs.addAll(it)
        }
    }

    private fun showAboutDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_about, null)
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("Close", null)
            .create()

        view.findViewById<ImageButton>(R.id.facebookIcon).setOnClickListener {
            dialog.dismiss()
            webView.loadUrl("https://facebook.com/CoderSigma")
        }

        view.findViewById<ImageButton>(R.id.xIcon).setOnClickListener {
            dialog.dismiss()
            webView.loadUrl("https://x.com/SoftwareDev2002")
        }

        view.findViewById<ImageButton>(R.id.emailIcon).setOnClickListener {
            dialog.dismiss()
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:robbyroda.00@gmail.com")
            }
            startActivity(intent)
        }

        view.findViewById<ImageButton>(R.id.githubIcon).setOnClickListener {
            dialog.dismiss()
            webView.loadUrl("https://github.com/RtechonDev")
        }

        dialog.show()
    }

    private fun showErudaDevTools() {
        erudaEnabled = true
        Toast.makeText(this, "DevTools enabled for all pages.", Toast.LENGTH_SHORT).show()
        enableEruda()
    }

    private fun enableEruda() {
        webView.evaluateJavascript(
            """
            (function () {
                var script = document.createElement('script');
                script.src = 'https://cdn.jsdelivr.net/npm/eruda';
                script.onload = function () { eruda.init(); };
                document.body.appendChild(script);
            })();
            """.trimIndent(), null
        )
    }

    private fun showHackbarDialog() {
        AlertDialog.Builder(this)
            .setTitle("Hackbar")
            .setMessage("This is a placeholder for Hackbar functionality.")
            .setPositiveButton("OK", null)
            .show()
    }
}
