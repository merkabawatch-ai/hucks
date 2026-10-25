package com.shanemarchant.hubtv.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shanemarchant.hubtv.Screen
import com.shanemarchant.hubtv.data.AppLauncher
import com.shanemarchant.hubtv.data.Kind
import com.shanemarchant.hubtv.data.MediaItem
import com.shanemarchant.hubtv.data.Repo
import com.shanemarchant.hubtv.data.Store
import com.shanemarchant.hubtv.data.Tmdb
import com.shanemarchant.hubtv.data.TmdbResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SearchScreen(store: Store, nav: (Screen) -> Unit, initialQuery: String = "") {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val apps = remember { AppLauncher.installed(ctx) }
    val field = remember { FocusRequester() }
    FocusOnStart(field)

    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var local by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var online by remember { mutableStateOf<List<TmdbResult>>(emptyList()) }

    fun run() {
        val q = query.trim()
        if (q.length < 2 || busy) return
        busy = true
        message = null
        scope.launch {
            val key = store.tmdbKey
            val tmdbJob = async {
                if (key.isBlank()) emptyList()
                else try { Tmdb.search(key, q, store.region) } catch (e: Exception) {
                    message = "Couldn't reach TMDB: ${e.message}"
                    emptyList()
                }
            }
            local = withContext(Dispatchers.Default) {
                val out = ArrayList<MediaItem>()
                for (s in store.sources()) {
                    try { out += Repo.items(s).filter { it.title.contains(q, ignoreCase = true) } } catch (e: Exception) { }
                }
                out.sortedBy { it.kind.ordinal }.take(200)
            }
            online = tmdbJob.await()
            if (key.isBlank()) message = "Add a TMDB key in Settings to see which apps have it."
            else if (local.isEmpty() && online.isEmpty()) message = "Nothing found for \"$q\"."
            busy = false
        }
    }

    LaunchedEffect(initialQuery) { if (initialQuery.isNotBlank()) run() }

    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp)) {
        ScreenTitle("Search")
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Field(
                label = "Film, show or channel",
                value = query,
                onChange = { query = it },
                imeAction = ImeAction.Search,
                onDone = { run() },
                modifier = Modifier.focusRequester(field),
            )
            Spacer(Modifier.width(16.dp))
            Tile(onClick = { run() }) { Text(if (busy) "Searching…" else "Search", fontSize = 18.sp) }
        }
        message?.let { Hint(it) }
        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(6.dp)) {
            if (online.isNotEmpty()) {
                item { SectionTitle("Where to watch (${store.region})") }
                items(online) { r -> OnlineRow(r, apps) }
            }
            if (local.isNotEmpty()) {
                item { SectionTitle("In your playlists") }
                items(local) { item ->
                    Tile(onClick = { openItem(ctx, item, nav) }, modifier = Modifier.width(820.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = item.logo, contentDescription = null, contentScale = ContentScale.Fit,
                                modifier = Modifier.size(56.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Hint(
                                    when (item.kind) { Kind.LIVE -> "Live"; Kind.MOVIE -> "Movie"; Kind.SERIES -> "Series" } +
                                        " · " + item.group
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun OnlineRow(r: TmdbResult, apps: List<com.shanemarchant.hubtv.data.AppInfo>) {
    val ctx = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = r.poster, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(70.dp, 105.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                r.title + if (r.year.isNotBlank()) " (${r.year})" else "",
                fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            )
            Hint(if (r.type == "movie") "Film" else "TV show")
            Spacer(Modifier.height(6.dp))
            if (r.providers.isEmpty()) {
                Hint("Not on a streaming service here right now")
            } else {
                // One button per service; services that share an app (e.g. Netflix + Netflix with ads) are merged.
                val seen = HashSet<String>()
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    for (p in r.providers) {
                        val app = AppLauncher.match(p, apps)
                        if (!seen.add(app?.pkg ?: p)) continue
                        Tile(onClick = {
                            if (app == null) toast(ctx, "$p isn't installed on this Firestick")
                            else if (!AppLauncher.launch(ctx, app.pkg)) toast(ctx, "Couldn't open ${app.label}")
                            else toast(ctx, "Search for \"${r.title}\" in ${app.label}")
                        }) {
                            Text(if (app != null) "Open ${app.label}" else p, fontSize = 15.sp, color = if (app != null) Accent else Dim)
                        }
                    }
                }
            }
        }
    }
}
