package com.shanemarchant.hubtv.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Free film/TV database: finds titles and which streaming services carry them. */
object Tmdb {
    private const val BASE = "https://api.themoviedb.org/3"

    private suspend fun call(key: String, path: String): JSONObject {
        // Long keys are "Read Access Tokens" (sent as a header); short ones are v3 API keys.
        val bearer = key.length > 40
        val url = BASE + path + if (bearer) "" else ((if ('?' in path) "&" else "?") + "api_key=" + key)
        val headers = if (bearer) mapOf("Authorization" to "Bearer $key") else emptyMap()
        return JSONObject(Http.get(url, headers, retry = false))
    }

    suspend fun search(key: String, query: String, region: String): List<TmdbResult> = coroutineScope {
        val js = call(key, "/search/multi?include_adult=false&query=" + URLEncoder.encode(query, "UTF-8"))
        val arr = js.optJSONArray("results") ?: JSONArray()
        val found = (0 until arr.length())
            .mapNotNull { arr.optJSONObject(it) }
            .filter { it.optString("media_type") == "movie" || it.optString("media_type") == "tv" }
            .take(8)
            .map { o ->
                val type = o.optString("media_type")
                val movie = type == "movie"
                TmdbResult(
                    id = o.optInt("id"),
                    type = type,
                    title = o.optString(if (movie) "title" else "name"),
                    year = o.optString(if (movie) "release_date" else "first_air_date").take(4),
                    poster = o.optString("poster_path").blankToNull()?.let { "https://image.tmdb.org/t/p/w185$it" },
                )
            }
        found.map { r ->
            async { r.copy(providers = runCatching { providers(key, r.type, r.id, region) }.getOrDefault(emptyList())) }
        }.awaitAll()
    }

    /** Films out now in the user's country, falling back to this week's trending. */
    suspend fun latest(key: String, region: String): List<TmdbResult> {
        var arr = call(key, "/movie/now_playing?region=" + region.uppercase()).optJSONArray("results")
        if (arr == null || arr.length() == 0) arr = call(key, "/trending/movie/week").optJSONArray("results")
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            .filter { it.optString("poster_path").blankToNull() != null }
            .take(20)
            .map { o ->
                TmdbResult(
                    id = o.optInt("id"),
                    type = "movie",
                    title = o.optString("title"),
                    year = o.optString("release_date").take(4),
                    poster = o.optString("poster_path").blankToNull()?.let { "https://image.tmdb.org/t/p/w342$it" },
                    backdrop = o.optString("backdrop_path").blankToNull()?.let { "https://image.tmdb.org/t/p/w1280$it" },
                    overview = o.optString("overview"),
                )
            }
    }

    private suspend fun providers(key: String, type: String, id: Int, region: String): List<String> {
        val js = call(key, "/$type/$id/watch/providers")
        val reg = js.optJSONObject("results")?.optJSONObject(region.uppercase()) ?: return emptyList()
        val out = LinkedHashSet<String>()
        for (k in listOf("flatrate", "free", "ads")) {
            val a = reg.optJSONArray(k) ?: continue
            for (i in 0 until a.length()) a.optJSONObject(i)?.optString("provider_name")?.blankToNull()?.let { out += it }
        }
        return out.toList()
    }
}
