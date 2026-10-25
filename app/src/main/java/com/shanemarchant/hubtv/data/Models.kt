package com.shanemarchant.hubtv.data

enum class SourceType { M3U, XTREAM }

data class Source(
    val id: String,
    val name: String,
    val type: SourceType,
    val url: String,
    val username: String = "",
    val password: String = "",
)

enum class Kind { LIVE, MOVIE, SERIES }

data class MediaItem(
    val sourceId: String,
    val title: String,
    val group: String,
    val kind: Kind,
    val streamUrl: String?,
    val logo: String?,
    val seriesId: String? = null,
)

data class Episode(val title: String, val season: Int, val number: Int, val streamUrl: String)

data class TmdbResult(
    val id: Int,
    val type: String,
    val title: String,
    val year: String,
    val poster: String?,
    val providers: List<String> = emptyList(),
    val backdrop: String? = null,
    val overview: String = "",
)

data class AppInfo(val pkg: String, val label: String)

internal fun String.blankToNull(): String? = if (isBlank() || this == "null") null else this
