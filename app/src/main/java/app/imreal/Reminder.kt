package app.imreal

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** Pengingat harian di jam acak antara 09.00 dan 21.00. */
object Reminder {
    private const val WORK = "imreal-daily"
    private const val CHANNEL = "daily"
    private const val START_HOUR = 9
    private const val END_HOUR = 21

    fun ensure(ctx: Context) {
        if (Prefs.notif(ctx)) enqueue(ctx, false, ExistingWorkPolicy.KEEP) else cancel(ctx)
    }

    fun restart(ctx: Context) = enqueue(ctx, false, ExistingWorkPolicy.REPLACE)

    fun cancel(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK)
    }

    fun enqueue(ctx: Context, tomorrow: Boolean, policy: ExistingWorkPolicy) {
        val delay = Duration.between(LocalDateTime.now(), nextTime(tomorrow)).toMillis().coerceAtLeast(60_000L)
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(WORK, policy, req)
    }

    private fun nextTime(forceTomorrow: Boolean): LocalDateTime {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val todayStart = today.atTime(START_HOUR, 0)
        val todayEnd = today.atTime(END_HOUR, 0)
        val from = if (now.isAfter(todayStart)) now.plusMinutes(1) else todayStart
        val useToday = !forceTomorrow && from.isBefore(todayEnd.minusMinutes(5))
        val day = if (useToday) today else today.plusDays(1)
        val lo = if (useToday) from else day.atTime(START_HOUR, 0)
        val hi = day.atTime(END_HOUR, 0)
        return lo.plusSeconds(Random.nextLong(Duration.between(lo, hi).seconds))
    }

    fun notify(c: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            c.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL, "Pengingat harian", NotificationManager.IMPORTANCE_DEFAULT))
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val i = Intent(c, MainActivity::class.java).putExtra("tab", 1)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(c, 1, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Waktunya ImReal")
            .setContentText("Ambil momen hari ini.")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(c).notify(1, n)
    }
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        if (Prefs.notif(c)) {
            if (!File(c.filesDir, "moments/${LocalDate.now()}/meta.json").exists()) Reminder.notify(c)
            Reminder.enqueue(c, true, ExistingWorkPolicy.APPEND_OR_REPLACE)
        }
        return Result.success()
    }
}
