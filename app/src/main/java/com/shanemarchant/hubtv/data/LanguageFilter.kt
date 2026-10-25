package com.shanemarchant.hubtv.data

/**
 * "UK only" filter.
 * Live TV: keeps only categories/channels tagged UK or GB ("UK | Sports", "[UK]", "UK: BBC One").
 * Movies/Series: keeps English ones tagged UK, GB, EN or US.
 */
object LanguageFilter {
    private val ukTags = setOf("UK", "GB")
    private val englishTags = setOf("UK", "GB", "EN", "ENG", "US", "USA")
    private val ukWords = listOf("united kingdom", "british")
    private val englishWords = listOf("united kingdom", "british", "english")

    private val separators = charArrayOf(' ', '|', ':', '-', ']', ')', '_', '.', '/', '[', '(')

    /** Capitalised tags anywhere in the name, e.g. "UK", "GB", "EN". */
    private fun tags(text: String): Set<String> =
        text.split(*separators).filter { it.length in 2..3 && it == it.uppercase() && it.all { c -> c.isLetter() } }.toSet()

    private fun matches(text: String, wanted: Set<String>, words: List<String>): Boolean {
        if (text.isBlank()) return false
        if (tags(text).any { it in wanted }) return true
        val lower = text.lowercase()
        return words.any { it in lower }
    }

    fun allowed(item: MediaItem): Boolean = when (item.kind) {
        Kind.LIVE -> matches(item.group, ukTags, ukWords) || matches(item.title, ukTags, ukWords)
        Kind.MOVIE, Kind.SERIES ->
            matches(item.group, englishTags, englishWords) || matches(item.title, englishTags, englishWords)
    }
}
