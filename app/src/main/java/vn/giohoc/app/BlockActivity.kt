package vn.giohoc.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Màn hình hiện đè lên app bị khóa. Tự đóng khi hết giờ khóa. */
class BlockActivity : ComponentActivity() {
    private val blockedPkg = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        blockedPkg.value = intent?.getStringExtra(EXTRA_PKG)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goHome()
            }
        })
        setContent { GHTheme { BlockScreen() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        blockedPkg.value = intent.getStringExtra(EXTRA_PKG)
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    private fun openQuiz() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_QUIZ, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }

    @Composable
    private fun BlockScreen() {
        val ctx = this
        val store = remember { Store(ctx) }
        var st by remember { mutableStateOf(LockEngine.state(ctx)) }
        var askEmergency by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            while (true) {
                val s = LockEngine.state(ctx)
                st = s
                if (!s.locked) {
                    finish()
                    break
                }
                delay(1000)
            }
        }

        val white = Color.White
        val outlined = ButtonDefaults.outlinedButtonColors(contentColor = white)
        Column(
            Modifier
                .fillMaxSize()
                .background(Night)
                .safeDrawingPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(32.dp))
            Text(
                if (st.kind == Kind.SELF) "PHIÊN TỰ HỌC" else "ĐANG TRONG GIỜ HỌC",
                color = white.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.Bold,
            )
            Text(st.title, color = white, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text(st.detail, color = white.copy(alpha = 0.9f), fontSize = 16.sp)
            blockedPkg.value?.let {
                Text("${Apps.label(ctx, it)} đang bị khóa.", color = white.copy(alpha = 0.7f))
            }
            Spacer(Modifier.weight(1f))
            if (st.pendingQuiz != null) {
                Button(
                    onClick = { openQuiz() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = white, contentColor = Ink),
                ) { Text("Làm câu hỏi ngay", fontWeight = FontWeight.Bold) }
            }
            OutlinedButton(onClick = { goHome() }, modifier = Modifier.fillMaxWidth(), colors = outlined) {
                Text("Về màn hình chính")
            }
            OutlinedButton(
                onClick = {
                    runCatching {
                        startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = outlined,
            ) { Text("Gọi điện (luôn được phép)") }

            val cfg = store.config
            if (st.kind != Kind.SELF && cfg != null) {
                val left = (cfg.emergencyPerWeek - store.emergencyUsedThisWeek()).coerceAtLeast(0)
                TextButton(
                    onClick = { askEmergency = true },
                    enabled = left > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Mở khẩn 5 phút (còn $left/${cfg.emergencyPerWeek} lượt tuần này)",
                        color = if (left > 0) Gold else white.copy(alpha = 0.4f),
                    )
                }
            }
        }

        if (askEmergency) {
            AlertDialog(
                onDismissRequest = { askEmergency = false },
                title = { Text("Mở khẩn 5 phút?") },
                text = {
                    Text("Máy mở toàn bộ trong 5 phút rồi tự khóa lại. Lượt dùng được ghi lại trên máy. Gọi điện thì không cần dùng lượt này.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        store.useEmergency()
                        askEmergency = false
                        finish()
                    }) { Text("Mở khẩn") }
                },
                dismissButton = { TextButton(onClick = { askEmergency = false }) { Text("Hủy") } },
            )
        }
    }

    companion object {
        const val EXTRA_PKG = "pkg"
    }
}
