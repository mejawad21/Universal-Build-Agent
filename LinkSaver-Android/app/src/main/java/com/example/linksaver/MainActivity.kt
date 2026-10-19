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
    private val bg = Color.rgb(8, 14, 13)
    private val panel = Color.rgb(20, 31, 28)
    private val accent = Color.rgb(190, 239, 117)
    private val secondary = Color.rgb(159, 176, 168)
    private lateinit var urlInput: EditText
    private lateinit var qualitySpinner: Spinner
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
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = if (android.os.Build.VERSION.SDK_INT >= 30) insets.getInsets(WindowInsets.Type.systemBars()) else null
            view.setPadding(
                18.dp() + (bars?.left ?: insets.systemWindowInsetLeft),
                8.dp() + (bars?.top ?: insets.systemWindowInsetTop),
                18.dp() + (bars?.right ?: insets.systemWindowInsetRight),
                8.dp() + (bars?.bottom ?: insets.systemWindowInsetBottom)
            )
            insets
        }

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8.dp(), 0, 18.dp())
        }

        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val mark = TextView(this).apply {
            text = "L"
            textSize = 23f
            setTypeface(null, Typeface.BOLD)
            setTextColor(bg)
            gravity = android.view.Gravity.CENTER
            background = shape(accent, 14.dp())
        }
        brandRow.addView(mark, LinearLayout.LayoutParams(46.dp(), 46.dp()))
        val brandCopy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp(), 0, 0, 0)
        }
        brandCopy.addView(TextView(this).apply {
            text = "LINKSAVER"
            textSize = 17f
            letterSpacing = 0.11f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        })
        brandCopy.addView(label("VIDEO DOWNLOADER", 10, secondary))
        brandRow.addView(brandCopy, LinearLayout.LayoutParams(0, -2, 1f))
        val premium = TextView(this).apply {
            text = "PLUS"
            textSize = 10f
            letterSpacing = 0.08f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accent)
            setPadding(12.dp(), 7.dp(), 12.dp(), 7.dp())
            background = shape(Color.rgb(30, 45, 35), 24.dp())
        }
        brandRow.addView(premium)
        page.addView(brandRow)

        page.addView(label("Your media,\none tap away.", 30, Color.WHITE).apply {
            setTypeface(null, Typeface.BOLD)
            setLineSpacing(0f, 1.0f)
        }, margin(top = 25.dp()))
        page.addView(label("Save videos and audio from links you share or paste.", 14, secondary), margin(top = 4.dp()))

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 17.dp(), 16.dp(), 16.dp())
            background = shape(panel, 22.dp())
        }
        page.addView(card, margin(top = 22.dp()))
        card.addView(label("VIDEO OR SOCIAL LINK", 11, accent).apply {
            letterSpacing = 0.08f
            setTypeface(null, Typeface.BOLD)
        })
        urlInput = EditText(this).apply {
            hint = "Paste a link to get started"
            setHintTextColor(Color.rgb(128, 144, 156))
            setTextColor(Color.WHITE)
            textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
            setPadding(14.dp(), 13.dp(), 14.dp(), 13.dp())
            background = shape(Color.rgb(12, 22, 20), 14.dp())
            backgroundTintList = null
        }
        card.addView(urlInput, margin(top = 10.dp()))
        card.addView(label("Choose your preferred video quality", 13, secondary), margin(top = 16.dp()))
        qualitySpinner = Spinner(this).apply {
            val choices = arrayOf("360p  ·  Most compatible", "480p", "720p  ·  HD", "1080p  ·  Full HD")
            val qualityAdapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, choices)
            qualityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            adapter = qualityAdapter
            setPadding(10.dp(), 2.dp(), 10.dp(), 2.dp())
            background = shape(Color.rgb(12, 22, 20), 14.dp())
            setSelection(prefs.getInt("quality_index", 0).coerceIn(0, 3))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    prefs.edit().putInt("quality_index", position).apply()
                }
            }
        }
        card.addView(qualitySpinner, margin(top = 8.dp()))

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        card.addView(actions, margin(top = 15.dp()))
        actions.addView(actionButton("Paste link", false) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            val value = if (clip != null && clip.itemCount > 0) clip.itemAt(0).coerceToText(this).toString() else ""
            urlInput.setText(value)
            urlInput.setSelection(urlInput.length())
        }, LinearLayout.LayoutParams(0, 50.dp(), 1f))
        actions.addView(actionButton("Download  ↓", true) { startDownload() },
            LinearLayout.LayoutParams(0, 50.dp(), 1.5f).apply { leftMargin = 10.dp() })

        val supported = label("YouTube  ·  Instagram  ·  TikTok  ·  X  ·  1000+ sites", 11, secondary).apply {
            gravity = android.view.Gravity.CENTER
        }
        page.addView(supported, margin(top = 12.dp()))

        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(12.dp(), 8.dp(), 12.dp(), 8.dp())
            background = shape(Color.rgb(17, 29, 25), 14.dp())
        }
        val statusDot = TextView(this).apply {
            text = "●"
            textSize = 10f
            setTextColor(accent)
            setPadding(0, 0, 8.dp(), 0)
        }
        statusCard.addView(statusDot)
        statusText = label("Preparing your download engine…", 12, secondary).apply { setPadding(0, 4.dp(), 0, 4.dp()) }
        statusCard.addView(statusText, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(statusCard, margin(top = 17.dp()))

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        page.addView(heading, margin(top = 26.dp()))
        val headingCopy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        headingCopy.addView(label("Your downloads", 21, Color.WHITE).apply { setTypeface(null, Typeface.BOLD) })
        headingCopy.addView(label("Recent activity", 12, secondary))
        heading.addView(headingCopy, LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(actionButton("Refresh", false) { renderDownloads() })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(list, margin(top = 12.dp()))
        page.addView(actionButton("Open Downloads folder", false) {
            try { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) }
            catch (_: Exception) { statusText.text = "Open the Files app and select Downloads." }
        }, margin(top = 8.dp()))

        val scroll = ScrollView(this).apply { isFillViewport = false; clipToPadding = false }
        scroll.addView(page)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        receiveSharedUrl(intent)
        renderDownloads()

        Thread {
            try {
                YoutubeDL.getInstance().init(applicationContext)
                FFmpeg.getInstance().init(applicationContext)
                runOnUiThread { statusText.text = "Updating video engine…" }
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(applicationContext, UpdateChannel.NIGHTLY)
                } catch (_: Exception) {
                    runOnUiThread { statusText.text = "Using the bundled video engine." }
                }
                extractorReady = true
                runOnUiThread {
                    if (!extractorBusy) statusText.text = "Ready to download. Some posts may require public access."
                }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "Video engine unavailable: " + (e.message ?: "Initialization failed") }
            }
        }.start()
    }

    override fun onNewIntent(intent: Intent) { {
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
            downloadSocial(raw, intArrayOf(360, 480, 720, 1080)[qualitySpinner.selectedItemPosition.coerceIn(0, 3)], true)
            return
        }
        val socialDomains = listOf(
            "instagram.com", "facebook.com", "fb.watch", "tiktok.com", "twitter.com",
            "x.com", "reddit.com", "redd.it", "vimeo.com", "dailymotion.com",
            "pinterest.com", "pin.it", "threads.net", "twitch.tv", "kick.com",
            "snapchat.com", "soundcloud.com", "bilibili.com"
        )
        if (socialDomains.any { host == it || host.endsWith(".$it") }) {
            downloadSocial(raw, intArrayOf(360, 480, 720, 1080)[qualitySpinner.selectedItemPosition.coerceIn(0, 3)], false)
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


    private fun downloadSocial(url: String, quality: Int, isYouTube: Boolean) {
        if (!extractorReady) {
            statusText.text = "Video engine is preparing. Try again in a moment."
            return
        }
        if (extractorBusy) {
            statusText.text = "A video is already downloading."
            return
        }
        extractorBusy = true
        statusText.text = "Connecting to video platform…"
        Thread {
            try {
                val directory = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "social")
                if (!directory.exists()) directory.mkdirs()
                val started = System.currentTimeMillis()
                val request = YoutubeDLRequest(url)
                request.addOption("--no-playlist")
                request.addOption("--no-update")
                if (isYouTube) request.addOption("--extractor-args", "youtube:player_client=android")
                request.addOption("--no-overwrites")
                val format = if (isYouTube) {
                    if (quality == 360) "18/best[height<=360]/best[height<=480]"
                    else "best[height<=$quality]/bestvideo[height<=$quality]+bestaudio/18"
                } else {
                    "best[height<=$quality]/bestvideo[height<=$quality]+bestaudio/best"
                }
                request.addOption("-f", format)
                request.addOption("--merge-output-format", "mp4")
                request.addOption("-o", File(directory, "%(title).80s_%(id)s.%(ext)s").absolutePath)
                YoutubeDL.getInstance().execute(request, null, { progress, eta, _ ->
                    runOnUiThread {
                        statusText.text = "Downloading (up to ${quality}p): " + progress.toInt() + "% • ETA " + eta + "s"
                    }
                })
                val outputs = directory.listFiles()?.filter {
                    it.isFile && it.lastModified() >= started - 3000 &&
                        !it.name.endsWith(".part") && !it.name.endsWith(".ytdl")
                }.orEmpty()
                if (outputs.isEmpty()) throw IllegalStateException("No completed video file found")
                val latest = outputs.maxByOrNull { it.lastModified() }!!
                val mime = when (latest.extension.lowercase()) {
                    "webm" -> "video/webm"
                    "m4a" -> "audio/mp4"
                    "mp3" -> "audio/mpeg"
                    "mov" -> "video/quicktime"
                    else -> "video/mp4"
                }
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
                runOnUiThread { statusText.text = if (e.message?.contains("403") == true) "This platform refused the stream (403). The post may be restricted." else "Video download failed: " + (e.message ?: "Video unavailable") }
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
        setTypeface(null, Typeface.BOLD)
        setTextColor(if (primary) Color.rgb(12, 21, 15) else Color.WHITE)
        backgroundTintList = null
        background = shape(if (primary) accent else Color.rgb(37, 50, 46), 15.dp())
        elevation = if (primary) 3.dp().toFloat() else 0f
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
