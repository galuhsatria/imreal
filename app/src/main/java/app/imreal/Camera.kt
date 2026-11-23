package app.imreal

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
fun CameraScreen(
    repo: MomentRepo,
    hasToday: Boolean,
    granted: Boolean,
    onRequestPermission: () -> Unit,
    pending: Pending?,
    onPending: (Pending?) -> Unit,
    onSaved: () -> Unit,
) {
    when {
        hasToday -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Sudah diambil hari ini", color = C.mute) }
        !granted -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Button(onClick = onRequestPermission) { Text("Izinkan kamera") }
        }
        pending != null -> Review(repo, pending, onRetake = { onPending(null) }, onSaved = onSaved)
        else -> Viewfinder(onResult = { onPending(it) })
    }
}

@Composable
private fun Viewfinder(onResult: (Pending) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val cam = remember { LifecycleCameraController(context) }
    var video by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(owner) {
        cam.bindToLifecycle(owner)
        onDispose { cam.unbind() }
    }
    LaunchedEffect(video) {
        cam.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        cam.setEnabledUseCases(if (video) CameraController.VIDEO_CAPTURE else CameraController.IMAGE_CAPTURE)
    }

    fun shoot() {
        busy = true; error = null
        scope.launch {
            try {
                val dir = File(context.cacheDir, "pending").apply { deleteRecursively(); mkdirs() }
                val exec = ContextCompat.getMainExecutor(context)
                val photos = mutableListOf<File>()
                val clips = mutableListOf<File>()
                val sides = listOf("back" to ("Belakang" to CameraSelector.DEFAULT_BACK_CAMERA),
                    "front" to ("Depan" to CameraSelector.DEFAULT_FRONT_CAMERA))
                for ((key, side) in sides) {
                    val (label, selector) = side
                    status = label
                    cam.cameraSelector = selector
                    delay(900)
                    if (video) {
                        val f = File(dir, "$key.mp4")
                        recordClip(cam, f, 3, exec) { status = "$label $it" }
                        clips += f
                        photos += poster(f, File(dir, "$key.jpg"))
                    } else {
                        val f = File(dir, "$key.jpg")
                        takePhoto(cam, f, exec)
                        photos += f
                    }
                }
                onResult(Pending(video, photos[0], photos[1], clips.getOrNull(0), clips.getOrNull(1)))
            } catch (e: Exception) {
                error = "Gagal mengambil: ${e.message}"
            } finally {
                busy = false; status = ""
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.clip(CircleShape).background(C.panel).border(1.dp, C.line, CircleShape).padding(4.dp)
        ) {
            ModePill("Foto", !video) { if (!busy) video = false }
            ModePill("Video", video) { if (!busy) video = true }
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(30.dp)).background(C.panel)) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        controller = cam
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            Text(
                status, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
            )
        }
        Spacer(Modifier.height(22.dp))
        Box(
            Modifier.size(78.dp).clip(CircleShape).border(4.dp, C.text, CircleShape).padding(6.dp)
                .clip(CircleShape).background(if (video) Color(0xFFFF5D5D) else C.text)
                .clickable(enabled = !busy) { shoot() }
        )
        error?.let { Text(it, color = Color(0xFFFF8A8A), fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp)) }
    }
}

@Composable
private fun ModePill(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape).background(if (on) C.text else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 8.dp)
    ) { Text(label, color = if (on) C.bg else C.mute, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
}

@Composable
private fun Review(repo: MomentRepo, p: Pending, onRetake: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var mood by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp)) {
        PairView(p.media())
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            MOODS.forEach { m ->
                val on = mood == m
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(if (on) Color(0xFF2A2E36) else C.panel)
                        .border(1.dp, if (on) C.text else C.line, CircleShape)
                        .clickable { mood = if (on) "" else m },
                    contentAlignment = Alignment.Center,
                ) { Text(m, fontSize = 22.sp) }
            }
        }
        OutlinedTextField(
            value = note, onValueChange = { if (it.length <= 80) note = it },
            placeholder = { Text("Catatan") }, singleLine = true, shape = CircleShape,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onRetake, enabled = !saving, modifier = Modifier.weight(1f).height(54.dp)) {
                Text("Ulangi", color = C.text, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = {
                    saving = true
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.save(p, mood, note) }
                        onSaved()
                    }
                },
                enabled = !saving, modifier = Modifier.weight(1f).height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.text, contentColor = C.bg),
            ) { Text("Simpan", fontWeight = FontWeight.Bold) }
        }
    }
}

private suspend fun takePhoto(c: LifecycleCameraController, f: File, exec: Executor) =
    suspendCancellableCoroutine<Unit> { k ->
        c.takePicture(
            ImageCapture.OutputFileOptions.Builder(f).build(), exec,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(r: ImageCapture.OutputFileResults) { k.resume(Unit) }
                override fun onError(e: ImageCaptureException) { k.resumeWithException(e) }
            },
        )
    }

@SuppressLint("MissingPermission")
private suspend fun recordClip(c: LifecycleCameraController, f: File, secs: Int, exec: Executor, tick: (Int) -> Unit) {
    val done = CompletableDeferred<Unit>()
    val rec = c.startRecording(FileOutputOptions.Builder(f).build(), AudioConfig.AUDIO_DISABLED, exec) { ev ->
        if (ev is VideoRecordEvent.Finalize) {
            if (ev.hasError()) done.completeExceptionally(RuntimeException("kode ${ev.error}")) else done.complete(Unit)
        }
    }
    for (s in secs downTo 1) { tick(s); delay(1000) }
    rec.stop()
    done.await()
}

private suspend fun poster(video: File, out: File): File = withContext(Dispatchers.IO) {
    val r = MediaMetadataRetriever()
    r.setDataSource(video.path)
    val bmp = r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    r.release()
    out.outputStream().use { bmp?.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    out
}
