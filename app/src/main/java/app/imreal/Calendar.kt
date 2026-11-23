package app.imreal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun CalendarScreen(items: List<Moment>, repo: MomentRepo) {
    var ym by remember { mutableStateOf(YearMonth.now()) }
    var open by remember { mutableStateOf<String?>(null) }
    val byDate = remember(items) { items.associateBy { it.date } }
    val today = LocalDate.now()
    val lead = ym.atDay(1).dayOfWeek.value - 1
    val len = ym.lengthOfMonth()
    val rows = (lead + len + 6) / 7

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { ym = ym.minusMonths(1) }) { Text("‹", color = C.text, fontSize = 22.sp) }
            Text(
                ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", ID)).replaceFirstChar { it.uppercase() },
                color = C.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { ym = ym.plusMonths(1) }, enabled = ym < YearMonth.now()) {
                Text("›", color = C.text, fontSize = 22.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min").forEach {
                Text(it, color = C.mute, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth().padding(bottom = 5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (c in 0..6) {
                    val n = r * 7 + c - lead + 1
                    Box(Modifier.weight(1f)) {
                        if (n in 1..len) {
                            val date = ym.atDay(n)
                            DayCell(n, date, today, byDate[date.toString()]) { open = date.toString() }
                        } else Spacer(Modifier.aspectRatio(3f / 4f))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 13.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat(items.count { YearMonth.from(LocalDate.parse(it.date)) == ym }.toString(), "momen bulan ini", Modifier.weight(1f))
            Stat(streak(items).toString(), "hari beruntun", Modifier.weight(1f))
        }
    }

    open?.let { d ->
        byDate[d]?.let { m -> Detail(m, onClose = { open = null }, onDelete = { repo.delete(d); open = null }) }
    }
}

@Composable
private fun DayCell(n: Int, date: LocalDate, today: LocalDate, m: Moment?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(11.dp)
    val future = date.isAfter(today)
    val ring = when {
        date == today -> Modifier.border(2.dp, C.accent, shape)
        future -> Modifier.border(1.dp, C.line, shape)
        else -> Modifier
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(shape)
            .background(if (future) Color.Transparent else C.panel)
            .then(if (m != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(ring)
    ) {
        if (m != null) {
            AsyncImage(m.back, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            AsyncImage(
                m.front, null, contentScale = ContentScale.Crop,
                modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp).fillMaxWidth(0.34f).aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(5.dp)).border(1.5.dp, Color.Black, RoundedCornerShape(5.dp)),
            )
            if (m.isVideo) Text("▶", color = Color.White, fontSize = 9.sp, modifier = Modifier.align(Alignment.BottomStart).padding(5.dp))
        }
        Text(
            n.toString(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = when { m != null -> Color.White; future -> Color(0xFF4A4F57); else -> C.mute },
            modifier = Modifier.padding(start = 5.dp, top = 4.dp),
        )
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(18.dp)).padding(16.dp, 14.dp)) {
        Text(value, color = C.text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = C.mute, fontSize = 13.sp)
    }
}

@Composable
private fun Detail(m: Moment, onClose: () -> Unit, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    var swapped by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = C.bg) {
            Column(Modifier.verticalScroll(rememberScrollState()).statusBarsPadding().padding(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onClose) { Text("Tutup", color = C.text) }
                    Text(
                        fullDate(LocalDate.parse(m.date)), color = C.text, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(64.dp))
                }
                PairView(m.media(), onSwap = { swapped = it })
                MomentCaption(m)
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { scope.launch { shareMoment(ctx, m, swapped) } },
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text("Bagikan", color = C.text, fontWeight = FontWeight.SemiBold) }
                    OutlinedButton(onClick = { confirm = true }, modifier = Modifier.weight(1f).height(52.dp)) {
                        Text("Hapus", color = Color(0xFFFF8A8A), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Hapus momen ini?") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Batal") } },
        )
    }
}
