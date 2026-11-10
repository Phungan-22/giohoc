package vn.giohoc.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

val SELF_GROUPS = listOf(
    ApplicationInfo.CATEGORY_SOCIAL to "Mạng xã hội",
    ApplicationInfo.CATEGORY_VIDEO to "Video",
    ApplicationInfo.CATEGORY_GAME to "Game",
    -1 to "Tất cả app (trừ gọi điện, tin nhắn, máy tính)",
)

@Composable
fun StudentApp(store: Store, quizRequest: MutableState<Boolean>, onSwitchRole: () -> Unit) {
    val ctx = LocalContext.current
    var cfg by remember { mutableStateOf(store.config) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val st = remember(now, cfg) { LockEngine.state(ctx, now) }
    val usage = remember(now) { Perms.hasUsage(ctx) }
    val overlay = remember(now) { Perms.hasOverlay(ctx) }
    val notif = remember(now) { Perms.hasNotif(ctx) }
    LaunchedEffect(usage, overlay, cfg) {
        if (cfg != null && usage && overlay) LockService.start(ctx)
    }

    var screen by remember { mutableStateOf("home") }
    var quizRef by remember { mutableStateOf<PeriodRef?>(null) }
    LaunchedEffect(quizRequest.value, st.pendingQuiz?.key) {
        if (quizRequest.value) {
            quizRequest.value = false
            st.pendingQuiz?.let {
                quizRef = it
                screen = "quiz"
            }
        }
    }

    val c = cfg
    val qr = quizRef
    if (c == null || screen == "join") {
        JoinScreen(
            store,
            onBack = if (c != null) ({ screen = "home" }) else null,
        ) {
            cfg = it
            screen = "home"
        }
        return
    }
    when {
        screen == "quiz" && qr != null -> QuizScreen(store, c, qr) {
            screen = "home"
            quizRef = null
            now = System.currentTimeMillis()
        }
        else -> StudentHome(
            store = store, cfg = c, st = st,
            usage = usage, overlay = overlay, notif = notif,
            onQuiz = { ref ->
                quizRef = ref
                screen = "quiz"
            },
            onJoin = { screen = "join" },
            onSwitchRole = onSwitchRole,
            onChanged = { now = System.currentTimeMillis() },
        )
    }
}

@Composable
fun StudentHome(
    store: Store,
    cfg: ClassConfig,
    st: LockState,
    usage: Boolean,
    overlay: Boolean,
    notif: Boolean,
    onQuiz: (PeriodRef) -> Unit,
    onJoin: () -> Unit,
    onSwitchRole: () -> Unit,
    onChanged: () -> Unit,
) {
    var askEmergency by remember { mutableStateOf(false) }
    val emergencyLeft = (cfg.emergencyPerWeek - store.emergencyUsedThisWeek()).coerceAtLeast(0)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle("Giờ Học", "${store.studentName} · Lớp ${cfg.className} · ${cfg.teacher}")
        StatusCard(st, emergencyLeft, onQuiz = { st.pendingQuiz?.let(onQuiz) }, onEmergency = { askEmergency = true })
        if (!usage || !overlay || !notif) PermissionCard(usage, overlay, notif)
        TodayCard(cfg, st)
        SelfLockCard(store, st, ready = usage && overlay, onChanged = onChanged)
        ResultsCard(store, cfg)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onJoin, enabled = !st.locked) { Text("Nhập mã lớp mới") }
            TextButton(onClick = onSwitchRole, enabled = !st.locked) { Text("Đổi vai trò") }
        }
        if (st.locked) Note("Không đổi lớp hay vai trò được khi máy đang khóa.")
    }

    if (askEmergency) {
        AlertDialog(
            onDismissRequest = { askEmergency = false },
            title = { Text("Mở khẩn 5 phút?") },
            text = { Text("Máy mở toàn bộ trong 5 phút rồi tự khóa lại. Bạn còn $emergencyLeft lượt trong tuần.") },
            confirmButton = {
                TextButton(onClick = {
                    store.useEmergency()
                    askEmergency = false
                    onChanged()
                }) { Text("Mở khẩn") }
            },
            dismissButton = { TextButton(onClick = { askEmergency = false }) { Text("Hủy") } },
        )
    }
}

