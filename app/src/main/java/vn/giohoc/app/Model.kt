package vn.giohoc.app

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class Question(val q: String, val options: List<String>, val correct: Int)

/** day: 1 = Thứ Hai ... 7 = Chủ nhật (giống java.time.DayOfWeek). start/end dạng "07:00". */
data class Period(
    val day: Int,
    val start: String,
    val end: String,
    val subject: String,
    val lock: Boolean = true,
) {
    fun startTime(): LocalTime = LocalTime.parse(start)
    fun endTime(): LocalTime = LocalTime.parse(end)
}

data class ClassConfig(
    val className: String,
    val teacher: String,
    val emergencyPerWeek: Int = 3,
    val breakAllowed: Boolean = true,
    val alwaysAllowed: List<String> = emptyList(),
    val periods: List<Period> = emptyList(),
    val allowedApps: Map<String, List<String>> = emptyMap(),
    val quizzes: Map<String, List<Question>> = emptyMap(),
) {
    fun subjects(): List<String> = periods.map { it.subject }.distinct()

    fun allowedFor(subject: String): Set<String> =
        (alwaysAllowed + allowedApps[subject].orEmpty()).toSet()

    fun toJson(): JSONObject = JSONObject().apply {
        put("v", 1)
        put("className", className)
        put("teacher", teacher)
        put("emergencyPerWeek", emergencyPerWeek)
        put("breakAllowed", breakAllowed)
        put("alwaysAllowed", JSONArray(alwaysAllowed))
        put("periods", JSONArray().apply {
            periods.forEach { p ->
                put(JSONObject().apply {
                    put("day", p.day); put("start", p.start); put("end", p.end)
                    put("subject", p.subject); put("lock", p.lock)
                })
            }
        })
        put("allowedApps", JSONObject().apply {
            allowedApps.forEach { (k, v) -> put(k, JSONArray(v)) }
        })
        put("quizzes", JSONObject().apply {
            quizzes.forEach { (k, v) ->
                put(k, JSONArray().apply {
                    v.forEach { q ->
                        put(JSONObject().apply {
                            put("q", q.q); put("o", JSONArray(q.options)); put("c", q.correct)
                        })
                    }
                })
            }
        })
    }

    /** Mã lớp để gửi cho học sinh: JSON nén gzip rồi mã hóa Base64. */
    fun encode(): String {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(toJson().toString().toByteArray(Charsets.UTF_8)) }
        return PREFIX + Base64.encodeToString(
            bos.toByteArray(),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    companion object {
        const val PREFIX = "GH1-"

        private fun strings(a: JSONArray?): List<String> =
            if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) }

        fun fromJson(o: JSONObject): ClassConfig {
            val periods = o.optJSONArray("periods")?.let { a ->
                (0 until a.length()).map { i ->
                    val p = a.getJSONObject(i)
                    Period(
                        p.getInt("day"), p.getString("start"), p.getString("end"),
                        p.getString("subject"), p.optBoolean("lock", true)
                    ).also { it.startTime(); it.endTime() } // kiểm tra giờ hợp lệ
                }
            }.orEmpty()
            val allowed = mutableMapOf<String, List<String>>()
            o.optJSONObject("allowedApps")?.let { m ->
                m.keys().forEach { k -> allowed[k] = strings(m.optJSONArray(k)) }
            }
            val quizzes = mutableMapOf<String, List<Question>>()
            o.optJSONObject("quizzes")?.let { m ->
                m.keys().forEach { k ->
                    val a = m.getJSONArray(k)
                    quizzes[k] = (0 until a.length()).map { i ->
                        val q = a.getJSONObject(i)
                        Question(q.getString("q"), strings(q.getJSONArray("o")), q.getInt("c"))
                    }
                }
            }
            return ClassConfig(
                className = o.optString("className", "Lớp"),
                teacher = o.optString("teacher", ""),
                emergencyPerWeek = o.optInt("emergencyPerWeek", 3),
                breakAllowed = o.optBoolean("breakAllowed", true),
                alwaysAllowed = strings(o.optJSONArray("alwaysAllowed")),
                periods = periods,
                allowedApps = allowed,
                quizzes = quizzes,
            )
        }

        fun decode(code: String): ClassConfig? = try {
            val clean = code.trim().replace("\\s".toRegex(), "")
            require(clean.startsWith(PREFIX))
            val bytes = Base64.decode(clean.removePrefix(PREFIX), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val json = GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes().toString(Charsets.UTF_8) }
            fromJson(JSONObject(json))
        } catch (e: Exception) {
            null
        }
    }
}

