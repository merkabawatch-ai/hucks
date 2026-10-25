package com.shanemarchant.hubtv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads each playlist once, keeps it in memory, and saves it on the device
 * so it's there instantly next time. "Refresh" downloads it again.
 */
object Repo {
    private val cache = ConcurrentHashMap<String, List<MediaItem>>()
    private val updated = ConcurrentHashMap<String, Long>()
    private val lock = Mutex()
    private var dir: File? = null

    /** What the app is doing right now, shown on the loading screens. */
    val status = MutableStateFlow("")
    fun report(text: String) { status.value = text }

    fun init(context: Context) {
        if (dir == null) dir = File(context.filesDir, "playlists").apply { mkdirs() }
    }

    private fun fileFor(id: String): File? = dir?.let { File(it, "$id.tsv") }

    suspend fun items(source: Source): List<MediaItem> {
        cache[source.id]?.let { return it }
        return lock.withLock {
            cache[source.id]?.let { return@withLock it }
            val fromDisk = withContext(Dispatchers.IO) { readSaved(source) }
            val list = fromDisk ?: download(source).also { list ->
                updated[source.id] = System.currentTimeMillis()
                withContext(Dispatchers.IO) { runCatching { save(source.id, list) } }
            }
            cache[source.id] = list
            list
        }
    }

    /** When this playlist was last downloaded (null if never). */
    fun updatedAt(id: String): Long? = updated[id]

    private suspend fun download(source: Source): List<MediaItem> = try {
        report("${source.name}: connecting to the server…")
        val result = when (source.type) {
            SourceType.M3U -> Http.stream(source.url.trim()) { input ->
                report("${source.name}: connected, reading the playlist…")
                M3uParser.parse(source.id, input.bufferedReader().lineSequence()) { n ->
                    report("${source.name}: ${"%,d".format(n)} channels read…")
                }
            }
            SourceType.XTREAM -> XtreamClient(source).all()
        }
        report("${source.name}: done, ${"%,d".format(result.size)} items")
        result
    } catch (e: OutOfMemoryError) {
        throw java.io.IOException("This playlist is too big for this device's memory")
    }

    // ---- Saving to the device: one line per item, tab-separated ----

    private const val HEADER = "HUCKS2"

    private fun clean(s: String?) = (s ?: "").replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    private fun save(id: String, list: List<MediaItem>) {
        val file = fileFor(id) ?: return
        val tmp = File(file.parentFile, "$id.tmp")
        tmp.bufferedWriter().use { w ->
            w.write("$HEADER\t${updated[id] ?: System.currentTimeMillis()}\n")
            for (m in list) {
                w.write(m.kind.name); w.write("\t")
                w.write(clean(m.group)); w.write("\t")
                w.write(clean(m.title)); w.write("\t")
                w.write(clean(m.streamUrl)); w.write("\t")
                w.write(clean(m.logo)); w.write("\t")
                w.write(clean(m.seriesId)); w.write("\t")
                w.write(clean(m.streamId)); w.write("\t")
                w.write(clean(m.epgId)); w.write("\n")
            }
        }
        tmp.renameTo(file)
    }

    private fun readSaved(source: Source): List<MediaItem>? {
        val file = fileFor(source.id) ?: return null
        if (!file.exists()) return null
        return try {
            report("${source.name}: opening saved playlist…")
            file.bufferedReader().use { r ->
                val header = r.readLine()?.split('\t') ?: return null
                if (header.firstOrNull() != HEADER) return null
                updated[source.id] = header.getOrNull(1)?.toLongOrNull() ?: file.lastModified()
                val out = ArrayList<MediaItem>()
                r.lineSequence().forEach { line ->
                    val p = line.split('\t')
                    if (p.size < 7) return@forEach
                    val kind = runCatching { Kind.valueOf(p[0]) }.getOrNull() ?: return@forEach
                    out += MediaItem(
                        sourceId = source.id,
                        title = p[2],
                        group = p[1],
                        kind = kind,
                        streamUrl = p[3].blankToNull(),
                        logo = p[4].blankToNull(),
                        seriesId = p[5].blankToNull(),
                        streamId = p[6].blankToNull(),
                        epgId = p.getOrNull(7)?.blankToNull(),
                    )
                }
                out
            }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** Forget one playlist (memory and saved copy). */
    fun clear(id: String) {
        cache.remove(id)
        updated.remove(id)
        fileFor(id)?.delete()
    }

    /** Forget everything so the next load downloads fresh lists. */
    fun clearAll() {
        cache.clear()
        updated.clear()
        dir?.listFiles()?.forEach { it.delete() }
    }

    suspend fun episodes(source: Source, seriesId: String): List<Episode> =
        XtreamClient(source).episodes(seriesId)
}
