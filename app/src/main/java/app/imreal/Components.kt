package app.imreal

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object C {
    val bg = Color(0xFF0D0E11)
    val panel = Color(0xFF17191E)
    val line = Color(0xFF262A31)
    val text = Color(0xFFF3F0EA)
    val mute = Color(0xFF8A8F98)
    val accent = Color(0xFF8B9AFF)
}

val ID = Locale("id", "ID")
val MOODS = listOf("😀", "😌", "😴", "😎", "🥲")

fun dateLabel(date: String): String {
    val d = LocalDate.parse(date)
    val today = LocalDate.now()
    return when (d) {
        today -> "Hari ini"
        today.minusDays(1) -> "Kemarin"
        else -> fullDate(d)
    }
}

fun fullDate(d: LocalDate): String =
    d.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", ID)).replaceFirstChar { it.uppercase() }

fun timeLabel(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

/** Foto besar + kotak kecil rounded ber-border hitam. Ketuk kotak kecil untuk membalik. */
@Composable
fun PairView(m: Media, modifier: Modifier = Modifier, onSwap: (Boolean) -> Unit = {}) {
    var swapped by remember(m) { mutableStateOf(false) }
    LaunchedEffect(swapped) { onSwap(swapped) }
    val bigImg = if (swapped) m.front else m.back
    val bigVid = if (swapped) m.frontVideo else m.backVideo
    val smallImg = if (swapped) m.back else m.front
    val smallVid = if (swapped) m.backVideo else m.frontVideo
    val small = RoundedCornerShape(18.dp)

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(30.dp))
            .background(C.panel)
    ) {
        MediaSlot(bigImg, bigVid, Modifier.fillMaxSize())
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
                .fillMaxWidth(0.3f)
                .aspectRatio(3f / 4f)
                .clip(small)
                .background(Color.Black)
                .border(3.dp, Color.Black, small)
                .clickable { swapped = !swapped }
        ) {
            MediaSlot(smallImg, smallVid, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun MediaSlot(image: File, video: File?, modifier: Modifier) {
    if (video != null) {
        key(video.path) { LoopVideo(video, modifier) }
    } else {
        AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    }
}

@OptIn(UnstableApi::class)
@Composable
fun LoopVideo(file: File, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val player = remember(file.path) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = {
            PlayerView(it).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                this.player = player
            }
        },
        update = { it.player = player },
        modifier = modifier,
    )
}
