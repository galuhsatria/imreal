package app.imreal

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.time.LocalDate

/** Satu momen per hari, disimpan di filesDir/moments/<yyyy-MM-dd>/ */
data class Moment(
    val date: String,
    val isVideo: Boolean,
    val dir: File,
    val mood: String,
    val note: String,
    val at: Long,
) {
    val back get() = File(dir, "back.jpg")
    val front get() = File(dir, "front.jpg")
    val backVideo get() = File(dir, "back.mp4")
    val frontVideo get() = File(dir, "front.mp4")
}

/** Hasil jepretan yang belum disimpan (menunggu Simpan atau Ulangi). */
data class Pending(
    val isVideo: Boolean,
    val back: File,
    val front: File,
    val backVideo: File? = null,
    val frontVideo: File? = null,
)

/** Bentuk umum yang ditampilkan oleh PairView. */
data class Media(
    val isVideo: Boolean,
    val back: File,
    val front: File,
    val backVideo: File?,
    val frontVideo: File?,
)

fun Moment.media() = Media(isVideo, back, front, if (isVideo) backVideo else null, if (isVideo) frontVideo else null)
fun Pending.media() = Media(isVideo, back, front, backVideo, frontVideo)

class MomentRepo(context: Context) {
    private val app = context.applicationContext
    private val root = File(context.filesDir, "moments").apply { mkdirs() }
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Moment>> = _items.asStateFlow()

    private fun load(): List<Moment> = root.listFiles().orEmpty().mapNotNull { d ->
        val meta = File(d, "meta.json")
        if (!meta.exists()) null else runCatching {
            val j = JSONObject(meta.readText())
            Moment(d.name, j.optBoolean("video"), d, j.optString("mood"), j.optString("note"), j.optLong("at"))
        }.getOrNull()
    }.sortedByDescending { it.date }

    fun save(p: Pending, mood: String, note: String) {
        val d = File(root, LocalDate.now().toString()).apply { mkdirs() }
        p.back.copyTo(File(d, "back.jpg"), true)
        p.front.copyTo(File(d, "front.jpg"), true)
        if (p.isVideo) {
            p.backVideo!!.copyTo(File(d, "back.mp4"), true)
            p.frontVideo!!.copyTo(File(d, "front.mp4"), true)
        }
        File(d, "meta.json").writeText(
            JSONObject().put("video", p.isVideo).put("mood", mood).put("note", note.trim())
                .put("at", System.currentTimeMillis()).toString()
        )
        _items.value = load()
        MomentWidget.refresh(app)
    }

    fun delete(date: String) {
        File(root, date).deleteRecursively()
        _items.value = load()
        MomentWidget.refresh(app)
    }

    /** Backup: zip berisi semua folder tanggal. */
    fun export(out: OutputStream) {
        ZipOutputStream(out).use { z ->
            root.walkTopDown().filter { it.isFile }.forEach { f ->
                z.putNextEntry(ZipEntry(f.relativeTo(root).path.replace(File.separatorChar, '/')))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
    }

    /** Restore dari zip backup. Mengembalikan jumlah hari yang dipulihkan. */
    fun import(input: InputStream): Int {
        val ok = Regex("\\d{4}-\\d{2}-\\d{2}/(back\\.jpg|front\\.jpg|back\\.mp4|front\\.mp4|meta\\.json)")
        val rootPath = root.canonicalPath + File.separator
        val days = mutableSetOf<String>()
        ZipInputStream(input).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val target = File(root, e.name).canonicalFile
                if (!e.isDirectory && ok.matches(e.name) && target.path.startsWith(rootPath)) {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { z.copyTo(it) }
                    days += e.name.substringBefore('/')
                }
                z.closeEntry()
                e = z.nextEntry
            }
        }
        _items.value = load()
        MomentWidget.refresh(app)
        return days.size
    }
}

fun streak(items: List<Moment>): Int {
    val days = items.map { it.date }.toSet()
    var d = LocalDate.now()
    if (d.toString() !in days) d = d.minusDays(1)
    var n = 0
    while (d.toString() in days) { n++; d = d.minusDays(1) }
    return n
}
