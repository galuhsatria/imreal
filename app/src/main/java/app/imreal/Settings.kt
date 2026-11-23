package app.imreal

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun SettingsDialog(repo: MomentRepo, onClose: () -> Unit, onLockChanged: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var notif by remember { mutableStateOf(Prefs.notif(ctx)) }
    var lock by remember { mutableStateOf(Prefs.lock(ctx)) }
    var msg by remember { mutableStateOf<String?>(null) }

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            msg = runCatching {
                withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)!!.use { repo.export(it) } }
                "Backup tersimpan"
            }.getOrElse { "Gagal: ${it.message}" }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            msg = runCatching {
                val n = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.use { repo.import(it) } }
                "$n hari dipulihkan"
            }.getOrElse { "Gagal: ${it.message}" }
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Selesai") } },
        title = { Text("Pengaturan") },
        text = {
            Column {
                SwitchRow("Pengingat harian", "Jam acak 09.00–21.00", notif) { on ->
                    notif = on; Prefs.setNotif(ctx, on)
                    if (on) {
                        if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                        Reminder.restart(ctx)
                    } else Reminder.cancel(ctx)
                }
                SwitchRow("Kunci aplikasi", "Sidik jari atau PIN HP", lock) { on ->
                    if (!on) {
                        lock = false; Prefs.setLock(ctx, false); onLockChanged()
                    } else if (!canLock(ctx)) {
                        msg = "Atur kunci layar (PIN, pola, atau sidik jari) di pengaturan HP dulu."
                    } else {
                        val a = ctx.findActivity()
                        if (a != null) authenticate(a, "Aktifkan kunci", onOk = {
                            lock = true; Prefs.setLock(ctx, true); onLockChanged()
                        })
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { export.launch("imreal-backup-${LocalDate.now()}.zip") }, modifier = Modifier.weight(1f)) {
                        Text("Ekspor")
                    }
                    OutlinedButton(
                        onClick = { import.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Impor") }
                }
                msg?.let { Text(it, color = C.mute, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)) }
            }
        },
    )
}

@Composable
private fun SwitchRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(sub, color = C.mute, fontSize = 12.sp)
        }
        Switch(checked = on, onCheckedChange = onChange)
    }
}