@Composable
fun StatusCard(st: LockState, emergencyLeft: Int, onQuiz: () -> Unit, onEmergency: () -> Unit) {
    val bg = when {
        st.locked -> Night
        st.kind == Kind.EMERGENCY -> Amber
        st.kind == Kind.NO_CONFIG -> Muted
        else -> Green
    }
    val label = when {
        st.locked -> "ĐANG KHÓA"
        st.kind == Kind.EMERGENCY -> "MỞ KHẨN"
        else -> "ĐANG MỞ"
    }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(st.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text(st.detail, color = Color.White.copy(alpha = 0.9f))
            if (st.pendingQuiz != null) {
                Button(
                    onClick = onQuiz,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink),
                ) { Text("Làm câu hỏi", fontWeight = FontWeight.Bold) }
            }
            if (st.kind == Kind.CLASS || st.kind == Kind.GAP_QUIZ || st.kind == Kind.GAP_LOCKED) {
                TextButton(onClick = onEmergency, enabled = emergencyLeft > 0) {
                    Text(
                        "Mở khẩn 5 phút (còn $emergencyLeft lượt tuần này)",
                        color = if (emergencyLeft > 0) Gold else Color.White.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionCard(usage: Boolean, overlay: Boolean, notif: Boolean) {
    val ctx = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun open(i: Intent) {
        runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { runCatching { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }
    Section("Cấp quyền để khóa được máy") {
        Note("Giờ Học chỉ cần biết app nào đang mở để quyết định khóa hay không. App không đọc tin nhắn, ảnh, lịch sử web hay nội dung màn hình.")
        PermRow("Quyền truy cập dữ liệu sử dụng", usage) { open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        PermRow("Hiển thị trên ứng dụng khác", overlay) {
            open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
        }
        if (Build.VERSION.SDK_INT >= 33) {
            PermRow("Thông báo", notif) { notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
        }
        Note("Máy Xiaomi, Oppo, Vivo, Samsung hay tự tắt app chạy nền: vào cài đặt pin, chọn Không hạn chế cho Giờ Học.")
        TextButton(onClick = { open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) {
            Text("Mở cài đặt pin")
        }
    }
}

@Composable
fun PermRow(label: String, ok: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = Ink)
        if (ok) Tag("Đã cấp", Green) else Button(onClick = onClick) { Text("Cấp quyền") }
    }
}

@Composable
fun TodayCard(cfg: ClassConfig, st: LockState) {
    val today = LocalDate.now().dayOfWeek.value
    val list = cfg.periods.withIndex().filter { it.value.day == today }.sortedBy { it.value.startTime() }
    Section("Hôm nay · ${dayName(today)}") {
        if (list.isEmpty()) Note("Hôm nay không có tiết nào.")
        list.forEachIndexed { n, iv ->
            val p = iv.value
            val isNow = st.current?.index == iv.index
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isNow) Blue.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(10.dp))
                    .padding(8.dp),
            ) {
                Text("${n + 1}", fontWeight = FontWeight.ExtraBold, color = Blue, modifier = Modifier.padding(end = 12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.subject, fontWeight = FontWeight.Bold, color = Ink)
                    Note("${p.start}–${p.end}" + if (cfg.quizzes[p.subject].isNullOrEmpty()) "" else " · có câu hỏi sau tiết")
                }
                if (p.lock) Tag("Khóa", Blue) else Tag("Không khóa", Muted)
            }
        }
    }
}

@Composable
fun SelfLockCard(store: Store, st: LockState, ready: Boolean, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    var groups by remember { mutableStateOf(store.selfGroups) }
    var mins by remember { mutableIntStateOf(25) }
    Section("Tự khóa khi học ở nhà") {
        when {
            st.kind == Kind.SELF -> Note(st.detail)
            st.locked -> Note("Đang trong giờ học trên lớp, máy đã được khóa.")
            else -> {
                Note("Chọn nhóm app muốn chặn. Nhóm được nhận theo phân loại mà mỗi app tự khai báo.")
                SELF_GROUPS.forEach { (code, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            groups = if (code in groups) groups - code else groups + code
                        },
                    ) {
                        Checkbox(checked = code in groups, onCheckedChange = null)
                        Text(label, color = Ink)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(25, 45, 90).forEach { m -> Choice(m == mins, "$m phút") { mins = m } }
                }
                Button(
                    onClick = {
                        store.selfGroups = groups
                        store.selfLockUntil = System.currentTimeMillis() + mins * 60_000L
                        LockService.start(ctx)
                        onChanged()
                    },
                    enabled = groups.isNotEmpty() && ready,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Bắt đầu tự học $mins phút") }
                if (!ready) Note("Cấp quyền ở trên trước thì mới khóa được.")
                Note("Đã bắt đầu thì không dừng được giữa chừng.")
            }
        }
    }
}

@Composable
fun ResultsCard(store: Store, cfg: ClassConfig) {
    val ctx = LocalContext.current
    val all = store.results()
    val today = LocalDate.now().toString()
    val todays = all.filter { it.date == today }
    Section("Kết quả câu hỏi sau tiết") {
        if (all.isEmpty()) {
            Note("Chưa có kết quả. Hết mỗi tiết có câu hỏi, kết quả sẽ hiện ở đây.")
        } else {
            all.takeLast(8).reversed().forEach { r ->
                Row(Modifier.fillMaxWidth()) {
                    Text("${r.subject} · ${r.date.substring(5)} ${r.time}", Modifier.weight(1f), color = Ink)
                    Text("${r.score}/${r.total}", fontWeight = FontWeight.Bold, color = if (r.score == r.total) Green else Amber)
                }
            }
            OutlinedButton(
                onClick = {
                    val list = todays.ifEmpty { all.takeLast(5) }
                    val text = "Giờ Học – kết quả của ${store.studentName} (lớp ${cfg.className}):\n" +
                        list.joinToString("\n") { "• ${it.subject} ${it.date} ${it.time}: ${it.score}/${it.total} câu đúng" }
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    ctx.startActivity(Intent.createChooser(send, "Gửi kết quả").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
            ) { Text("Gửi kết quả cho thầy cô") }
        }
    }
}

@Composable
fun JoinScreen(store: Store, onBack: (() -> Unit)?, onDone: (ClassConfig) -> Unit) {
    var name by remember { mutableStateOf(store.studentName) }
    var code by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val clip = LocalClipboardManager.current

    fun finish(c: ClassConfig) {
        store.studentName = name.trim()
        store.config = c
        onDone(c)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle("Vào lớp", "Giáo viên chủ nhiệm gửi mã lớp qua Zalo hoặc nhóm lớp. Mã bắt đầu bằng GH1-.")
        OutlinedTextField(
            value = name, onValueChange = { name = it; err = null },
            label = { Text("Họ và tên") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = code, onValueChange = { code = it; err = null },
            label = { Text("Mã lớp") }, minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = { clip.getText()?.text?.let { code = it } }) { Text("Dán mã") }
        err?.let { Text(it, color = Red) }
        Button(
            onClick = {
                val c = ClassConfig.decode(code)
                when {
                    name.isBlank() -> err = "Nhập họ và tên trước."
                    c == null -> err = "Mã lớp không đúng. Mã phải bắt đầu bằng GH1- và được dán đầy đủ."
                    else -> finish(c)
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Vào lớp") }
        TextButton(onClick = { if (name.isBlank()) err = "Nhập họ và tên trước." else finish(sampleConfig()) }) {
            Text("Chưa có mã? Dùng lớp mẫu 10A1 để thử")
        }
        if (onBack != null) TextButton(onClick = onBack) { Text("Quay lại") }
    }
}

@Composable
fun QuizScreen(store: Store, cfg: ClassConfig, ref: PeriodRef, onDone: () -> Unit) {
    val qs = cfg.quizzes[ref.period.subject].orEmpty()
    val answers = remember { mutableStateListOf<Int>().apply { repeat(qs.size) { add(-1) } } }
    var left by remember { mutableIntStateOf(120) }
    var submitted by remember { mutableStateOf(false) }

    fun submit() {
        if (submitted) return
        submitted = true
        val score = qs.indices.count { answers[it] == qs[it].correct }
        store.addResult(
            QuizResult(
                ref.dateKey, ref.index, ref.period.subject, score, qs.size,
                LocalTime.now().withSecond(0).withNano(0).toString()
            )
        )
        store.markQuizDone(ref.key)
    }

    LaunchedEffect(Unit) {
        while (left > 0 && !submitted) {
            delay(1000)
            left--
        }
        if (!submitted) submit()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { ScreenTitle("Ôn nhanh · ${ref.period.subject}") }
            if (!submitted) Tag("%d:%02d".format(left / 60, left % 60), Amber)
        }
        if (!submitted) {
            Note("Trả lời ${qs.size} câu về bài vừa học. Hết giờ máy tự nộp.")
        } else {
            val score = qs.indices.count { answers[it] == qs[it].correct }
            Card(colors = CardDefaults.cardColors(containerColor = if (score == qs.size) Green else Amber)) {
                Text(
                    "Bạn đúng $score/${qs.size} câu." + if (cfg.breakAllowed) " Máy đã mở đến tiết sau." else "",
                    color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(14.dp),
                )
            }
        }
        qs.forEachIndexed { i, q ->
            Section("Câu ${i + 1}. ${q.q}") {
                q.options.forEachIndexed { j, o ->
                    val color = when {
                        !submitted -> Ink
                        j == q.correct -> Green
                        answers[i] == j -> Red
                        else -> Ink
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !submitted) { answers[i] = j },
                    ) {
                        RadioButton(selected = answers[i] == j, onClick = null, enabled = !submitted)
                        Text("${'A' + j}. $o", color = color, fontWeight = if (submitted && j == q.correct) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
        if (!submitted) {
            val n = answers.count { it >= 0 }
            Button(onClick = { submit() }, enabled = n == qs.size, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Nộp bài ($n/${qs.size})")
            }
        } else {
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Xong") }
        }
        Note("Gọi điện vẫn dùng được bất cứ lúc nào.")
    }
}
