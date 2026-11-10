package vn.giohoc.app

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun TeacherApp(store: Store, onSwitchRole: () -> Unit) {
    var cfg by remember { mutableStateOf(store.teacherConfig) }
    val save: (ClassConfig) -> Unit = { c ->
        cfg = c
        store.teacherConfig = c
    }
    var screen by remember { mutableStateOf("home") }
    BackHandler(enabled = screen != "home") { screen = "home" }

    when {
        screen == "export" -> ExportScreen(cfg) { screen = "home" }
        screen.startsWith("apps:") -> AppPicker(cfg, screen.removePrefix("apps:"), save) { screen = "home" }
        screen.startsWith("quiz:") -> QuizEditor(cfg, screen.removePrefix("quiz:"), save) { screen = "home" }
        else -> TeacherHome(cfg, save, open = { screen = it }, onSwitchRole = onSwitchRole)
    }
}

@Composable
fun TeacherHome(cfg: ClassConfig, save: (ClassConfig) -> Unit, open: (String) -> Unit, onSwitchRole: () -> Unit) {
    var day by remember { mutableIntStateOf(LocalDate.now().dayOfWeek.value.coerceAtMost(6)) }
    var editing by remember { mutableStateOf<Int?>(null) } // chỉ số tiết đang sửa, -1 = thêm mới

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle("Giáo viên", "Soạn lớp, thời khóa biểu và câu hỏi, rồi gửi mã lớp cho học sinh.")

        Section("Thông tin lớp") {
            OutlinedTextField(
                value = cfg.className, onValueChange = { save(cfg.copy(className = it)) },
                label = { Text("Tên lớp") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = cfg.teacher, onValueChange = { save(cfg.copy(teacher = it)) },
                label = { Text("Giáo viên chủ nhiệm") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Số lần Mở khẩn mỗi tuần", color = Ink)
                    Note("Mỗi lần mở máy 5 phút")
                }
                OutlinedButton(onClick = {
                    if (cfg.emergencyPerWeek > 0) save(cfg.copy(emergencyPerWeek = cfg.emergencyPerWeek - 1))
                }) { Text("−") }
                Text("${cfg.emergencyPerWeek}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
                OutlinedButton(onClick = {
                    if (cfg.emergencyPerWeek < 10) save(cfg.copy(emergencyPerWeek = cfg.emergencyPerWeek + 1))
                }) { Text("+") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mở máy giờ ra chơi sau khi làm câu hỏi", color = Ink)
                    Note("Tắt nếu trường cấm dùng điện thoại cả giờ ra chơi")
                }
                Switch(checked = cfg.breakAllowed, onCheckedChange = { save(cfg.copy(breakAllowed = it)) })
            }
        }

        Section("Thời khóa biểu") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..7).forEach { d -> Choice(d == day, dayName(d)) { day = d } }
            }
            val list = cfg.periods.withIndex().filter { it.value.day == day }.sortedBy { it.value.start }
            if (list.isEmpty()) Note("Chưa có tiết nào vào ${dayName(day)}.")
            list.forEach { iv ->
                val p = iv.value
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editing = iv.index }
                        .padding(vertical = 6.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.subject, fontWeight = FontWeight.Bold, color = Ink)
                        Note("${p.start}–${p.end}")
                    }
                    if (p.lock) Tag("Khóa máy", Blue) else Tag("Không khóa", Muted)
                }
            }
            OutlinedButton(onClick = { editing = -1 }) { Text("+ Thêm tiết") }
            Note("Bấm vào một tiết để sửa hoặc xóa.")
        }

        Section("Theo môn") {
            Note("Chọn app được mở trong tiết (ví dụ GeoGebra cho Toán) và câu hỏi gửi khi hết tiết.")
            cfg.subjects().forEach { s ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(s, fontWeight = FontWeight.Bold, color = Ink)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { open("apps:$s") }) { Text("App mở: ${cfg.allowedApps[s].orEmpty().size}") }
                        OutlinedButton(onClick = { open("quiz:$s") }) { Text("Câu hỏi: ${cfg.quizzes[s].orEmpty().size}") }
                    }
                }
            }
            OutlinedButton(onClick = { open("apps:*") }, modifier = Modifier.fillMaxWidth()) {
                Text("App luôn mở ở mọi tiết: ${cfg.alwaysAllowed.size}")
            }
            Note("Gọi điện, tin nhắn, máy tính và màn hình chính luôn mở, không cần chọn.")
        }

        Button(onClick = { open("export") }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text("Tạo mã lớp gửi học sinh", fontSize = 16.sp)
        }
        Row {
            TextButton(onClick = { save(sampleConfig()) }) { Text("Nạp lại lớp mẫu") }
            TextButton(onClick = onSwitchRole) { Text("Đổi vai trò") }
        }
    }

    editing?.let { idx ->
        PeriodDialog(
            initial = if (idx >= 0) cfg.periods[idx] else Period(day, "07:00", "07:45", "", true),
            isNew = idx < 0,
            onDismiss = { editing = null },
            onDelete = {
                save(cfg.copy(periods = cfg.periods.filterIndexed { j, _ -> j != idx }))
                editing = null
            },
            onSave = { p ->
                val list = if (idx >= 0) cfg.periods.mapIndexed { j, o -> if (j == idx) p else o } else cfg.periods + p
                save(cfg.copy(periods = list))
                day = p.day
                editing = null
            },
        )
    }
}

