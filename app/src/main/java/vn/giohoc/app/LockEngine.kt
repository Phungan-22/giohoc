package vn.giohoc.app

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import java.time.Instant
import java.time.ZoneId

enum class Kind { NO_CONFIG, FREE, CLASS, GAP_QUIZ, GAP_LOCKED, SELF, EMERGENCY }

data class PeriodRef(val index: Int, val period: Period, val dateKey: String) {
    val key: String get() = "$dateKey#$index"
}

data class LockState(
    val kind: Kind,
    val title: String,
    val detail: String,
    /** App được mở thêm (ngoài app thiết yếu) khi khóa theo tiết. */
    val allowed: Set<String> = emptySet(),
    /** Khác null = khóa theo nhóm app (tự học). -1 = mọi app không thiết yếu. */
    val blockCategories: Set<Int>? = null,
    val pendingQuiz: PeriodRef? = null,
    val current: PeriodRef? = null,
) {
    val locked: Boolean get() = kind == Kind.CLASS || kind == Kind.GAP_QUIZ || kind == Kind.GAP_LOCKED || kind == Kind.SELF
}

fun minutesLeft(ms: Long): String {
    val m = ((ms + 59_999) / 60_000).coerceAtLeast(1)
    return "$m phút"
}

object LockEngine {
    fun whyNot(ctx: Context, pkg: String, st: LockState): String = when {
        pkg in Apps.essentials(ctx) -> "app thiết yếu"
        !Apps.hasLauncher(ctx, pkg) -> "không có biểu tượng (app hệ thống)"
        st.blockCategories != null -> "nhóm app (category=${Apps.category(ctx, pkg)}) không nằm trong nhóm đã chọn"
        else -> "app được phép trong tiết này"
    }

    /** Sau tiết cuối, học sinh còn 10 phút để làm câu hỏi của tiết đó. */
    private const val LAST_QUIZ_WINDOW_MIN = 10L

    fun state(ctx: Context, nowMs: Long = System.currentTimeMillis()): LockState {
        val st = Store(ctx)
        if (nowMs < st.emergencyUntil) {
            return LockState(Kind.EMERGENCY, "Đang mở khẩn", "Máy tự khóa lại sau ${minutesLeft(st.emergencyUntil - nowMs)}.")
        }
        val cfg = st.config
        val dt = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault())
        val t = dt.toLocalTime()
        val dateKey = dt.toLocalDate().toString()

        if (cfg != null) {
            val today = cfg.periods.withIndex()
                .filter { it.value.day == dt.dayOfWeek.value }
                .sortedBy { it.value.startTime() }
            val cur = today.firstOrNull { !t.isBefore(it.value.startTime()) && t.isBefore(it.value.endTime()) }
            if (cur != null) {
                val ref = PeriodRef(cur.index, cur.value, dateKey)
                if (cur.value.lock) {
                    return LockState(
                        Kind.CLASS,
                        "Tiết ${cur.value.subject}",
                        "${cur.value.start}–${cur.value.end}. Chỉ mở các app cần cho việc học.",
                        allowed = cfg.allowedFor(cur.value.subject),
                        current = ref,
                    )
                }
            } else {
                val prev = today.lastOrNull { !t.isBefore(it.value.endTime()) }
                val next = today.firstOrNull { t.isBefore(it.value.startTime()) }
                if (prev != null && prev.value.lock) {
                    val ref = PeriodRef(prev.index, prev.value, dateKey)
                    val qs = cfg.quizzes[prev.value.subject].orEmpty()
                    val inWindow = next != null ||
                        t.isBefore(prev.value.endTime().plusMinutes(LAST_QUIZ_WINDOW_MIN))
                    if (qs.isNotEmpty() && inWindow && !st.isQuizDone(ref.key)) {
                        return LockState(
                            Kind.GAP_QUIZ,
                            "Hết tiết ${prev.value.subject}",
                            "Trả lời ${qs.size} câu hỏi ôn tập để mở máy.",
                            pendingQuiz = ref,
                        )
                    }
                    if (next != null && !cfg.breakAllowed && next.value.lock) {
                        return LockState(
                            Kind.GAP_LOCKED,
                            "Chờ tiết ${next.value.subject}",
                            "Lớp không mở máy giờ ra chơi. Tiết sau bắt đầu lúc ${next.value.start}.",
                        )
                    }
                }
            }
        }

