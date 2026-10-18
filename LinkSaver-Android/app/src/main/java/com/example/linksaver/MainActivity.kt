package com.example.linksaver

import android.app.Activity
import android.app.DownloadManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.WindowInsets
import android.widget.*
import java.net.URLConnection
import java.io.File
import java.io.FileInputStream
import android.content.ContentValues
import android.provider.MediaStore
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL.UpdateChannel

class MainActivity : Activity() {
    private val bg = Color.rgb(13, 17, 29)
    private val panel = Color.rgb(26, 33, 50)
    private val accent = Color.rgb(107, 143, 255)
    private val secondary = Color.rgb(174, 186, 207)
    private lateinit var urlInput: EditText
    private lateinit var statusText: TextView
    private lateinit var list: LinearLayout
    private lateinit var manager: DownloadManager
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("linksaver_v2", MODE_PRIVATE) }
    private var visible = false
    @Volatile private var extractorReady = false
    @Volatile private var extractorBusy = false
    private val refresh = object : Runnable {
        override fun run() {
            if (!visible) return
            renderDownloads()
            handler.postDelayed(this, 1500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        Thread {
            try {
                YoutubeDL.getInstance().init(applicationContext)
                FFmpeg.getInstance().init(applicationContext)
                runOnUiThread { statusText.text = "Updating YouTube download engine…" }
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(applicationContext, UpdateChannel.NIGHTLY)
                } catch (updateError: Exception) {
                    runOnUiThread { statusText.text = "Engine update unavailable. Using bundled version." }
                }
                extractorReady = true
                runOnUiThread { if (!extractorBusy) statusText.text = "Ready for YouTube links and direct media files." }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "Video engine unavailable: " + (e.message ?: "Initialization failed") }
            }
        }.start()
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(20.dp(), 16.dp(), 20.dp(), 12.dp())
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = if (android.os.Build.VERSION.SDK_INT >= 30) insets.getInsets(WindowInsets.Type.systemBars()) else null
            view.setPadding(20.dp() + (bars?.left ?: insets.systemWindowInsetLeft), 16.dp() + (bars?.top ?: insets.systemWindowInsetTop), 20.dp() + (bars?.right ?: insets.systemWindowInsetRight), 12.dp() + (bars?.bottom ?: insets.systemWindowInsetBottom))
            insets
        }
        val header = TextView(this).apply {
            text = "LinkSaver 3.1"
            textSize = 29f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }
        root.addView(header)
        root.addView(label("Video & audio download manager", 14, secondary), margin(top = 2.dp()))
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
            background = shape(panel, 18.dp())
        }
        root.addView(card, margin(top = 24.dp()))
        card.addView(label("Paste a YouTube or video link", 16, Color.WHITE).apply { setTypeface(null, Typeface.BOLD) })
        urlInput = EditText(this).apply {
            hint = "https://example.com/video.mp4"
            setHintTextColor(secondary)
            setTextColor(Color.WHITE)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
            backgroundTintList = ColorStateList.valueOf(accent)
        }
        card.addView(urlInput, margin(top = 8.dp()))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        card.addView(actions, margin(top = 10.dp()))
        actions.addView(actionButton("Paste", false) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            val value = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(this).toString() else ""
            urlInput.setText(value)
            urlInput.setSelection(urlInput.length())
        }, LinearLayout.LayoutParams(0, 48.dp(), 1f))
        actions.addView(actionButton("Download", true) { startDownload() },
            LinearLayout.LayoutParams(0, 48.dp(), 1.5f).apply { leftMargin = 10.dp() })
        statusText = label("Preparing video download engine…", 13, secondary)
        root.addView(statusText, margin(top = 15.dp()))
        val heading = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        root.addView(heading, margin(top = 25.dp()))
        heading.addView(label("Downloads", 21, Color.WHITE).apply { setTypeface(null, Typeface.BOLD) },
            LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(actionButton("Refresh", false) { renderDownloads() })
        val scroll = ScrollView(this).apply { isFillViewport = false }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 10.dp() })
        root.addView(actionButton("Open Downloads", false) {
            try { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) }
            catch (_: Exception) { statusText.text = "Open the Files app and select Downloads." }
        }, margin(top = 8.dp()))
        setContentView(root)
        receiveSharedUrl(intent)
        renderDownloads()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveSharedUrl(intent)
    }

    private fun receiveSharedUrl(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val value = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            if (value.isNotBlank()) urlInput.setText(value)
        }
    }

    private fun startDownload() {
        val raw = urlInput.text.toString().trim()
        val uri = try { Uri.parse(raw) } catch (_: Exception) { null }
        if (uri == null || uri.scheme?.lowercase() != "https" || uri.host.isNullOrBlank()) {
            statusText.text = "Please enter a valid HTTPS media URL."
            return
        }
        val host = uri.host!!.lowercase()
        if (host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be") {
            downloadYouTube(raw)
            return
        }
        val filePart = uri.lastPathSegment.orEmpty()
        val extension = filePart.substringAfterLast('.', "").lowercase()
        val supported = setOf("mp4", "webm", "m4v", "mov", "mp3", "m4a")
        if (extension !in supported) {
            statusText.text = "This looks like a webpage, not a direct video file. Supported: MP4, WebM, MOV, MP3, M4A."
            return
        }
        val safeName = filePart.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
            .ifBlank { "media.$extension" }
        val finalName = "LinkSaver_" + System.currentTimeMillis() + "_" + safeName
        try {
            val request = DownloadManager.Request(uri)
                .setTitle(safeName)
                .setDescription("LinkSaver download")
                .setMimeType(URLConnection.guessContentTypeFromName(safeName) ?: "application/octet-stream")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, finalName)
                .setAllowedOverMetered(true)
            val id = manager.enqueue(request)
            val ids = savedIds().toMutableList()
            ids.add(0, id)
            prefs.edit().putString("ids", ids.distinct().take(30).joinToString(","))
                .putString("name_$id", safeName).apply()
            statusText.text = "Download started. Progress appears below."
            renderDownloads()
        } catch (e: Exception) {
            statusText.text = "Download could not start: " + (e.message ?: "Unknown error")
        }
    }


    private fun downloadYouTube(url: String) {
        if (!extractorReady) {
            statusText.text = "Video engine is preparing. Try again in a moment."
            return
        }
        if (extractorBusy) {
            statusText.text = "A video is already downloading."
            return
        }
        extractorBusy = true
        statusText.text = "Connecting to YouTube…"
        Thread {
            try {
                val directory = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "youtube")
                if (!directory.exists()) directory.mkdirs()
                val started = System.currentTimeMillis()
                val request = YoutubeDLRequest(url)
                request.addOption("--no-playlist")
                request.addOption("--no-update")
                request.addOption("--extractor-args", "youtube:player_client=android_vr,web")
                request.addOption("--no-overwrites")
                request.addOption("-f", "bestvideo[height<=720]+bestaudio/best[height<=720]/best")
                request.addOption("--merge-output-format", "mp4")
                request.addOption("-o", File(directory, "%(title).80s_%(id)s.%(ext)s").absolutePath)
                YoutubeDL.getInstance().execute(request, null, { progress, eta, _ ->
                    runOnUiThread {
                        statusText.text = "Downloading YouTube video: " + progress.toInt() + "% • ETA " + eta + "s"
                    }
                })
                val outputs = directory.listFiles()?.filter {
                    it.isFile && it.lastModified() >= started - 3000 &&
                        !it.name.endsWith(".part") && !it.name.endsWith(".ytdl")
                }.orEmpty()
                if (outputs.isEmpty()) throw IllegalStateException("No completed video file found")
                val latest = outputs.maxByOrNull { it.lastModified() }!!
                val mime = if (latest.extension.equals("webm", true)) "video/webm" else "video/mp4"
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, latest.name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/LinkSaver")
                }
                val destination = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Cannot save to Downloads")
                try {
                    contentResolver.openOutputStream(destination)?.use { output ->
                        FileInputStream(latest).use { input -> input.copyTo(output) }
                    } ?: throw IllegalStateException("Cannot write video")
                } catch (e: Exception) {
                    contentResolver.delete(destination, null, null)
                    throw e
                }
                latest.delete()
                runOnUiThread { statusText.text = "Saved to Downloads/LinkSaver: " + latest.name }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = if (e.message?.contains("403") == true) "YouTube refused this stream (403). Try another video or update the engine." else "YouTube download failed: " + (e.message ?: "Video unavailable") }
            } finally {
                extractorBusy = false
            }
        }.start()
    }

    private fun savedIds(): List<Long> = prefs.getString("ids", "").orEmpty()
        .split(",").mapNotNull { it.toLongOrNull() }

    private fun renderDownloads() {
        list.removeAllViews()
        val ids = savedIds()
        if (ids.isEmpty()) {
            list.addView(label("No downloads yet. Paste a direct media URL above.", 14, secondary))
            return
        }
        ids.forEach { id ->
            val cursor = try { manager.query(DownloadManager.Query().setFilterById(id)) }
                catch (_: Exception) { null }
            var state = "Not found"
            var progress = 0
            var done = false
            var available = false
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    available = true
                    val st = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val current = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    progress = if (total > 0) ((current * 100L) / total).toInt().coerceIn(0, 100) else 0
                    done = st == DownloadManager.STATUS_SUCCESSFUL
                    state = when (st) {
                        DownloadManager.STATUS_SUCCESSFUL -> "Completed"
                        DownloadManager.STATUS_FAILED -> "Failed"
                        DownloadManager.STATUS_PAUSED -> "Paused"
                        DownloadManager.STATUS_RUNNING -> if (total > 0) "Downloading $progress%" else "Downloading"
                        else -> "Queued"
                    }
                }
            }
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
                background = shape(panel, 14.dp())
            }
            list.addView(tile, margin(bottom = 10.dp()))
            tile.addView(label(prefs.getString("name_$id", "Download").orEmpty(), 15, Color.WHITE))
            tile.addView(label(state, 12, if (done) Color.rgb(108, 221, 158) else secondary))
            if (available && !done) {
                tile.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100
                    this.progress = progress
                    progressTintList = ColorStateList.valueOf(accent)
                }, margin(top = 6.dp()))
            }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            tile.addView(row)
            if (done) {
                row.addView(actionButton("Open", false) {
                    try {
                        val file = manager.getUriForDownloadedFile(id)
                        startActivity(Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(file, manager.getMimeTypeForDownloadedFile(id))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        })
                    } catch (_: Exception) { statusText.text = "Open the file from Downloads." }
                })
            }
            row.addView(actionButton("Remove", false) {
                if (!done && available) manager.remove(id)
                val remaining = savedIds().filter { it != id }
                prefs.edit().putString("ids", remaining.joinToString(",")).remove("name_$id").apply()
                renderDownloads()
            })
        }
    }

    override fun onResume() {
        super.onResume()
        visible = true
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }

    override fun onPause() {
        visible = false
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun label(value: String, size: Int, color: Int) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        setPadding(2.dp(), 5.dp(), 2.dp(), 5.dp())
    }

    private fun actionButton(title: String, primary: Boolean, onTap: () -> Unit) = Button(this).apply {
        text = title
        textSize = 13f
        isAllCaps = false
        setTextColor(Color.WHITE)
        backgroundTintList = ColorStateList.valueOf(if (primary) Color.rgb(62, 99, 211) else Color.rgb(58, 68, 91))
        setOnClickListener { onTap() }
    }

    private fun shape(color: Int, radius: Int) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun margin(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(-1, -2).apply { topMargin = top; bottomMargin = bottom }

    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()
}
