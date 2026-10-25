package com.shanemarchant.hubtv.data

import android.net.Uri
import android.util.Base64
import android.util.Xml
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class Programme(val title: String, val description: String, val start: Long, val end: Long)

/**
 * TV guide. The provider's full guide (xmltv.php) is downloaded once in the background
 * and gives "now" for every channel; get_short_epg fills gaps for single channels.
 */
object Epg {
    private class Cached(val fetchedAt: Long, val programmes: List<Programme>)
    private val cache = ConcurrentHashMap<String, Cached>()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    /** Full guide: "sourceId|epgId" -> programmes in the next few hours. */
    private val guide = ConcurrentHashMap<String, List<Programme>>()
    private val guideLoadedAt = ConcurrentHashMap<String, Long>()

    /** Bumped whenever the full guide changes, so the screen redraws. */
    val version = MutableStateFlow(0)
    /** Shown at the top of Live TV while the guide downloads. */
    val status = MutableStateFlow("")

    private class Server(val base: String, val user: String, val pass: String)

    private fun serverFor(source: Source): Server? = when (source.type) {
        SourceType.XTREAM -> Server(normaliseServer(source.url), source.username, source.password)
        SourceType.M3U -> {
            if (!source.url.contains("get.php", ignoreCase = true)) null
            else {
                val uri = runCatching { Uri.parse(source.url.trim()) }.getOrNull()
                val user = uri?.getQueryParameter("username")
                val pass = uri?.getQueryParameter("password")
                if (user == null || pass == null) null else Server(normaliseServer(source.url), user, pass)
            }
        }
    }

    private fun streamIdFor(source: Source, item: MediaItem): String? = when (source.type) {
        SourceType.XTREAM -> item.streamId
        SourceType.M3U -> item.streamUrl?.substringAfterLast('/')?.substringBefore('.')
            ?.takeIf { s -> s.isNotEmpty() && s.all { it.isDigit() } }
    }

    fun hasGuide(source: Source, item: MediaItem) =
        item.kind == Kind.LIVE && serverFor(source) != null &&
            (item.epgId != null || streamIdFor(source, item) != null)

    // ---------- Full guide for every channel ----------

    private fun upcoming(list: List<Programme>?): List<Programme>? {
        if (list == null) return null
        val now = System.currentTimeMillis()
        return list.filter { it.end > now }.takeIf { it.isNotEmpty() }
    }

    private fun fromGuide(item: MediaItem): List<Programme>? =
        item.epgId?.let { upcoming(guide["${item.sourceId}|$it"]) }

    /** What's on now on this channel, from the full guide (no network). */
    fun nowOn(item: MediaItem): Programme? =
        fromGuide(item)?.firstOrNull()?.takeIf { it.start <= System.currentTimeMillis() }

    /** Downloads the provider's full guide unless it was loaded in the last 3 hours. */
    suspend fun loadGuide(source: Source, items: List<MediaItem>) {
        val server = serverFor(source) ?: return
        val last = guideLoadedAt[source.id]
        if (last != null && System.currentTimeMillis() - last < 3 * 3_600_000L) return
        val wanted = items.mapNotNullTo(HashSet()) { if (it.sourceId == source.id && it.kind == Kind.LIVE) it.epgId else null }
        if (wanted.isEmpty()) return

        status.value = "Loading TV guide…"
        val url = "${server.base}/xmltv.php?username=${enc(server.user)}&password=${enc(server.pass)}"
        try {
            val found = Http.stream(url) { input -> parseXmltv(input, wanted) }
            found.forEach { (id, list) -> guide["${source.id}|$id"] = list.sortedBy { it.start } }
            guideLoadedAt[source.id] = System.currentTimeMillis()
            version.value = version.value + 1
            status.value = ""
        } catch (e: Exception) {
            status.value = "TV guide unavailable right now"
        } catch (e: OutOfMemoryError) {
            status.value = "TV guide too big for this device"
        }
    }

