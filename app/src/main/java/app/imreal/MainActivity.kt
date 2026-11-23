package app.imreal

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

class MainActivity : FragmentActivity() {
    internal val openTab = mutableIntStateOf(-1)
    internal val locked = mutableStateOf(false)
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = MomentRepo(applicationContext)
        openTab.intValue = intent.getIntExtra("tab", -1)
        locked.value = Prefs.lock(this)
        applySecure()
        Reminder.ensure(this)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = C.bg, surface = C.panel, primary = C.accent,
                    onBackground = C.text, onSurface = C.text,
                )
            ) { Root(repo, this) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openTab.intValue = intent.getIntExtra("tab", -1)
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // Kunci lagi bila ditinggal lebih dari 30 detik.
        if (stoppedAt > 0 && Prefs.lock(this) && SystemClock.elapsedRealtime() - stoppedAt > 30_000) locked.value = true
    }

    /** Sembunyikan isi aplikasi dari screenshot dan daftar aplikasi terbaru saat kunci aktif. */
    fun applySecure() {
        if (Prefs.lock(this)) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

@Composable
private fun Root(repo: MomentRepo, a: MainActivity) {
    if (a.locked.value) LockScreen(a) { a.locked.value = false } else Main(repo, a)
}

@Composable
private fun LockScreen(a: MainActivity, onUnlock: () -> Unit) {
    fun go() = authenticate(a, "Buka ImReal", onUnlock)
    LaunchedEffect(Unit) { go() }
    Box(Modifier.fillMaxSize().background(C.bg), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ImReal", color = C.text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { go() }) { Text("Buka kunci") }
        }
    }
}

@Composable
private fun Main(repo: MomentRepo, a: MainActivity) {
    val context = LocalContext.current
    val items by repo.items.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val hasToday = items.any { it.date == LocalDate.now().toString() }
    val s = streak(items)

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && Prefs.notif(context) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(a.openTab.intValue) {
        val t = a.openTab.intValue
        if (t >= 0) { tab = t; a.openTab.intValue = -1 }
    }

    Scaffold(
        containerColor = C.bg,
        topBar = {
            Row(Modifier.statusBarsPadding().padding(22.dp, 12.dp, 10.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("ImReal", color = C.text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                Spacer(Modifier.weight(1f))
                if (s > 0) {
                    Text(
                        "🔥 $s", color = C.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(CircleShape).border(1.dp, C.line, CircleShape).padding(12.dp, 6.dp),
                    )
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Pengaturan", tint = C.mute)
                }
            }
        },
        bottomBar = {
            Column {
                HorizontalDivider(color = C.line)
                NavigationBar(containerColor = C.bg) {
                    listOf(
                        Icons.Outlined.Home to "Beranda",
                        Icons.Outlined.PhotoCamera to "Kamera",
                        Icons.Outlined.CalendarMonth to "Kalender",
                    ).forEachIndexed { i, (icon, desc) ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { tab = i }, alwaysShowLabel = false,
                            icon = { Icon(icon, contentDescription = desc) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = C.text, unselectedIconColor = C.mute,
                                indicatorColor = Color.Transparent,
                            ),
                        )
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> HomeScreen(items, hasToday) { tab = 1 }
                1 -> CameraScreen(
                    repo, hasToday, granted,
                    onRequestPermission = { camPerm.launch(Manifest.permission.CAMERA) },
                    pending = pending, onPending = { pending = it },
                    onSaved = { pending = null; tab = 0 },
                )
                else -> CalendarScreen(items, repo)
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            repo, onClose = { showSettings = false },
            onLockChanged = { a.applySecure(); MomentWidget.refresh(context) },
        )
    }
}