/** "7", "7h", "7:5", "07:05" -> "07:05". Sai định dạng -> null. */
fun normTime(s: String): String? = runCatching {
    val parts = s.trim().lowercase().replace('h', ':').split(':')
    val h = parts[0].trim().toInt()
    val m = parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.toInt() ?: 0
    LocalTime.of(h, m).toString()
}.getOrNull()

@Composable
fun PeriodDialog(
    initial: Period,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (Period) -> Unit,
) {
    var day by remember { mutableIntStateOf(initial.day) }
    var subject by remember { mutableStateOf(initial.subject) }
    var start by remember { mutableStateOf(initial.start) }
    var end by remember { mutableStateOf(initial.end) }
    var lock by remember { mutableStateOf(initial.lock) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Thêm tiết" else "Sửa tiết") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..7).forEach { d -> Choice(d == day, dayName(d)) { day = d } }
                }
                OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Môn") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("Bắt đầu") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("Kết thúc") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Khóa máy trong tiết này", Modifier.weight(1f))
                    Switch(checked = lock, onCheckedChange = { lock = it })
                }
                err?.let { Text(it, color = Red) }
                if (!isNew) TextButton(onClick = onDelete) { Text("Xóa tiết này", color = Red) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = normTime(start)
                val e = normTime(end)
                when {
                    subject.isBlank() -> err = "Nhập tên môn."
                    s == null || e == null -> err = "Giờ phải có dạng 07:00."
                    !LocalTime.parse(s).isBefore(LocalTime.parse(e)) -> err = "Giờ kết thúc phải sau giờ bắt đầu."
                    else -> onSave(Period(day, s, e, subject.trim(), lock))
                }
            }) { Text("Lưu") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
    )
}

@Composable
fun AppPicker(cfg: ClassConfig, subject: String, save: (ClassConfig) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    val apps = remember { Apps.launcherApps(ctx) }
    val isAll = subject == "*"
    val selected = if (isAll) cfg.alwaysAllowed else cfg.allowedApps[subject].orEmpty()
    fun set(list: List<String>) {
        save(if (isAll) cfg.copy(alwaysAllowed = list) else cfg.copy(allowedApps = cfg.allowedApps + (subject to list)))
    }
    var query by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf("") }
    val extra = selected.filter { p -> apps.none { it.second == p } }
    val shown = apps.filter { query.isBlank() || it.first.contains(query, ignoreCase = true) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = back) { Text("‹ Xong") }
            Text(
                if (isAll) "App luôn mở" else "App mở trong tiết $subject",
                fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink,
            )
        }
        Note("Danh sách lấy từ các app trên máy của thầy cô. Học sinh cài cùng app (cùng tên gói) thì app đó được mở. Đã chọn ${selected.size} app.")
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Tìm app") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f)) {
            items(extra) { p ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { set(selected - p) }) {
                    Checkbox(checked = true, onCheckedChange = null)
                    Column {
                        Text(p, color = Ink)
                        Note("Không có trên máy này")
                    }
                }
            }
            items(shown) { (label, pkg) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { set(if (pkg in selected) selected - pkg else selected + pkg) },
                ) {
                    Checkbox(checked = pkg in selected, onCheckedChange = null)
                    Column {
                        Text(label, color = Ink)
                        Note(pkg)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = manual, onValueChange = { manual = it },
                label = { Text("Thêm theo tên gói") }, singleLine = true, modifier = Modifier.weight(1f),
            )
            Button(onClick = {
                val p = manual.trim()
                if (p.isNotEmpty() && p !in selected) set(selected + p)
                manual = ""
            }) { Text("Thêm") }
        }
    }
}

