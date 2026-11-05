package com.kokwet.voicecircle

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.PermissionRequest
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class FloatingChatService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var windowParams: WindowManager.LayoutParams
    private lateinit var chatView: LinearLayout
    private lateinit var bubbleView: TextView
    private lateinit var webView: WebView
    private var currentView: View? = null
    private var chatUrl = ""
    private var collapsed = false
    private var expandedWidth = 0
    private var expandedHeight = 0
    private var dragStartX = 0
    private var dragStartY = 0
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var movedWindow = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        chatUrl = intent?.getStringExtra(EXTRA_CHAT_URL).orEmpty()
        if (chatUrl.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        showChatWindow()
        return START_NOT_STICKY
    }

    private fun showChatWindow() {
        val restoreExpandedSize = collapsed
        removeCurrentView()
        collapsed = false
        chatView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(253, 252, 250))
            elevation = dp(8).toFloat()
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(6), dp(8))
            setBackgroundColor(Color.rgb(36, 18, 6))
        }
        val title = TextView(this).apply {
            text = "Kokwet live chat  ·  drag to move"
            textSize = 12f
            setTextColor(Color.WHITE)
            setOnTouchListener(::moveWindow)
        }
        toolbar.addView(title, LinearLayout.LayoutParams(0, dp(42), 1f))
        toolbar.addView(toolbarButton("−", "Make chat smaller") { resizeWindow(-1) })
        toolbar.addView(toolbarButton("+", "Make chat larger") { resizeWindow(1) })
        toolbar.addView(toolbarButton("□", "Minimize chat") { showBubble() })
        toolbar.addView(toolbarButton("×", "Close floating chat") { stopSelf() })
        chatView.addView(toolbar)

        webView = WebView(this).apply {
            setBackgroundColor(Color.rgb(253, 252, 250))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(false)
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    val origin = Uri.parse(chatUrl)
                    val requestOrigin = request.origin
                    val hasAudioPermission = checkSelfPermission(
                        android.Manifest.permission.RECORD_AUDIO,
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (hasAudioPermission
                        && requestOrigin.scheme == "https"
                        && requestOrigin.host == origin.host
                        && requestOrigin.port == origin.port
                        && request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                    ) {
                        request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                    } else {
                        request.deny()
                    }
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val target = request.url
                    val home = Uri.parse(chatUrl)
                    if (target.scheme == "https" && target.host == home.host) return false
                    if (target.scheme == "https") {
                        try {
                            startActivity(
                                Intent(Intent.ACTION_VIEW, target)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        } catch (_: ActivityNotFoundException) {
                            return true
                        }
                    }
                    return true
                }
            }
            loadUrl(chatUrl)
        }
        chatView.addView(webView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))
        chatView.addView(TextView(this).apply {
            text = "Drag the title to move  ·  − / + to resize"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(110, 97, 84))
            setPadding(dp(4), dp(6), dp(4), dp(6))
            setBackgroundColor(Color.rgb(245, 240, 231))
        })
        if (!::windowParams.isInitialized) {
            setDefaultWindowSize()
        } else if (restoreExpandedSize) {
            windowParams.width = expandedWidth
            windowParams.height = expandedHeight
        }
        showView(chatView)
    }

    private fun showBubble() {
        removeCurrentView()
        expandedWidth = windowParams.width
        expandedHeight = windowParams.height
        collapsed = true
        bubbleView = TextView(this).apply {
            text = "K"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(10, 90, 30))
            elevation = dp(8).toFloat()
            contentDescription = "Open Kokwet live chat"
            setOnClickListener { showChatWindow() }
            setOnTouchListener(::moveWindow)
        }
        windowParams.width = dp(60)
        windowParams.height = dp(60)
        val metrics = resources.displayMetrics
        windowParams.x = windowParams.x.coerceAtMost(metrics.widthPixels - windowParams.width)
        windowParams.y = windowParams.y.coerceAtMost(metrics.heightPixels - windowParams.height)
        showView(bubbleView)
    }

    private fun setDefaultWindowSize() {
        val metrics = resources.displayMetrics
        windowParams = WindowManager.LayoutParams(
            minOf(dp(390), metrics.widthPixels - dp(24)),
            minOf(dp(620), metrics.heightPixels - dp(80)),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(70)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
    }

    private fun showView(view: View) {
        try {
            windowManager.addView(view, windowParams)
            currentView = view
        } catch (error: SecurityException) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun removeCurrentView() {
        currentView?.let { view ->
            if (view.isAttachedToWindow) windowManager.removeView(view)
        }
        currentView = null
    }

    private fun moveWindow(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragStartX = windowParams.x
                dragStartY = windowParams.y
                touchStartX = event.rawX
                touchStartY = event.rawY
                movedWindow = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val metrics = resources.displayMetrics
                movedWindow = movedWindow
                    || kotlin.math.abs(event.rawX - touchStartX) > dp(5)
                    || kotlin.math.abs(event.rawY - touchStartY) > dp(5)
                windowParams.x = (dragStartX + event.rawX - touchStartX).toInt()
                    .coerceIn(0, (metrics.widthPixels - windowParams.width).coerceAtLeast(0))
                windowParams.y = (dragStartY + event.rawY - touchStartY).toInt()
                    .coerceIn(0, (metrics.heightPixels - windowParams.height).coerceAtLeast(0))
                currentView?.let { windowManager.updateViewLayout(it, windowParams) }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (collapsed && !movedWindow) view.performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return false
    }

    private fun resizeWindow(direction: Int) {
        val metrics = resources.displayMetrics
        val step = dp(40) * direction
        windowParams.width = (windowParams.width + step).coerceIn(dp(280), metrics.widthPixels)
        windowParams.height = (windowParams.height + step).coerceIn(dp(340), metrics.heightPixels)
        windowParams.x = windowParams.x.coerceAtMost(metrics.widthPixels - windowParams.width)
        windowParams.y = windowParams.y.coerceAtMost(metrics.heightPixels - windowParams.height)
        currentView?.let { windowManager.updateViewLayout(it, windowParams) }
    }

    private fun toolbarButton(label: String, description: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            contentDescription = description
            textSize = 16f
            minWidth = dp(38)
            minimumWidth = dp(38)
            minHeight = dp(42)
            minimumHeight = dp(42)
            setPadding(0, 0, 0, 0)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { onClick() }
        }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, FloatingChatService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Kokwet floating chat is active")
            .setContentText("Chat stays above other apps until you close it.")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL,
                "Floating chat",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        removeCurrentView()
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_CHAT_URL = "chat_url"
        private const val ACTION_STOP = "com.kokwet.voicecircle.STOP_OVERLAY"
        private const val NOTIFICATION_CHANNEL = "floating_chat"
        private const val NOTIFICATION_ID = 2026
    }
}
