package vn.giohoc.app

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Lớp phủ toàn màn hình hiện đè lên app bị khóa.
 * Vẽ thẳng bằng WindowManager (quyền "Hiển thị trên ứng dụng khác") nên không cần
 * mở Activity từ nền, là thứ máy Xiaomi, Oppo, Vivo hay chặn.
 */
class BlockOverlay(private val ctx: Context) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val store = Store(ctx)
    private var root: LinearLayout? = null
    private var tagView: TextView? = null
    private var titleView: TextView? = null
    private var detailView: TextView? = null
    private var appView: TextView? = null
    private var quizBtn: TextView? = null
    private var emergencyBtn: TextView? = null
    private var armed = false

    val showing: Boolean get() = root != null

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    private fun text(size: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(ctx)
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(10)
        t.layoutParams = lp
        return t
    }

    private fun button(label: String, filled: Boolean, onClick: () -> Unit): TextView {
        val t = TextView(ctx)
        t.text = label
        t.textSize = 16f
        t.gravity = Gravity.CENTER
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        t.setTextColor(if (filled) INK else WHITE)
        val bg = GradientDrawable()
        bg.cornerRadius = dp(26).toFloat()
        if (filled) bg.setColor(WHITE) else bg.setStroke(dp(1), WHITE_70)
        t.background = bg
        t.setPadding(dp(16), dp(14), dp(16), dp(14))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(10)
        t.layoutParams = lp
        t.isClickable = true
        t.setOnClickListener { onClick() }
        return t
    }

    private fun launch(i: Intent) {
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Diag.error = "Không mở được màn hình: " + e.javaClass.simpleName
        }
    }

    private fun build(): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.VERTICAL
        r.setBackgroundColor(NIGHT)
        r.setPadding(dp(24), dp(72), dp(24), dp(40))
        r.isClickable = true

        val tag = text(13f, WHITE_70, true)
        val title = text(30f, WHITE, true)
        val detail = text(16f, WHITE)
        val app = text(15f, WHITE_70)
        r.addView(tag)
        r.addView(title)
        r.addView(detail)
        r.addView(app)

        val spacer = View(ctx)
        spacer.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        r.addView(spacer)

        val quiz = button("Làm câu hỏi ngay", true) {
            launch(
                Intent(ctx, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_QUIZ, true)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
        r.addView(quiz)
        r.addView(button("Về màn hình chính", false) {
            launch(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        })
        r.addView(button("Gọi điện (luôn được phép)", false) {
            launch(Intent(Intent.ACTION_DIAL))
        })
        val emergency = TextView(ctx)
        emergency.textSize = 15f
        emergency.gravity = Gravity.CENTER
        emergency.setPadding(dp(16), dp(18), dp(16), dp(8))
        emergency.isClickable = true
        emergency.setOnClickListener { onEmergency() }
        r.addView(emergency)

        val hint = text(12f, WHITE_70)
        hint.gravity = Gravity.CENTER
        hint.text = "Nút không phản hồi? Vuốt về màn hình chính rồi mở Giờ Học."
        r.addView(hint)

        tagView = tag
        titleView = title
        detailView = detail
        appView = app
        quizBtn = quiz
        emergencyBtn = emergency
        return r
    }

    private fun onEmergency() {
        val cfg = store.config ?: return
        val left = cfg.emergencyPerWeek - store.emergencyUsedThisWeek()
        if (left <= 0) return
        if (!armed) {
            armed = true
            emergencyBtn?.text = "Bấm lần nữa để mở khẩn 5 phút (tốn 1 lượt)"
            return
        }
        store.useEmergency()
        hide()
    }

    /** Hiện lớp phủ (nếu chưa hiện) và cập nhật nội dung. Trả về false nếu không vẽ được. */
    fun show(pkg: String?, st: LockState): Boolean {
        if (root == null) {
            val r = build()
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE,
            )
            lp.gravity = Gravity.TOP or Gravity.START
            lp.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            if (Build.VERSION.SDK_INT >= 28) {
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            try {
                wm.addView(r, lp)
            } catch (e: Exception) {
                Diag.error = "Không vẽ được lớp phủ: " + e.javaClass.simpleName + " " + (e.message ?: "")
                return false
            }
            root = r
            armed = false
        }
        tagView?.text = if (st.kind == Kind.SELF) "PHIÊN TỰ HỌC" else "ĐANG TRONG GIỜ HỌC"
        titleView?.text = st.title
        detailView?.text = st.detail
        appView?.text = if (pkg == null) "Đây là màn hình chặn thử." else Apps.label(ctx, pkg) + " đang bị khóa."
        quizBtn?.visibility = if (st.pendingQuiz != null) View.VISIBLE else View.GONE
        val cfg = store.config
        val eb = emergencyBtn
        if (eb != null) {
            if (st.kind == Kind.SELF || cfg == null || pkg == null) {
                eb.visibility = View.GONE
            } else {
                eb.visibility = View.VISIBLE
                val left = (cfg.emergencyPerWeek - store.emergencyUsedThisWeek()).coerceAtLeast(0)
                eb.setTextColor(if (left > 0) GOLD else WHITE_40)
                if (!armed) eb.text = "Mở khẩn 5 phút (còn $left/${cfg.emergencyPerWeek} lượt tuần này)"
            }
        }
        return true
    }

    fun hide() {
        val r = root ?: return
        root = null
        armed = false
        try {
            wm.removeView(r)
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val NIGHT = 0xFF1D2A44.toInt()
        private const val INK = 0xFF16213A.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val WHITE_70 = 0xB3FFFFFF.toInt()
        private const val WHITE_40 = 0x66FFFFFF
        private const val GOLD = 0xFFF3A531.toInt()
    }
}