data class QuizResult(
    val date: String,
    val periodIndex: Int,
    val subject: String,
    val score: Int,
    val total: Int,
    val time: String,
)

/** Lưu mọi thứ trong SharedPreferences trên máy. Bản này chưa có máy chủ. */
class Store(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("giohoc", Context.MODE_PRIVATE)

    var role: String?
        get() = p.getString("role", null)
        set(v) = p.edit().putString("role", v).apply()

    var studentName: String
        get() = p.getString("studentName", "") ?: ""
        set(v) = p.edit().putString("studentName", v).apply()

    var config: ClassConfig?
        get() = p.getString("config", null)?.let {
            runCatching { ClassConfig.fromJson(JSONObject(it)) }.getOrNull()
        }
        set(v) = p.edit().putString("config", v?.toJson()?.toString()).apply()

    var teacherConfig: ClassConfig
        get() = p.getString("teacherConfig", null)?.let {
            runCatching { ClassConfig.fromJson(JSONObject(it)) }.getOrNull()
        } ?: sampleConfig()
        set(v) = p.edit().putString("teacherConfig", v.toJson().toString()).apply()

    var emergencyUntil: Long
        get() = p.getLong("emergencyUntil", 0L)
        set(v) = p.edit().putLong("emergencyUntil", v).apply()

    var selfLockUntil: Long
        get() = p.getLong("selfLockUntil", 0L)
        set(v) = p.edit().putLong("selfLockUntil", v).apply()

    /** Nhóm app tự khóa: mã ApplicationInfo.CATEGORY_*, hoặc -1 = mọi app không thiết yếu. */
    var selfGroups: Set<Int>
        get() = (p.getStringSet("selfGroups", null) ?: setOf("-1")).mapNotNull { it.toIntOrNull() }.toSet()
        set(v) = p.edit().putStringSet("selfGroups", v.map { it.toString() }.toSet()).apply()

    fun isQuizDone(key: String): Boolean =
        (p.getStringSet("quizDone", emptySet()) ?: emptySet()).contains(key)

    fun markQuizDone(key: String) {
        val s = HashSet(p.getStringSet("quizDone", emptySet()) ?: emptySet())
        s.add(key)
        p.edit().putStringSet("quizDone", s).apply()
    }

    private fun weekStartMs(): Long =
        LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun emergencyTimes(): List<Long> =
        (p.getString("emergencyTimes", "") ?: "").split(",").mapNotNull { it.toLongOrNull() }

    fun emergencyUsedThisWeek(): Int {
        val ws = weekStartMs()
        return emergencyTimes().count { it >= ws }
    }

    fun useEmergency(now: Long = System.currentTimeMillis()) {
        val ws = weekStartMs()
        val list = emergencyTimes().filter { it >= ws } + now
        p.edit()
            .putString("emergencyTimes", list.joinToString(","))
            .putLong("emergencyUntil", now + EMERGENCY_MS)
            .apply()
    }

    fun results(): List<QuizResult> {
        val a = runCatching { JSONArray(p.getString("results", "[]")) }.getOrElse { JSONArray() }
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            QuizResult(
                o.getString("date"), o.getInt("pi"), o.getString("subject"),
                o.getInt("score"), o.getInt("total"), o.optString("time")
            )
        }
    }

    fun addResult(r: QuizResult) {
        val a = runCatching { JSONArray(p.getString("results", "[]")) }.getOrElse { JSONArray() }
        a.put(JSONObject().apply {
            put("date", r.date); put("pi", r.periodIndex); put("subject", r.subject)
            put("score", r.score); put("total", r.total); put("time", r.time)
        })
        // giữ 200 kết quả gần nhất
        val trimmed = JSONArray()
        val from = maxOf(0, a.length() - 200)
        for (i in from until a.length()) trimmed.put(a.get(i))
        p.edit().putString("results", trimmed.toString()).apply()
    }

    companion object {
        const val EMERGENCY_MS = 5 * 60 * 1000L
    }
}

