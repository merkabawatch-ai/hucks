package com.shanemarchant.hubtv.data

object M3uParser {
    private val attrRegex = Regex("([A-Za-z0-9_-]+)=\"([^\"]*)\"")

    fun parse(sourceId: String, text: String): List<MediaItem> {
        val out = ArrayList<MediaItem>()
        var title: String? = null
        var group = ""
        var logo: String? = null

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val matches = attrRegex.findAll(line).toList()
                    val attrs = matches.associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    val afterAttrs = matches.lastOrNull()?.range?.last ?: 0
                    val comma = line.indexOf(',', afterAttrs)
                    val name = if (comma >= 0) line.substring(comma + 1).trim() else ""
                    title = name.ifBlank { attrs["tvg-name"] ?: "Untitled" }
                    group = attrs["group-title"] ?: ""
                    logo = attrs["tvg-logo"]?.blankToNull()
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    if (group.isBlank()) group = line.substring(8).trim()
                }
                line.startsWith("#") -> Unit
                else -> {
                    val t = title ?: line.substringAfterLast('/')
                    out += MediaItem(sourceId, t, group.ifBlank { "Uncategorised" }, guessKind(line, group), line, logo)
                    title = null; group = ""; logo = null
                }
            }
        }
        return out
    }

    private fun guessKind(url: String, group: String): Kind {
        val u = url.lowercase()
        val g = group.lowercase()
        return when {
            "/series/" in u || "series" in g || "box set" in g -> Kind.SERIES
            "/movie/" in u || "movie" in g || "film" in g || "vod" in g ||
                u.endsWith(".mp4") || u.endsWith(".mkv") || u.endsWith(".avi") -> Kind.MOVIE
            else -> Kind.LIVE
        }
    }
}
