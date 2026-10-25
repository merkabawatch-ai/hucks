package com.shanemarchant.hubtv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shanemarchant.hubtv.PlayerActivity
import com.shanemarchant.hubtv.Screen
import com.shanemarchant.hubtv.data.Episode
import com.shanemarchant.hubtv.data.Kind
import com.shanemarchant.hubtv.data.MediaItem
import com.shanemarchant.hubtv.data.Repo
import com.shanemarchant.hubtv.data.Store

@Composable
fun BrowseScreen(kind: Kind, store: Store, nav: (Screen) -> Unit) {
    var items by remember { mutableStateOf<List<MediaItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(kind) {
        val sources = store.sources()
        if (sources.isEmpty()) {
            error = "No playlists yet. Add one under Playlists on the home screen."
            items = emptyList()
            return@LaunchedEffect
        }
        val all = ArrayList<MediaItem>()
        val errs = ArrayList<String>()
        for (s in sources) {
            try { all += Repo.items(s).filter { it.kind == kind } } catch (e: Exception) { errs += "${s.name}: ${e.message}" }
        }
        error = errs.joinToString("\n").ifEmpty { null }
        items = all
    }

    val title = when (kind) { Kind.LIVE -> "Live TV"; Kind.MOVIE -> "Movies"; Kind.SERIES -> "Series" }
    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp)) {
        ScreenTitle(title)
        error?.let { Hint(it, ErrorRed) }
        Spacer(Modifier.height(12.dp))
        val list = items
        when {
            list == null -> Hint("Loading… big playlists can take a minute the first time.")
            list.isEmpty() -> Hint("Nothing in this section.")
            else -> BrowseContent(list, kind, nav)
        }
    }
}

@Composable
private fun BrowseContent(list: List<MediaItem>, kind: Kind, nav: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val groups = remember(list) { list.map { it.group }.distinct() }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val current = picked?.takeIf { it in groups } ?: groups.first()
    val shown = remember(list, current) { list.filter { it.group == current } }
    val first = remember { FocusRequester() }
    FocusOnStart(first)

    Row(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.width(280.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(6.dp),
        ) {
            itemsIndexed(groups) { i, g ->
                Tile(
                    onClick = { picked = g },
                    selected = g == current,
                    modifier = Modifier.fillMaxWidth(),
                    focusRequester = if (i == 0) first else null,
                ) {
                    Text(g, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp)
                }
            }
        }
        Spacer(Modifier.width(20.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (kind == Kind.LIVE) 170.dp else 150.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(8.dp),
        ) {
            items(shown) { item ->
                Tile(onClick = { openItem(ctx, item, nav) }) {
                    AsyncImage(
                        model = item.logo,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(if (kind == Kind.LIVE) 70.dp else 190.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
fun SeriesScreen(item: MediaItem, store: Store) {
    val ctx = LocalContext.current
    var eps by remember { mutableStateOf<List<Episode>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }

    LaunchedEffect(item) {
        val source = store.sources().find { it.id == item.sourceId }
        eps = try {
            if (source == null || item.seriesId == null) emptyList() else Repo.episodes(source, item.seriesId)
        } catch (e: Exception) {
            error = e.message
            emptyList()
        }
    }
    FocusOnStart(first, eps?.size)

    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp)) {
        ScreenTitle(item.title)
        error?.let { Hint(it, ErrorRed) }
        Spacer(Modifier.height(12.dp))
        val list = eps
        when {
            list == null -> Hint("Loading episodes…")
            list.isEmpty() -> Hint("No episodes found.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(6.dp)) {
                itemsIndexed(list) { i, ep ->
                    Tile(
                        onClick = { PlayerActivity.start(ctx, ep.streamUrl, ep.title) },
                        modifier = Modifier.width(720.dp),
                        focusRequester = if (i == 0) first else null,
                    ) {
                        Text("S${ep.season} E${ep.number}   ${ep.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
