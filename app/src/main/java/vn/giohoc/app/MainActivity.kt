package vn.giohoc.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private val quizRequest = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        quizRequest.value = intent?.getBooleanExtra(EXTRA_QUIZ, false) == true
        setContent { GHTheme { Root(quizRequest) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_QUIZ, false)) quizRequest.value = true
    }

    companion object {
        const val EXTRA_QUIZ = "quiz"
    }
}

@Composable
fun Root(quizRequest: MutableState<Boolean>) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    var role by remember { mutableStateOf(store.role) }
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        when (role) {
            null -> RolePicker { r ->
                store.role = r
                role = r
            }
            ROLE_STUDENT -> StudentApp(store, quizRequest) {
                store.role = null
                role = null
            }
            else -> TeacherApp(store) {
                store.role = null
                role = null
            }
        }
    }
}

@Composable
fun RolePicker(onPick: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("Giờ Học", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Blue)
        Note("Khóa app gây xao nhãng trong giờ học theo thời khóa biểu, hết tiết làm câu hỏi ôn tập rồi mới mở máy.")
        Button(onClick = { onPick(ROLE_STUDENT) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Tôi là học sinh", fontSize = 16.sp)
        }
        OutlinedButton(onClick = { onPick(ROLE_TEACHER) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Tôi là giáo viên", fontSize = 16.sp)
        }
        Note("Bản 0.1: dữ liệu lưu trên từng máy, chưa có máy chủ. Giáo viên gửi mã lớp cho học sinh qua Zalo.")
    }
}