        if (nowMs < st.selfLockUntil) {
            return LockState(
                Kind.SELF,
                "Đang tự học",
                "Còn ${minutesLeft(st.selfLockUntil - nowMs)}. Phiên tự học không dừng được giữa chừng.",
                blockCategories = st.selfGroups,
            )
        }
        if (cfg == null) return LockState(Kind.NO_CONFIG, "Chưa vào lớp", "Nhập mã lớp do giáo viên gửi.")
        return LockState(Kind.FREE, "Máy đang mở", "Ngoài giờ học hoặc đang giải lao.")
    }

    fun shouldBlock(ctx: Context, pkg: String, st: LockState): Boolean {
        if (!st.locked) return false
        if (pkg in Apps.essentials(ctx)) return false
        if (!Apps.hasLauncher(ctx, pkg)) return false // dịch vụ hệ thống, bàn phím...
        val cats = st.blockCategories
        if (cats != null) {
            if (-1 in cats) return true
            if (ApplicationInfo.CATEGORY_GAME in cats && Apps.isGame(ctx, pkg)) return true
            return Apps.category(ctx, pkg) in cats
        }
        return pkg !in st.allowed
    }
}

object Apps {
    @Suppress("DEPRECATION")
    fun isGame(ctx: Context, pkg: String): Boolean = try {
        val ai = ctx.packageManager.getApplicationInfo(pkg, 0)
        ai.category == ApplicationInfo.CATEGORY_GAME || (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0
    } catch (e: Exception) {
        false
    }

    private var essentialCache: Set<String> = emptySet()
    private var essentialAt = 0L
    private val launcherCache = HashMap<String, Boolean>()

    /** App luôn mở: chính Giờ Học, màn hình chính, gọi điện, tin nhắn, máy tính. */
    fun essentials(ctx: Context): Set<String> {
        val now = System.currentTimeMillis()
        if (now - essentialAt < 60_000 && essentialCache.isNotEmpty()) return essentialCache
        val pm = ctx.packageManager
        val s = mutableSetOf(
            ctx.packageName, "android", "com.android.systemui",
            "com.android.phone", "com.android.server.telecom", "com.android.emergency",
            "com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer",
            "com.android.contacts", "com.google.android.contacts", "com.samsung.android.app.contacts",
        )
        fun addAll(i: Intent) = pm.queryIntentActivities(i, 0).forEach { s.add(it.activityInfo.packageName) }
        addAll(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        addAll(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR))
        runCatching { ctx.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()?.let(s::add)
        runCatching { Telephony.Sms.getDefaultSmsPackage(ctx) }.getOrNull()?.let(s::add)
        essentialCache = s
        essentialAt = now
        return s
    }

    fun hasLauncher(ctx: Context, pkg: String): Boolean =
        launcherCache.getOrPut(pkg) { ctx.packageManager.getLaunchIntentForPackage(pkg) != null }

    fun category(ctx: Context, pkg: String): Int = try {
        ctx.packageManager.getApplicationInfo(pkg, 0).category
    } catch (e: PackageManager.NameNotFoundException) {
        ApplicationInfo.CATEGORY_UNDEFINED
    }

    fun label(ctx: Context, pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    /** Các app có biểu tượng trên máy này: (tên, package). */
    fun launcherApps(ctx: Context): List<Pair<String, String>> {
        val pm = ctx.packageManager
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .filter { it.second != ctx.packageName }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
    }
}

object Perms {
    fun hasUsage(ctx: Context): Boolean = try {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        false
    }

    fun hasOverlay(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun hasNotif(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun ready(ctx: Context): Boolean = hasUsage(ctx) && hasOverlay(ctx)
}
