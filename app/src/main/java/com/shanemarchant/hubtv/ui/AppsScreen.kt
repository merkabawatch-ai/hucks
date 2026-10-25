package com.shanemarchant.hubtv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanemarchant.hubtv.data.AppLauncher
import com.shanemarchant.hubtv.data.Store

@Composable
fun AppsScreen(store: Store) {
    val ctx = LocalContext.current
    val apps = remember { AppLauncher.installed(ctx) }
    var pinned by remember { mutableStateOf(store.pinnedApps()) }
    val first = remember { FocusRequester() }
    FocusOnStart(first)

    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp)) {
        ScreenTitle("My Apps")
        Hint("Press OK to pin or unpin. Pinned apps show on the home screen, and search uses them to open results.")
        Spacer(Modifier.height(14.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(8.dp),
        ) {
            itemsIndexed(apps) { i, app ->
                val on = app.pkg in pinned
                Tile(
                    onClick = {
                        pinned = if (on) pinned - app.pkg else pinned + app.pkg
                        store.savePinnedApps(pinned)
                    },
                    selected = on,
                    focusRequester = if (i == 0) first else null,
                ) {
                    AppIcon(app.pkg, 56.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                    Text(if (on) "✓ Pinned" else " ", color = Accent, fontSize = 13.sp)
                }
            }
        }
    }
}
