package vn.giohoc.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat

/**
 * Chạy nền, mỗi giây xem app nào đang ở trên màn hình (qua UsageStatsManager).
 * Nếu đang trong giờ khóa và app đó không được phép thì vẽ lớp phủ BlockOverlay đè lên.
 * Không dùng Accessibility, không đọc nội dung màn hình.
 */
object Diag {
    @Volatile var pkg: String = "(chưa thấy)"
    @Volatile var decision: String = "-"
    @Volatile var error: String = ""
    @Volatile var lastLaunchAt: Long = 0L
    @Volatile var ticks: Int = 0
    @Volatile var testUntil: Long = 0L
}

class LockService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastPkg: String? = null
    private val overlay by lazy { BlockOverlay(this) }
    private var lastNotifText = ""

    private val tick = object : Runnable {
        override fun run() {
            try {
                check()
            } catch (e: Exception) {
                Diag.error = e.javaClass.simpleName + ": " + (e.message ?: "")
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        createChannel()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotif("Giờ Học đang chạy", ""), type)
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(tick)
        overlay.hide()
        super.onDestroy()
    }

    private fun check() {
        Diag.ticks++
        val st = LockEngine.state(this)
        val text = st.title + "|" + st.detail
        if (text != lastNotifText) {
            lastNotifText = text
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotif(st.title, st.detail))
        }
        if (System.currentTimeMillis() < Diag.testUntil) {
            Diag.decision = "Đang hiện màn hình chặn thử"
            if (overlay.show(null, st)) Diag.lastLaunchAt = System.currentTimeMillis()
            return
        }
        if (!Perms.hasUsage(this)) { Diag.decision = "Thiếu quyền dữ liệu sử dụng"; overlay.hide(); return }
        val pkg = foregroundPackage()
        if (pkg == null) { Diag.decision = "Chưa đọc được app đang mở"; overlay.hide(); return }
        Diag.pkg = pkg
        if (!st.locked) { Diag.decision = "Máy đang không trong giờ khóa"; overlay.hide(); return }
        if (pkg == packageName) { Diag.decision = "Đang ở trong Giờ Học"; overlay.hide(); return }
        val block = LockEngine.shouldBlock(this, pkg, st)
        Diag.decision = if (block) "CHẶN" else "Không chặn: " + LockEngine.whyNot(this, pkg, st)
        if (block) {
            if (!overlay.showing) Diag.lastLaunchAt = System.currentTimeMillis()
            overlay.show(pkg, st)
        } else {
            overlay.hide()
        }
    }

    private fun foregroundPackage(): String? {
        val usm = getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 10_000, now)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            // 1 = ACTIVITY_RESUMED (trước Android 10 gọi là MOVE_TO_FOREGROUND)
            if (e.eventType == 1) lastPkg = e.packageName
        }
        return lastPkg
    }

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL, "Trạng thái khóa máy", NotificationManager.IMPORTANCE_LOW)
        ch.setShowBadge(false)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotif(title: String, text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    companion object {
        const val CHANNEL = "lock"
        const val NOTIF_ID = 1

        @Volatile
        var running = false

        fun start(ctx: Context) {
            if (running) return
            if (!Perms.ready(ctx)) return
            runCatching { ctx.startForegroundService(Intent(ctx, LockService::class.java)) }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val st = Store(context)
        if (st.role == ROLE_STUDENT && (st.config != null || st.selfLockUntil > System.currentTimeMillis())) {
            LockService.start(context)
        }
    }
}
