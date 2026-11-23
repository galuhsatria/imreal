package app.imreal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(items: List<Moment>, hasToday: Boolean, onCapture: () -> Unit) {
    val list = items.take(10)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        if (list.isNotEmpty()) {
            val pager = rememberPagerState { list.size }
            HorizontalPager(state = pager, pageSpacing = 12.dp, modifier = Modifier.fillMaxWidth()) { i ->
                Slide(list[i])
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.Center) {
                list.indices.forEach { i ->
                    val on = pager.currentPage == i
                    Box(
                        Modifier.padding(horizontal = 3.dp).height(6.dp).width(if (on) 18.dp else 6.dp)
                            .clip(CircleShape).background(if (on) C.text else Color(0xFF343942))
                    )
                }
            }
        }
        if (!hasToday) {
            OutlinedButton(onClick = onCapture, modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(52.dp)) {
                Text("Ambil momen hari ini", color = C.text, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Slide(m: Moment) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var swapped by remember { mutableStateOf(false) }
    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(dateLabel(m.date), color = C.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text((if (m.isVideo) "Video · " else "") + timeLabel(m.at), color = C.mute, fontSize = 13.sp)
                IconButton(onClick = { scope.launch { shareMoment(ctx, m, swapped) } }) {
                    Icon(Icons.Outlined.Share, contentDescription = "Bagikan", tint = C.mute)
                }
            }
        }
        PairView(m.media(), onSwap = { swapped = it })
        MomentCaption(m)
    }
}

@Composable
fun MomentCaption(m: Moment) {
    val t = "${m.mood} ${m.note}".trim()
    if (t.isNotEmpty()) Text(t, color = C.text, fontSize = 15.sp, modifier = Modifier.padding(start = 6.dp, top = 12.dp))
}