@Composable
fun QuizEditor(cfg: ClassConfig, subject: String, save: (ClassConfig) -> Unit, back: () -> Unit) {
    val qs = cfg.quizzes[subject].orEmpty()
    fun set(list: List<Question>) = save(cfg.copy(quizzes = cfg.quizzes + (subject to list)))
    var q by remember { mutableStateOf("") }
    val opts = remember { mutableStateListOf("", "", "", "") }
    var correct by remember { mutableIntStateOf(0) }
    var err by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = back) { Text("‹ Xong") }
            Text("Câu hỏi sau tiết $subject", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
        }
        Note("Học sinh có 2 phút. Nên giữ 3–5 câu ngắn về bài vừa dạy. Không có câu hỏi thì hết tiết học sinh được mở máy luôn.")
        qs.forEachIndexed { i, x ->
            Section("Câu ${i + 1}. ${x.q}") {
                x.options.forEachIndexed { j, o ->
                    Text(
                        "${'A' + j}. $o",
                        color = if (j == x.correct) Green else Ink,
                        fontWeight = if (j == x.correct) FontWeight.Bold else FontWeight.Normal,
                    )
                }
                TextButton(onClick = { set(qs.filterIndexed { k, _ -> k != i }) }) { Text("Xóa câu này", color = Red) }
            }
        }
        Section("Thêm câu hỏi") {
            OutlinedTextField(value = q, onValueChange = { q = it; err = null }, label = { Text("Câu hỏi") }, modifier = Modifier.fillMaxWidth())
            (0..3).forEach { j ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = correct == j, onClick = { correct = j })
                    OutlinedTextField(
                        value = opts[j], onValueChange = { opts[j] = it; err = null },
                        label = { Text("Đáp án ${'A' + j}") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
            }
            Note("Chấm tròn đánh dấu đáp án đúng. Có thể để trống đáp án C, D.")
            err?.let { Text(it, color = Red) }
            Button(onClick = {
                val o = opts.map { it.trim() }
                when {
                    q.isBlank() -> err = "Nhập nội dung câu hỏi."
                    o[0].isEmpty() || o[1].isEmpty() -> err = "Cần ít nhất đáp án A và B."
                    o[correct].isEmpty() -> err = "Đáp án đúng đang để trống."
                    else -> {
                        val kept = o.withIndex().filter { it.value.isNotEmpty() }
                        val newCorrect = kept.indexOfFirst { it.index == correct }
                        set(qs + Question(q.trim(), kept.map { it.value }, newCorrect))
                        q = ""
                        for (j in 0..3) opts[j] = ""
                        correct = 0
                        err = null
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Thêm câu hỏi") }
        }
    }
}

@Composable
fun ExportScreen(cfg: ClassConfig, back: () -> Unit) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val code = remember(cfg) { cfg.encode() }
    var copied by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = back) { Text("‹ Quay lại") }
        }
        ScreenTitle("Mã lớp ${cfg.className}")
        Note("Gửi mã này vào nhóm Zalo của lớp. Học sinh mở Giờ Học, chọn Học sinh rồi dán mã. Sửa thời khóa biểu hay câu hỏi thì gửi lại mã mới.")
        SelectionContainer {
            Text(
                code,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = Ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(10.dp))
                    .padding(12.dp),
            )
        }
        Note("${cfg.periods.size} tiết/tuần · ${cfg.quizzes.values.sumOf { it.size }} câu hỏi · ${code.length} ký tự")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                clip.setText(AnnotatedString(code))
                copied = true
            }) { Text(if (copied) "Đã sao chép" else "Sao chép mã") }
            OutlinedButton(onClick = {
                val text = "Mã lớp ${cfg.className} trên app Giờ Học (mở app, chọn Học sinh, dán mã):\n$code"
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                ctx.startActivity(Intent.createChooser(send, "Gửi mã lớp").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("Gửi qua Zalo…") }
        }
    }
}
