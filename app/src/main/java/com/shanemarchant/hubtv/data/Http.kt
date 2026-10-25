package com.shanemarchant.hubtv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {
    /**
     * Many IPTV servers only answer apps they recognise. If a server refuses,
     * we try again as each of these, and remember whichever one worked.
     */
    private val AGENTS = listOf(
        "IPTVSmartersPlayer",
        "IPTVSmartersPro",
        "VLC/3.0.20 LibVLC/3.0.20",
        "TiviMate/4.7.0 (Linux; Android 11)",
        "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        "okhttp/4.12.0",
    )

    /** The name that last worked; the player uses it too. */
    @Volatile
    var userAgent: String = AGENTS.first()
        private set

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private fun agentsToTry(): List<String> = listOf(userAgent) + AGENTS.filter { it != userAgent }

    /** Opens the URL, retrying under other app names if the server refuses. */
    private fun open(url: String, headers: Map<String, String>, retry: Boolean): Response {
        var lastCode = 0
        val agents = if (retry) agentsToTry() else listOf(userAgent)
        for ((i, ua) in agents.withIndex()) {
            val builder = Request.Builder().url(url).header("User-Agent", ua)
            headers.forEach { (k, v) -> builder.header(k, v) }
            val r = client.newCall(builder.build()).execute()
            if (r.isSuccessful) {
                if (retry) userAgent = ua
                return r
            }
            lastCode = r.code
            r.close()
            if (lastCode == 404) break
            if (retry && i < agents.lastIndex) Repo.report("Server said no ($lastCode), trying again as another app…")
        }
        throw IOException(
            if (retry && lastCode != 404) "Server replied $lastCode even after trying several app names"
            else "Server replied $lastCode"
        )
    }

    /** Reads the reply bit by bit instead of loading it all into memory (big playlists). */
    suspend fun <T> stream(url: String, block: (java.io.InputStream) -> T): T = withContext(Dispatchers.IO) {
        open(url, emptyMap(), retry = true).use { r ->
            val body = r.body ?: throw IOException("Server sent nothing back")
            body.byteStream().use { block(it) }
        }
    }

    suspend fun get(url: String, headers: Map<String, String> = emptyMap(), retry: Boolean = true): String =
        withContext(Dispatchers.IO) {
            open(url, headers, retry).use { r -> r.body?.string() ?: "" }
        }
}
