package com.example.linksaver

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.InputType
import android.content.ActivityNotFoundException
import android.widget.*
import java.net.URLConnection

class MainActivity : Activity() {
    private lateinit var urlBox: EditText
    private lateinit var status: TextView
    private lateinit var downloads: LinearLayout
    private val prefs by lazy { getSharedPreferences("downloads", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(12, 14, 22)
        window.navigationBarColor = Color.rgb(12, 14, 22)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(12, 14, 22))
            setPadding(24.dp(), 28.dp(), 24.dp(), 18.dp())
        }
        val heading = text("LinkSaver", 28, Color.WHITE).apply { setTypeface(null, 1) }
        root.addView(heading)
        root.addView(text("Save downloadable videos from direct HTTPS media links", 14, Color.LTGRAY))
        urlBox = EditText(this).apply {
            hint = "Paste a video URL (https://...)"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(110, 130, 255)))
        }
        root.addView(urlBox, LinearLayout.LayoutParams(-1, 60.dp()).apply { topMargin = 22.dp() })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button("Paste") {
            val clip = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val value = clip.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
            urlBox.setText(value)
        }, LinearLayout.LayoutParams(0, 52.dp(), 1f))
        row.addView(button("Download") { download() }, LinearLayout.LayoutParams(0, 52.dp(), 2f).apply { leftMargin = 12.dp() })
        root.addView(row)
        status = text("Only download files you own or are authorized to save.", 13, Color.LTGRAY)
        root.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 16.dp() })
        root.addView(text("Recent downloads", 20, Color.WHITE).apply { setTypeface(null, 1) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 30.dp() })
        val scroll = ScrollView(this)
        downloads = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(downloads)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(button("Open Downloads folder") {
            try { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) }
            catch (_: Exception) { status.text = "Open your phone's Files app → Downloads." }
        })
        setContentView(root)
        val shared = if (intent?.action == Intent.ACTION_SEND) intent?.getStringExtra(Intent.EXTRA_TEXT) else null
        if (!shared.isNullOrBlank()) urlBox.setText(shared)
        renderHistory()
    }

    private fun download() {
        val input = urlBox.text.toString().trim()
        val uri = try { Uri.parse(input) } catch (_: Exception) { null }
        if (uri == null || uri.scheme != "https" || uri.host.isNullOrBlank()) {
            status.text = "Enter a valid HTTPS video file URL."
            return
        }
        val host = uri.host!!.lowercase()
        if (host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be" || host.endsWith(".youtu.be")) {
            status.text = "YouTube watch links aren't direct media files. Use YouTube's official offline feature for those videos."
            return
        }
        val path = uri.lastPathSegment.orEmpty().substringBefore('?')
        val extension = path.substringAfterLast('.', "").lowercase()
        if (extension !in setOf("mp4", "webm", "m4v", "mov", "mp3", "m4a")) {
            status.text = "This app accepts direct MP4, WebM, MOV, MP3 or M4A links. A webpage URL needs a supported provider integration."
            return
        }
        val name = path.replace(Regex("[^A-Za-z0-9._-]"), "_").take(90).ifBlank { "video.mp4" }
        try {
            val req = DownloadManager.Request(uri)
                .setTitle(name)
                .setDescription("Downloading with LinkSaver")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "LinkSaver_" + System.currentTimeMillis() + "_" + name)
                .setMimeType(URLConnection.guessContentTypeFromName(name) ?: "application/octet-stream")
            val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val id = manager.enqueue(req)
            prefs.edit().putString("recent", ("$name (ID $id)\n" + prefs.getString("recent", "").orEmpty()).take(3000)).apply()
            status.text = "Download queued. Check notification or Downloads folder."
            renderHistory()
        } catch (e: Exception) {
            status.text = "Unable to start download: ${e.message}"
        }
    }

    private fun renderHistory() {
        downloads.removeAllViews()
        val history = prefs.getString("recent", "").orEmpty()
        downloads.addView(text(if (history.isBlank()) "No downloads yet" else history, 14, Color.LTGRAY))
    }
    private fun text(s: String, size: Int, color: Int) = TextView(this).apply {
        text = s; textSize = size.toFloat(); setTextColor(color); setPadding(0, 8.dp(), 0, 8.dp())
    }
    private fun button(s: String, click: () -> Unit) = Button(this).apply {
        text = s; setOnClickListener { click() }; isAllCaps = false
    }
    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()
}
