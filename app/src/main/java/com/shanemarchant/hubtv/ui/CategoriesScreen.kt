package com.shanemarchant.hubtv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanemarchant.hubtv.data.Kind
import com.shanemarchant.hubtv.data.LanguageFilter
import com.shanemarchant.hubtv.data.Repo
import com.shanemarchant.hubtv.data.Store

/** Tick the categories you want to see. */
@Composable
fun CategoriesScreen(kind: Kind, store: Store, back: () -> Unit) {
    var groups by remember { mutableStateOf<List<Pair<String, Int>>?>(null) }
    var hidden by remember { mutableStateOf(store.hiddenGroups()) }
    val first = remember { FocusRequester() }
    FocusOnStart(first)

    LaunchedEffect(kind) {
        val englishOnly = store.englishOnly
        val counts = LinkedHashMap<String, Int>()
        for (s in store.sources()) {
            try {
                Repo.items(s).forEach {
                    if (it.kind == kind && (!englishOnly || LanguageFilter.allowed(it))) counts[it.group] = (counts[it.group] ?: 0) + 1
                }
            } catch (e: Exception) { }
        }
        groups = counts.toList()
    }

    fun key(g: String) = "${kind.name}|$g"
    fun save(set: Set<String>) { hidden = set; store.saveHiddenGroups(set) }

    val title = when (kind) { Kind.LIVE -> "Live TV"; Kind.MOVIE -> "Movies"; Kind.SERIES -> "Series" }
    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp)) {
        ScreenTitle("Choose categories · $title")
        Hint("Press OK to tick or untick. Only ticked categories show up.")
        Spacer(Modifier.height(12.dp))
        val list = groups
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Tile(onClick = back, focusRequester = first) { Text("Done", fontSize = 16.sp) }
            Tile(onClick = { list?.let { l -> save(hidden - l.map { key(it.first) }.toSet()) } }) { Text("Show all", fontSize = 16.sp) }
            Tile(onClick = { list?.let { l -> save(hidden + l.map { key(it.first) }) } }) { Text("Hide all", fontSize = 16.sp) }
        }
        Spacer(Modifier.height(14.dp))
        when {
            list == null -> LoadingStatus()
            list.isEmpty() -> Hint("No categories found.")
            else -> {
                val shownCount = list.count { key(it.first) !in hidden }
                Hint("$shownCount of ${list.size} categories ticked")
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(260.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(6.dp),
                ) {
                    items(list) { (group, count) ->
                        val on = key(group) !in hidden
                        Tile(
                            onClick = { save(if (on) hidden + key(group) else hidden - key(group)) },
                            selected = on,
                        ) {
                            Row {
                                Text(if (on) "☑  " else "☐  ", fontSize = 18.sp, color = if (on) Accent else Dim)
                                Column {
                                    Text(group, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp, color = if (on) androidx.compose.ui.graphics.Color.White else Dim)
                                    Hint(if (kind == Kind.LIVE) "$count channels" else "$count titles")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
