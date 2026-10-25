package com.shanemarchant.hubtv.data

import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URLEncoder

fun normaliseServer(raw: String): String {
    var u = raw.trim()
    if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) u = "http://$u"
    return u.substringBefore("/player_api.php").substringBefore("/get.php").trimEnd('/')
}

private fun jsonArray(text: String): JSONArray =
    text.trim().let { if (it.startsWith("[")) runCatching { JSONArray(it) }.getOrElse { JSONArray() } else JSONArray() }

class XtreamClient(private val source: Source) {
    private val base = normaliseServer(source.url)
    private val user = source.username
    private val pass = source.password

    private val counts = java.util.concurrent.ConcurrentHashMap<Kind, Int>()

    private fun progress(kind: Kind, n: Int) {
        counts[kind] = n
        val parts = listOf(Kind.LIVE to "Live", Kind.MOVIE to "Movies", Kind.SERIES to "Series")
            .mapNotNull { (k, label) -> counts[k]?.let { "$label ${"%,d".format(it)}" } }
        Repo.report("${source.name}: " + parts.joinToString(" · ") + " loaded…")
    }

    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")

    private fun api(action: String?, extra: String = ""): String = buildString {
        append(base).append("/player_api.php?username=").append(enc(user)).append("&password=").append(enc(pass))
        if (action != null) append("&action=").append(action)
        append(extra)
    }

    suspend fun checkLogin(): Boolean {
        val o = JSONObject(Http.get(api(null)))
        val info = o.optJSONObject("user_info") ?: return false
        return info.optInt("auth", 0) == 1
    }

    /** Loads live, movies and series. One failing section doesn't kill the others. */
    suspend fun all(): List<MediaItem> = supervisorScope {
        Repo.report("${source.name}: logged in, asking for channel lists…")
        val jobs = listOf(
            async { streams("get_live_categories", "get_live_streams", Kind.LIVE) },
            async { streams("get_vod_categories", "get_vod_streams", Kind.MOVIE) },
            async { streams("get_series_categories", "get_series", Kind.SERIES) },
        )
        val results = jobs.map { runCatching { it.await() } }
        if (results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
        results.flatMap { it.getOrDefault(emptyList()) }
    }

    private suspend fun streams(catAction: String, action: String, kind: Kind): List<MediaItem> {
        val cats = HashMap<String, String>()
        runCatching {
            val catArr = jsonArray(Http.get(api(catAction)))
            for (i in 0 until catArr.length()) {
                val c = catArr.optJSONObject(i) ?: continue
                cats[c.optString("category_id")] = c.optString("category_name")
            }
        }
        progress(kind, 0)
        // Stream lists can be huge, so read them item by item instead of all at once.
        return Http.stream(api(action)) { input -> readStreams(input, kind, cats) }.also { progress(kind, it.size) }
    }

    private fun JsonReader.str(): String = when (peek()) {
        JsonToken.NULL -> { nextNull(); "" }
        JsonToken.STRING, JsonToken.NUMBER -> nextString()
        JsonToken.BOOLEAN -> nextBoolean().toString()
        else -> { skipValue(); "" }
    }

    private fun readStreams(input: InputStream, kind: Kind, cats: Map<String, String>): List<MediaItem> {
        val out = ArrayList<MediaItem>()
        val r = JsonReader(InputStreamReader(input, Charsets.UTF_8))
        r.isLenient = true
        if (r.peek() != JsonToken.BEGIN_ARRAY) return out
        r.beginArray()
        while (r.hasNext()) {
            if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); continue }
            var name = ""; var id = ""; var cat = ""; var icon = ""; var ext = ""; var seriesId = ""; var cover = ""; var epg = ""
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "name" -> name = r.str()
                    "stream_id" -> id = r.str()
                    "category_id" -> cat = r.str()
                    "stream_icon" -> icon = r.str()
                    "container_extension" -> ext = r.str()
                    "series_id" -> seriesId = r.str()
                    "cover" -> cover = r.str()
                    "epg_channel_id" -> epg = r.str()
                    else -> r.skipValue()
                }
            }
            r.endObject()
            if (out.size % 500 == 0) progress(kind, out.size)
            val title = name.blankToNull() ?: "Untitled"
            val group = cats[cat]?.blankToNull() ?: "Other"
            out += when (kind) {
                Kind.LIVE -> MediaItem(source.id, title, group, kind, "$base/live/$user/$pass/$id.ts", icon.blankToNull(), streamId = id, epgId = epg.blankToNull())
                Kind.MOVIE -> MediaItem(
                    source.id, title, group, kind,
                    "$base/movie/$user/$pass/$id.${ext.blankToNull() ?: "mp4"}", icon.blankToNull(),
                )
                Kind.SERIES -> MediaItem(source.id, title, group, kind, null, cover.blankToNull(), seriesId = seriesId)
            }
        }
        r.endArray()
        return out
    }

    suspend fun episodes(seriesId: String): List<Episode> {
        val o = JSONObject(Http.get(api("get_series_info", "&series_id=" + enc(seriesId))))
        val out = ArrayList<Episode>()
        fun add(e: JSONObject, seasonKey: String) {
            val ext = e.optString("container_extension").blankToNull() ?: "mp4"
            out += Episode(
                title = e.optString("title").blankToNull() ?: "Episode",
                season = e.optInt("season", seasonKey.toIntOrNull() ?: 0),
                number = e.optInt("episode_num", 0),
                streamUrl = "$base/series/$user/$pass/${e.optString("id")}.$ext",
            )
        }
        when (val eps = o.opt("episodes")) {
            is JSONObject -> eps.keys().forEach { key ->
                val a = eps.optJSONArray(key) ?: return@forEach
                for (i in 0 until a.length()) a.optJSONObject(i)?.let { add(it, key) }
            }
            is JSONArray -> for (i in 0 until eps.length()) {
                when (val x = eps.opt(i)) {
                    is JSONArray -> for (j in 0 until x.length()) x.optJSONObject(j)?.let { add(it, (i + 1).toString()) }
                    is JSONObject -> add(x, "")
                }
            }
        }
        return out.sortedWith(compareBy({ it.season }, { it.number }))
    }
}