    private val xmltvTime = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
    private val xmltvTimeNoZone = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)

    private fun parseTime(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        val t = s.trim()
        return runCatching { xmltvTime.parse(t)?.time }.getOrNull()
            ?: runCatching { xmltvTimeNoZone.parse(t.take(14))?.time }.getOrNull()
    }

    /** Reads the guide piece by piece, keeping only our channels and the next 4 hours. */
    private fun parseXmltv(input: InputStream, wanted: Set<String>): Map<String, List<Programme>> {
        val now = System.currentTimeMillis()
        val until = now + 4 * 3_600_000L
        val out = HashMap<String, MutableList<Programme>>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        var seen = 0
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "programme") {
                val channel = parser.getAttributeValue(null, "channel")
                val start = parseTime(parser.getAttributeValue(null, "start"))
                val stop = parseTime(parser.getAttributeValue(null, "stop"))
                var title = ""
                var desc = ""
                var t = parser.next()
                while (t != XmlPullParser.END_DOCUMENT && !(t == XmlPullParser.END_TAG && parser.name == "programme")) {
                    if (t == XmlPullParser.START_TAG) {
                        when (parser.name) {
                            "title" -> title = runCatching { parser.nextText() }.getOrDefault("")
                            "desc" -> desc = runCatching { parser.nextText() }.getOrDefault("")
                        }
                    }
                    t = parser.next()
                }
                if (channel != null && start != null && stop != null && channel in wanted && stop > now && start < until) {
                    out.getOrPut(channel) { ArrayList() }.add(Programme(title.trim(), desc.trim(), start, stop))
                }
                seen++
                if (seen % 5000 == 0) status.value = "Loading TV guide… ${"%,d".format(seen)} shows read"
            }
            event = parser.next()
        }
        return out
    }

    fun clear() {
        guide.clear()
        guideLoadedAt.clear()
        cache.clear()
        version.value = version.value + 1
    }

    // ---------- Now/next for one channel ----------

    private fun fresh(c: Cached?): List<Programme>? {
        if (c == null) return null
        val now = System.currentTimeMillis()
        if (now - c.fetchedAt > 15 * 60_000) return null
        val upcoming = c.programmes.filter { it.end > now }
        return if (c.programmes.isNotEmpty() && upcoming.isEmpty()) null else upcoming.take(2)
    }

    private fun shortKey(source: Source, item: MediaItem) = "${source.id}|short|${streamIdFor(source, item)}"

    /** Already-loaded now/next, without going to the network. */
    fun cached(source: Source, item: MediaItem): List<Programme>? =
        fromGuide(item)?.take(2) ?: fresh(cache[shortKey(source, item)])

    suspend fun nowNext(source: Source, item: MediaItem): List<Programme> {
        fromGuide(item)?.let { if (it.size >= 2) return it.take(2) }
        fresh(cache[shortKey(source, item)])?.let { return it }
        val server = serverFor(source) ?: return emptyList()
        val streamId = streamIdFor(source, item) ?: return fromGuide(item)?.take(2) ?: emptyList()
        val url = "${server.base}/player_api.php?username=${enc(server.user)}&password=${enc(server.pass)}" +
            "&action=get_short_epg&stream_id=${enc(streamId)}&limit=4"
        val json = JSONObject(Http.get(url))
        val arr = json.optJSONArray("epg_listings") ?: JSONArray()
        val list = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val start = o.optString("start_timestamp").toLongOrNull()?.times(1000) ?: return@mapNotNull null
            val end = o.optString("stop_timestamp").toLongOrNull()?.times(1000) ?: return@mapNotNull null
            Programme(decode(o.optString("title")), decode(o.optString("description")), start, end)
        }.sortedBy { it.start }
        cache[shortKey(source, item)] = Cached(System.currentTimeMillis(), list)
        return list.filter { it.end > System.currentTimeMillis() }.take(2)
    }

    fun time(ms: Long): String = timeFormat.format(Date(ms))

    /** Two-line summary for the player overlay. */
    fun summary(p: List<Programme>): String? {
        if (p.isEmpty()) return null
        val now = p[0]
        val next = p.getOrNull(1)
        return "Now  ${time(now.start)}–${time(now.end)}  ${now.title}" +
            (next?.let { "\nNext  ${time(it.start)}  ${it.title}" } ?: "")
    }

    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")

    /** Xtream sends short-guide text base64-encoded. */
    private fun decode(s: String): String {
        if (s.isBlank()) return ""
        return runCatching { String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8).trim() }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: s
    }
}
