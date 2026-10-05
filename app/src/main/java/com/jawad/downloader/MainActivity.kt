package com.jawad.downloader

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.URLUtil
import android.widget.*
import android.view.ViewGroup
import android.view.Gravity

class MainActivity : android.app.Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 80, 48, 32)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val title = TextView(this).apply {
            text = "Jawad Downloader"
            textSize = 28f
            gravity = Gravity.CENTER
        }
        val info = TextView(this).apply {
            text = "Download direct/public media files you own or are authorized to save."
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 40)
        }
        val input = EditText(this).apply {
            hint = "Paste direct media URL (https://...)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        val button = Button(this).apply { text = "Download" }
        val status = TextView(this).apply {
            text = "Ready"
            setPadding(0, 28, 0, 0)
        }

        button.setOnClickListener {
            val raw = input.text.toString().trim()
            val uri = runCatching { Uri.parse(raw) }.getOrNull()
            if (uri == null || uri.scheme != "https" || uri.host.isNullOrBlank()) {
                status.text = "Please enter a valid HTTPS direct media URL."
                return@setOnClickListener
            }
            try {
                val name = URLUtil.guessFileName(raw, null, null)
                val req = DownloadManager.Request(uri)
                    .setTitle(name)
                    .setDescription("Downloading media")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(false)
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(req)
                status.text = "Download started. Check your Downloads folder."
            } catch (e: Exception) {
                status.text = "Could not start download: " + (e.message ?: "unknown error")
            }
        }

        root.addView(title, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(info, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(input, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(button, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(status, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
    }
}
