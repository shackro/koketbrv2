package com.kokwet.voicecircle

import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var siteInput: EditText
    private lateinit var permissionStatus: TextView
    private var pendingChatUrl: String? = null
    private val preferences by lazy { getSharedPreferences(PREFERENCES, MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
        handleCompanionLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCompanionLink(intent)
    }

    override fun onResume() {
        super.onResume()
        if (::permissionStatus.isInitialized) updatePermissionStatus()
    }

    private fun render() {
        val padding = dp(24)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(Color.rgb(253, 252, 250))
        }
        val page = ScrollView(this).apply {
            addView(content, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }

        content.addView(TextView(this).apply {
            text = "Kokwet Voice Circle"
            textSize = 28f
            setTextColor(Color.rgb(36, 18, 6))
        })
        content.addView(TextView(this).apply {
            text = "Keep the live chat in a movable window while you use TikTok or another app."
            textSize = 15f
            setTextColor(Color.rgb(90, 75, 63))
            setPadding(0, dp(12), 0, dp(20))
        })
        content.addView(TextView(this).apply {
            text = "Kokwet website address"
            textSize = 13f
            setTextColor(Color.rgb(36, 18, 6))
        })
        siteInput = EditText(this).apply {
            hint = "https://your-kokwet-site.com"
            textSize = 14f
            setSingleLine(true)
            setText(preferences.getString(SITE_URL, ""))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        content.addView(siteInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(8) })

        permissionStatus = TextView(this).apply {
            textSize = 13f
            setPadding(0, dp(16), 0, dp(12))
        }
        content.addView(permissionStatus)
        content.addView(Button(this).apply {
            text = "Allow floating window"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName"),
                    ))
                } else {
                    updatePermissionStatus()
                }
            }
        })
        content.addView(Button(this).apply {
            text = "Start floating chat"
            setOnClickListener { startFloatingChat() }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(8) })
        content.addView(Button(this).apply {
            text = "Stop floating chat"
            setOnClickListener { stopService(Intent(this@MainActivity, FloatingChatService::class.java)) }
        })
        content.addView(TextView(this).apply {
            text = "The overlay stays visible until you close it or stop the service. Android requires you to approve overlay access. This app does not monitor which apps you open."
            textSize = 12f
            setTextColor(Color.rgb(110, 97, 84))
            setPadding(0, dp(20), 0, 0)
        })

        setContentView(page)
        updatePermissionStatus()
    }

    private fun startFloatingChat() {
        val siteOrigin = normalizeSiteOrigin(siteInput.text.toString())
        val chatUrl = siteOrigin?.let(::buildChatUrl)
        if (siteOrigin == null || chatUrl == null) {
            siteInput.error = "Enter a valid HTTPS address for the Kokwet website"
            return
        }
        siteInput.setText(siteOrigin)
        preferences.edit().putString(SITE_URL, siteOrigin).apply()

        if (!Settings.canDrawOverlays(this)) {
            updatePermissionStatus()
            startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ))
            return
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingChatUrl = chatUrl
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            return
        }
        launchChatService(chatUrl)
    }

    private fun handleCompanionLink(launchIntent: Intent?) {
        val link = launchIntent?.data ?: return
        if (link.scheme != "kokwet" || link.host != "floating-chat") return
        val siteOrigin = normalizeSiteOrigin(link.getQueryParameter("site_url") ?: return) ?: return
        siteInput.setText(siteOrigin)
        val savedSiteOrigin = preferences.getString(SITE_URL, null)
        if (savedSiteOrigin == siteOrigin && Settings.canDrawOverlays(this)) {
            startFloatingChat()
        } else {
            permissionStatus.text = "Confirm this Kokwet website address, then tap Start floating chat to continue."
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_AUDIO) return
        val siteUrl = pendingChatUrl ?: return
        pendingChatUrl = null
        if (grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Chat will start, but voice recording is unavailable without microphone access.", Toast.LENGTH_LONG).show()
        }
        launchChatService(siteUrl)
    }

    private fun launchChatService(siteUrl: String) {
        val serviceIntent = Intent(this, FloatingChatService::class.java)
            .putExtra(FloatingChatService.EXTRA_CHAT_URL, siteUrl)
        startForegroundService(serviceIntent)
        finish()
    }

    private fun updatePermissionStatus() {
        permissionStatus.text = if (Settings.canDrawOverlays(this)) {
            "Overlay access is enabled. Start chat before switching apps."
        } else {
            "Overlay access is required to show chat above TikTok and other apps."
        }
    }

    private fun normalizeSiteOrigin(input: String): String? {
        val uri = Uri.parse(input.trim())
        val host = uri.host ?: return null
        if (uri.scheme != "https" || host.isBlank() || uri.userInfo != null) return null
        if (uri.port != -1 && uri.port !in 1..65535) return null
        val normalizedHost = host.lowercase()
        val authority = if (uri.port == -1) normalizedHost else "$normalizedHost:${uri.port}"
        return Uri.Builder().scheme("https").encodedAuthority(authority).build().toString()
    }

    private fun buildChatUrl(siteOrigin: String): String {
        return Uri.parse(siteOrigin).buildUpon()
            .appendPath("culture")
            .appendQueryParameter("open_chat", "1")
            .fragment("live-lounge")
            .build()
            .toString()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val PREFERENCES = "kokwet_overlay"
        private const val SITE_URL = "site_url"
        private const val REQUEST_AUDIO = 1
    }
}
