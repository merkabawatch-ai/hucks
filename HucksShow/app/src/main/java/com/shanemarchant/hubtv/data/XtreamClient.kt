package com.shanemarchant.hubtv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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
        val catText = runCatching { Http.get(api(catAction)) }.getOrDefault("[]")
        val text = Http.get(api(action))
        return withContext(Dispatchers.Default) {
            val cats = HashMap<String, String>()
            val catArr = jsonArray(catText)
            for (i in 0 until catArr.length()) {
                val c = catArr.optJSONObject(i) ?: continue
                cats[c.optString("category_id")] = c.optString("category_name")
            }
            val arr = jsonArray(text)
            val out = ArrayList<MediaItem>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name").blankToNull() ?: "Untitled"
                val group = cats[o.optString("category_id")]?.blankToNull() ?: "Other"
                out += when (kind) {
                    Kind.LIVE -> MediaItem(
                        source.id, name, group, kind,
                        "$base/live/$user/$pass/${o.optString("stream_id")}.ts",
                        o.optString("stream_icon").blankToNull(),
                    )
                    Kind.MOVIE -> {
                        val ext = o.optString("container_extension").blankToNull() ?: "mp4"
                        MediaItem(
                            source.id, name, group, kind,
                            "$base/movie/$user/$pass/${o.optString("stream_id")}.$ext",
                            o.optString("stream_icon").blankToNull(),
                        )
                    }
                    Kind.SERIES -> MediaItem(
                        source.id, name, group, kind, null,
                        o.optString("cover").blankToNull(),
                        seriesId = o.optString("series_id"),
                    )
                }
            }
            out
        }
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
