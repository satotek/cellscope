package dev.satotek.cellscope.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.satotek.cellscope.CellScopeApp
import dev.satotek.cellscope.MainActivity
import dev.satotek.cellscope.R
import dev.satotek.cellscope.ui.screens.PipHud
import dev.satotek.cellscope.ui.theme.CellScopeTheme
import kotlin.math.hypot

/** Draggable TYPE_APPLICATION_OVERLAY HUD. Survives Home; closed from the X or Setup. */
class OverlayService : Service() {

    private val owner = OverlayOwner()
    private var wm: WindowManager? = null
    private var root: View? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        owner.attach()
        startAsForeground()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        attachWindow()
    }

    override fun onDestroy() {
        detachWindow()
        owner.detach()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.overlay), NotificationManager.IMPORTANCE_MIN))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_hud)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.overlay_running))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        startForeground(NOTIF, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    private fun attachWindow() {
        val d = resources.displayMetrics.density
        val w = (320 * d).toInt()
        val h = (168 * d).toInt()
        val p = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (24 * d).toInt()
            y = (120 * d).toInt()
        }
        val vm = (application as CellScopeApp).vm
        val compose = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                val s = vm.state.collectAsStateWithLifecycle().value
                val page = vm.hudPage.collectAsStateWithLifecycle().value
                CellScopeTheme { PipHud(s, page) }
            }
        }
        val frame = FrameLayout(this)
        frame.setViewTreeLifecycleOwner(owner)
        frame.setViewTreeViewModelStoreOwner(owner)
        frame.setViewTreeSavedStateRegistryOwner(owner)
        frame.addView(compose, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val close = ImageButton(this).apply {
            setImageResource(R.drawable.ic_close)
            background = null
            imageTintList = android.content.res.ColorStateList.valueOf(0xB3FFFFFF.toInt())
            setOnClickListener { vm.setOverlayEnabled(false) }
            contentDescription = getString(R.string.overlay_close)
        }
        val pad = (4 * d).toInt()
        val sz = (32 * d).toInt()
        frame.addView(close, FrameLayout.LayoutParams(sz, sz, Gravity.END or Gravity.TOP).apply { setMargins(0, pad, pad, 0) })
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var dragged = false
        compose.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = p.x; startY = p.y
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (hypot(dx.toDouble(), dy.toDouble()) > 16 * d) dragged = true
                    if (dragged) {
                        p.x = startX + dx.toInt()
                        p.y = startY + dy.toInt()
                        runCatching { wm?.updateViewLayout(frame, p) }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!dragged) vm.cycleHud()
                    true
                }
                else -> false
            }
        }
        val window = getSystemService(WINDOW_SERVICE) as WindowManager
        runCatching { window.addView(frame, p) }.onFailure { stopSelf(); return }
        wm = window; root = frame; params = p
    }

    private fun detachWindow() {
        val v = root ?: return
        runCatching { wm?.removeView(v) }
        root = null; wm = null; params = null
    }

    companion object {
        private const val CHANNEL = "hud"
        private const val NOTIF = 7
        fun start(ctx: Context) {
            if (!Settings.canDrawOverlays(ctx)) return
            ctx.startForegroundService(Intent(ctx, OverlayService::class.java))
        }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, OverlayService::class.java)) }
    }
}

private class OverlayOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    fun attach() {
        saved.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }
    fun detach() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}
