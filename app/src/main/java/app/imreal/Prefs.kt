package app.imreal

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("imreal", Context.MODE_PRIVATE)
    fun notif(c: Context) = sp(c).getBoolean("notif", true)
    fun setNotif(c: Context, v: Boolean) = sp(c).edit().putBoolean("notif", v).apply()
    fun lock(c: Context) = sp(c).getBoolean("lock", false)
    fun setLock(c: Context, v: Boolean) = sp(c).edit().putBoolean("lock", v).apply()
}
