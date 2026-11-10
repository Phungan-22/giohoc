package vn.giohoc.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val ROLE_STUDENT = "student"
const val ROLE_TEACHER = "teacher"

val Blue = Color(0xFF2A55C9)
val Ink = Color(0xFF16213A)
val Muted = Color(0xFF5F6A80)
val Paper = Color(0xFFF2F4F8)
val Green = Color(0xFF17905A)
val Red = Color(0xFFCF3A32)
val Amber = Color(0xFFC27A0B)
val Night = Color(0xFF1D2A44)
val Gold = Color(0xFFF3A531)

@Composable
fun GHTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Blue,
            onPrimary = Color.White,
            background = Paper,
            surface = Color.White,
            onSurface = Ink,
            onBackground = Ink,
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Paper) { content() }
    }
}

@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink)
            content()
        }
    }
}

@Composable
fun Tag(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun Note(text: String) {
    Text(text, color = Muted, fontSize = 13.sp)
}

@Composable
fun ScreenTitle(title: String, sub: String? = null) {
    Column {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
        if (sub != null) Note(sub)
    }
}

/** Nút chọn một trong nhiều (thứ trong tuần, số phút...). */
@Composable
fun Choice(selected: Boolean, label: String, onClick: () -> Unit) {
    val pad = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
    if (selected) {
        Button(onClick = onClick, contentPadding = pad) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, contentPadding = pad) { Text(label) }
    }
}
