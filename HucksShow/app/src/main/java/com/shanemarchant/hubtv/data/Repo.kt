package com.shanemarchant.hubtv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Loads each playlist once and keeps it in memory until "Refresh". */
object Repo {
    private val cache = ConcurrentHashMap<String, List<MediaItem>>()
    private val lock = Mutex()

    suspend fun items(source: Source): List<MediaItem> {
        cache[source.id]?.let { return it }
        return lock.withLock {
            cache[source.id] ?: load(source).also { cache[source.id] = it }
        }
    }

    private suspend fun load(source: Source): List<MediaItem> = when (source.type) {
        SourceType.M3U -> {
            val text = Http.get(source.url.trim())
            withContext(Dispatchers.Default) { M3uParser.parse(source.id, text) }
        }
        SourceType.XTREAM -> XtreamClient(source).all()
    }

    fun clear(id: String) { cache.remove(id) }

    suspend fun episodes(source: Source, seriesId: String): List<Episode> =
        XtreamClient(source).episodes(seriesId)
}
