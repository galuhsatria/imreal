package app.imreal

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

/** Widget layar utama: momen hari ini, atau ajakan untuk mengambilnya. */
class MomentWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { render(context, manager, ids) } finally { pending.finish() }
        }
    }

    companion object {
        fun refresh(ctx: Context) {
            val app = ctx.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                val mgr = AppWidgetManager.getInstance(app)
                val ids = mgr.getAppWidgetIds(ComponentName(app, MomentWidget::class.java))
                if (ids.isNotEmpty()) render(app, mgr, ids)
            }
        }

        private fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val today = LocalDate.now().toString()
            val dir = File(ctx.filesDir, "moments/$today")
            val locked = Prefs.lock(ctx)
            val bmp = if (File(dir, "meta.json").exists() && !locked)
                composeBitmap(Moment(today, false, dir, "", "", 0L), false, 360, rounded = true) else null

            val views = RemoteViews(ctx.packageName, R.layout.widget)
            if (bmp != null) {
                views.setImageViewBitmap(R.id.widget_img, bmp)
                views.setViewVisibility(R.id.widget_img, View.VISIBLE)
                views.setViewVisibility(R.id.widget_text, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_img, View.GONE)
                views.setViewVisibility(R.id.widget_text, View.VISIBLE)
                views.setTextViewText(R.id.widget_text, if (locked) "Buka ImReal" else "Ambil momen hari ini")
            }
            val i = Intent(ctx, MainActivity::class.java).putExtra("tab", if (bmp != null) 0 else 1)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(ctx, 2, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
            ids.forEach { mgr.updateAppWidget(it, views) }
        }
    }
}
