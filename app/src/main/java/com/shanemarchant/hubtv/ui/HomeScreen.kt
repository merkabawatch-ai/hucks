package com.shanemarchant.hubtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.shanemarchant.hubtv.Screen
import com.shanemarchant.hubtv.data.AppLauncher
import com.shanemarchant.hubtv.data.Kind
import com.shanemarchant.hubtv.data.Store
import com.shanemarchant.hubtv.data.Tmdb
import com.shanemarchant.hubtv.data.TmdbResult

/** Latest films are kept for the whole session so the home screen doesn't reload them every time. */
private var latestCache: List<TmdbResult>? = null

@Composable
fun HomeScreen(store: Store, nav: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val first = remember { FocusRequester() }
    val installed = remember { AppLauncher.installed(ctx).associateBy { it.pkg } }
    val pinned = remember { store.pinnedApps().mapNotNull { installed[it] } }
    var latest by remember { mutableStateOf(latestCache ?: emptyList()) }
    var featuredIndex by rememberSaveable { mutableStateOf(0) }
    var crash by remember { mutableStateOf(store.lastCrash) }
    FocusOnStart(first)

    LaunchedEffect(Unit) {
        if (latestCache == null && store.tmdbKey.isNotBlank()) {
            runCatching { Tmdb.latest(store.tmdbKey, store.region) }.onSuccess {
                latestCache = it
                latest = it
            }
        }
    }
    val featured = latest.getOrNull(featuredIndex) ?: latest.firstOrNull()

    val menu = listOf(
        "Search" to Screen.Search(),
        "Live TV" to Screen.Browse(Kind.LIVE),
        "Movies" to Screen.Browse(Kind.MOVIE),
        "Series" to Screen.Browse(Kind.SERIES),
        "My Apps" to Screen.MyApps,
        "Playlists" to Screen.Sources,
        "Settings" to Screen.Settings,
    )

    Box(Modifier.fillMaxSize()) {
        // Full-screen film artwork that changes as you move along "Latest movies".
        if (featured?.backdrop != null) {
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(featured.backdrop).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.45f,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Bg.copy(alpha = 0.55f), Bg.copy(alpha = 0.85f), Bg))
                )
            )
        }

        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 48.dp, vertical = 28.dp)
        ) {
            Text("Hucks Show", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Accent)
            if (featured != null) {
                Text(
                    featured.title + if (featured.year.isNotBlank()) "  (${featured.year})" else "",
                    fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    featured.overview, fontSize = 15.sp, color = Color(0xFFD0D6DE),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(720.dp),
                )
            } else {
                Hint("Your playlists and apps in one place")
            }
            crash?.let { report ->
                Spacer(Modifier.height(12.dp))
                Tile(
                    onClick = { store.clearCrash(); crash = null },
                    modifier = Modifier.width(900.dp),
                ) {
                    Text("Hucks Show crashed last time. Take a photo of this for Claude, then press OK to hide it.", color = ErrorRed, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(report.lines().take(10).joinToString("\n"), fontSize = 12.sp, color = Dim)
                }
            }
            Spacer(Modifier.height(18.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 10.dp, horizontal = 6.dp),
            ) {
                itemsIndexed(menu) { i, entry ->
                    Tile(
                        onClick = { nav(entry.second) },
                        modifier = Modifier.size(160.dp, 84.dp),
                        focusRequester = if (i == 0) first else null,
                    ) {
                        Spacer(Modifier.weight(1f))
                        Text(entry.first, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (latest.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Latest movies", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(vertical = 10.dp, horizontal = 6.dp),
                ) {
                    itemsIndexed(latest) { i, film ->
                        Tile(
                            onClick = { nav(Screen.Search(film.title)) },
                            onFocus = { featuredIndex = i },
                            modifier = Modifier.width(130.dp),
                        ) {
                            AsyncImage(
                                model = film.poster,
                                contentDescription = film.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(170.dp),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(film.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("My apps", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (pinned.isEmpty()) {
                Hint("Open My Apps to pin Netflix, iPlayer or anything else installed on this Firestick.")
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 10.dp, horizontal = 6.dp),
                ) {
                    items(pinned) { app ->
                        Tile(
                            onClick = { if (!AppLauncher.launch(ctx, app.pkg)) toast(ctx, "Couldn't open ${app.label}") },
                            modifier = Modifier.width(150.dp),
                        ) {
                            AppIcon(app.pkg, 64.dp)
                            Spacer(Modifier.height(6.dp))
                            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
