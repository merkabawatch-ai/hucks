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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shanemarchant.hubtv.PlayerActivity
import com.shanemarchant.hubtv.Screen
import com.shanemarchant.hubtv.data.Episode
import com.shanemarchant.hubtv.data.Epg
import com.shanemarchant.hubtv.data.LanguageFilter
import com.shanemarchant.hubtv.data.Kind
import com.shanemarchant.hubtv.data.MediaItem
import com.shanemarchant.hubtv.data.Repo
import com.shanemarchant.hubtv.data.Store
import com.shanemarchant.hubtv.data.key

@Composable
fun BrowseScreen(kind: Kind, store: Store, nav: (Screen) -> Unit) {
    var items by remember { mutableStateOf<List<MediaItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var updatedText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(kind, reload) {
        items = null
        error = null
        val sources = store.sources()
        if (sources.isEmpty()) {
            error = "No playlists yet. Add one under Playlists on the home screen."
            items = emptyList()
            return@LaunchedEffect
        }
        val all = ArrayList<MediaItem>()
        val errs = ArrayList<String>()
        val hidden = store.hiddenGroups()
        for (s in sources) {
            val englishOnly = store.englishOnly
            try { all += Repo.items(s).filter {
                it.kind == kind && (!englishOnly || LanguageFilter.allowed(it)) && "${kind.name}|${it.group}" !in hidden
            } } catch (e: Exception) { errs += "${s.name}: ${e.message}" }
        }
        error = errs.joinToString("\n").ifEmpty { null }
        updatedText = sources.mapNotNull { Repo.updatedAt(it.id) }.minOrNull()?.let { "Updated ${ago(it)}" }
        items = all
        if (kind == Kind.LIVE) {
            for (s in sources) runCatching { Epg.loadGuide(s, Repo.items(s)) }
        }
    }

    val title = when (kind) { Kind.LIVE -> "Live TV"; Kind.MOVIE -> "Movies"; Kind.SERIES -> "Series" }
    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle(title)
            Spacer(Modifier.width(24.dp))
            if (items != null) {
                Tile(onClick = { nav(Screen.Categories(kind)) }) { Text("Choose categories", fontSize = 16.sp) }
                Spacer(Modifier.width(14.dp))
                Tile(onClick = {
                    Repo.clearAll()
                    Epg.clear()
                    reload++
                }) { Text("⟳  Refresh", fontSize = 16.sp) }
                Spacer(Modifier.width(14.dp))
                Tile(onClick = {
                    store.englishOnly = !store.englishOnly
                    reload++
                }, selected = store.englishOnly) {
                    Text(if (store.englishOnly) "UK only: ON" else "UK only: OFF (all countries)", fontSize = 16.sp)
                }
                updatedText?.let {
                    Spacer(Modifier.width(14.dp))
                    Hint(it)
                }
            }
        }
        error?.let { Hint(it, ErrorRed) }
        if (kind == Kind.LIVE) {
            val guideStatus by Epg.status.collectAsState()
            if (guideStatus.isNotBlank()) Hint(guideStatus, Accent)
        }
        Spacer(Modifier.height(12.dp))
        val list = items
        when {
            list == null -> LoadingStatus()
            list.isEmpty() -> Hint(
                if (store.englishOnly) "Nothing tagged UK here. Press \"UK only\" to switch it off and show everything."
                else "Nothing to show. If you hid every category, press Choose categories."
            )
            else -> BrowseContent(list, kind, store, nav)
        }
    }
}

private const val FAVOURITES = "★ Favourites"
private const val RECENT = "Recently watched"

/** "5 minutes ago", "3 hours ago", "2 days ago". */
fun ago(time: Long): String {
    val mins = ((System.currentTimeMillis() - time) / 60_000).coerceAtLeast(0)
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "$mins min ago"
        mins < 60 * 24 -> "${mins / 60} hours ago"
        else -> "${mins / (60 * 24)} days ago"
    }
}

@Composable
fun LoadingStatus() {
    val status by Repo.status.collectAsState()
    var seconds by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(1000); seconds++ }
    }
    Column {
        Hint("Loading… ${seconds}s  (big playlists can take a minute the first time)")
        Spacer(Modifier.height(8.dp))
        Text(status.ifBlank { "Starting…" }, fontSize = 18.sp, color = Accent)
        if (seconds > 90) {
            Spacer(Modifier.height(8.dp))
            Hint("This is taking a long time. If the numbers above stopped moving, the server may not be answering. Press Back and try again.", ErrorRed)
        }
    }
}

@Composable
private fun BrowseContent(list: List<MediaItem>, kind: Kind, store: Store, nav: (Screen) -> Unit) {
    val sources = remember { store.sources().associateBy { it.id } }
    var focusedItem by remember { mutableStateOf<MediaItem?>(null) }
    val ctx = LocalContext.current
    var resumeTick by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumeTick++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val special = if (kind == Kind.SERIES) emptyList() else listOf(FAVOURITES, RECENT)
    val groups = remember(list) { special + list.map { it.group }.distinct() }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val firstReal = groups.getOrNull(special.size) ?: groups.first()
    val current = picked?.takeIf { it in groups } ?: firstReal
    val shown = remember(list, current, resumeTick) {
        when (current) {
            FAVOURITES -> store.favourites().let { favs -> list.filter { it.key() in favs } }
            RECENT -> {
                val byKey = list.associateBy { it.key() }
                store.recent().mapNotNull { byKey[it] }
            }
            else -> list.filter { it.group == current }
        }
    }
    val first = remember { FocusRequester() }
    FocusOnStart(first)
    val guideVersion by Epg.version.collectAsState()
    val favs = remember(resumeTick) { store.favourites() }

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
        Column(Modifier.fillMaxSize()) {
        if (kind == Kind.LIVE) {
            GuidePanel(focusedItem, focusedItem?.let { sources[it.sourceId] })
            Spacer(Modifier.height(10.dp))
        }
        if (shown.isEmpty()) {
            Hint(
                when (current) {
                    FAVOURITES -> "No favourites yet. While watching, press the Menu (☰) button on the remote to add one."
                    RECENT -> "Nothing watched yet."
                    else -> "Nothing in this category."
                }
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (kind == Kind.LIVE) 170.dp else 150.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(8.dp),
        ) {
            itemsIndexed(shown) { index, item ->
                Tile(
                    onClick = {
                        if (item.streamUrl != null) PlayerActivity.startList(ctx, shown, index)
                        else openItem(ctx, item, nav)
                    },
                    onFocus = { if (kind == Kind.LIVE) focusedItem = item },
                ) {
                    AsyncImage(
                        model = item.logo,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(if (kind == Kind.LIVE) 70.dp else 190.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        (if (item.key() in favs) "★ " else "") + item.title,
                        maxLines = if (kind == Kind.LIVE) 1 else 2, overflow = TextOverflow.Ellipsis, fontSize = 15.sp,
                    )
                    if (kind == Kind.LIVE) {
                        val now = remember(item, guideVersion) { Epg.nowOn(item) }
                        Text(
                            now?.title ?: " ",
                            maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = Accent,
                        )
                    }
                }
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
