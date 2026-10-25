package com.shanemarchant.hubtv.data

import android.content.Context
import com.shanemarchant.hubtv.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

/** Everything the app remembers lives on the Firestick itself. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("hubtv", Context.MODE_PRIVATE)

    fun sources(): List<Source> {
        val arr = runCatching { JSONArray(prefs.getString("sources", "[]") ?: "[]") }.getOrElse { JSONArray() }
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Source(
                id = o.optString("id"),
                name = o.optString("name"),
                type = runCatching { SourceType.valueOf(o.optString("type")) }.getOrDefault(SourceType.M3U),
                url = o.optString("url"),
                username = o.optString("username"),
                password = o.optString("password"),
            )
        }
    }

    fun saveSources(list: List<Source>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id).put("name", s.name).put("type", s.type.name)
                    .put("url", s.url).put("username", s.username).put("password", s.password)
            )
        }
        prefs.edit().putString("sources", arr.toString()).apply()
    }

    /** The key typed in Settings wins; otherwise the one built in from the GitHub secret. */
    var tmdbKey: String
        get() = (prefs.getString("tmdb_key", "") ?: "").ifBlank { BuildConfig.TMDB_KEY }
        set(v) { prefs.edit().putString("tmdb_key", v.trim()).apply() }

    val tmdbKeyTyped: String get() = prefs.getString("tmdb_key", "") ?: ""

    var region: String
        get() = prefs.getString("region", "GB") ?: "GB"
        set(v) { prefs.edit().putString("region", v.trim().uppercase().ifBlank { "GB" }).apply() }

    var englishOnly: Boolean
        get() = prefs.getBoolean("english_only", true)
        set(v) { prefs.edit().putBoolean("english_only", v).apply() }

    /** Categories the user switched off, stored as "LIVE|UK Sports". */
    fun hiddenGroups(): Set<String> =
        runCatching {
            val arr = JSONArray(prefs.getString("hidden_groups", "[]") ?: "[]")
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        }.getOrDefault(emptySet())

    fun saveHiddenGroups(set: Set<String>) {
        val arr = JSONArray()
        set.forEach { arr.put(it) }
        prefs.edit().putString("hidden_groups", arr.toString()).apply()
    }

    // ---- Favourites and recently watched ----

    private fun readList(name: String): List<String> = runCatching {
        val arr = JSONArray(prefs.getString(name, "[]") ?: "[]")
        (0 until arr.length()).map { arr.getString(it) }
    }.getOrDefault(emptyList())

    private fun writeList(name: String, list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString(name, arr.toString()).apply()
    }

    fun favourites(): Set<String> = readList("favourites").toSet()

    fun isFavourite(item: MediaItem) = item.key() in favourites()

    /** Returns true if it's now a favourite. */
    fun toggleFavourite(item: MediaItem): Boolean {
        val list = readList("favourites").toMutableList()
        val k = item.key()
        val added = if (k in list) { list.remove(k); false } else { list.add(k); true }
        writeList("favourites", list)
        return added
    }

    /** Most recent first. */
    fun recent(): List<String> = readList("recent")

    fun addRecent(item: MediaItem) {
        val k = item.key()
        writeList("recent", (listOf(k) + readList("recent").filter { it != k }).take(30))
    }

    val lastCrash: String? get() = prefs.getString("last_crash", null)

    fun clearCrash() { prefs.edit().remove("last_crash").apply() }

    fun pinnedApps(): List<String> =
        (prefs.getString("pinned", "") ?: "").split(",").filter { it.isNotBlank() }

    fun savePinnedApps(list: List<String>) {
        prefs.edit().putString("pinned", list.joinToString(",")).apply()
    }
}
