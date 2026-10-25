package com.shanemarchant.hubtv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {
    const val USER_AGENT = "HubTV/1.0 (Android TV)"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url).header("User-Agent", USER_AGENT)
        headers.forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Server replied ${r.code}")
            r.body?.string() ?: ""
        }
    }
}