/** Lớp mẫu để giáo viên sửa lại, hoặc để học sinh thử app ngay. */
fun sampleConfig(): ClassConfig {
    val times = listOf("07:00" to "07:45", "07:50" to "08:35", "08:55" to "09:40", "09:45" to "10:30", "10:35" to "11:20")
    val week = listOf(
        listOf("Toán", "Toán", "Ngữ văn", "Tiếng Anh", "Tin học"),
        listOf("Tin học", "Toán", "Ngữ văn", "Tiếng Anh", "Vật lý"),
        listOf("Ngữ văn", "Ngữ văn", "Toán", "Vật lý", "Tiếng Anh"),
        listOf("Tiếng Anh", "Vật lý", "Toán", "Tin học", "Ngữ văn"),
        listOf("Vật lý", "Toán", "Tiếng Anh", "Ngữ văn", "Tin học"),
    )
    val periods = week.flatMapIndexed { d, subjects ->
        subjects.mapIndexed { i, s -> Period(d + 1, times[i].first, times[i].second, s, true) }
    }
    val quizzes = mapOf(
        "Toán" to listOf(
            Question("Cho A = {1; 2; 3}, B = {2; 3; 4}. A ∩ B bằng:", listOf("{2; 3}", "{1; 4}", "{1; 2; 3; 4}", "∅"), 0),
            Question("Phủ định của \"Mọi số tự nhiên đều không âm\" là:", listOf("Mọi số tự nhiên đều âm", "Có một số tự nhiên âm", "Không có số tự nhiên nào âm", "Có một số tự nhiên không âm"), 1),
            Question("Tập {a; b; c} có bao nhiêu tập con?", listOf("3", "6", "8", "9"), 2),
        ),
        "Tin học" to listOf(
            Question("Trong Python, lệnh nào in ra màn hình?", listOf("input()", "print()", "int()", "len()"), 1),
            Question("Kết quả của 7 // 2 trong Python là:", listOf("3.5", "3", "4", "1"), 1),
            Question("Kiểu dữ liệu của giá trị \"10\" là:", listOf("int", "float", "str", "bool"), 2),
        ),
        "Ngữ văn" to listOf(
            Question("Thần thoại thường giải thích điều gì?", listOf("Nguồn gốc thế giới, tự nhiên", "Chuyện đời thường", "Kinh nghiệm sản xuất", "Lịch sử triều đại"), 0),
            Question("Nhân vật trong thần thoại thường là:", listOf("Người nông dân", "Các vị thần", "Vua chúa", "Con vật biết nói"), 1),
        ),
        "Tiếng Anh" to listOf(
            Question("She ___ to school every day.", listOf("go", "goes", "going", "gone"), 1),
            Question("\"Household chores\" nghĩa là:", listOf("Bài tập về nhà", "Việc nhà", "Đồ gia dụng", "Hộ gia đình"), 1),
        ),
        "Vật lý" to listOf(
            Question("Đơn vị của vận tốc trong hệ SI là:", listOf("km/h", "m/s", "m/s²", "N"), 1),
            Question("Đi 100 m trong 20 s, tốc độ trung bình là:", listOf("2 m/s", "5 m/s", "20 m/s", "2000 m/s"), 1),
        ),
    )
    return ClassConfig(
        className = "10A1",
        teacher = "Cô Lan",
        emergencyPerWeek = 3,
        breakAllowed = true,
        periods = periods,
        quizzes = quizzes,
    )
}

val DAY_SHORT = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
fun dayName(d: Int): String = DAY_SHORT.getOrElse(d - 1) { "?" }
