package com.shanemarchant.hubtv.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanemarchant.hubtv.BuildConfig
import com.shanemarchant.hubtv.data.Store

@Composable
fun SettingsScreen(store: Store) {
    val ctx = LocalContext.current
    var key by rememberSaveable { mutableStateOf(store.tmdbKeyTyped) }
    var region by rememberSaveable { mutableStateOf(store.region) }
    val first = remember { FocusRequester() }
    FocusOnStart(first)

    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp)) {
        ScreenTitle("Settings")
        Spacer(Modifier.height(16.dp))
        Hint(
            if (BuildConfig.TMDB_KEY.isNotBlank()) "A TMDB key is built in. Only fill this in to use a different one."
            else "TMDB key (free from themoviedb.org → Settings → API). Either the API key or the Read Access Token works."
        )
        Spacer(Modifier.height(6.dp))
        Field("TMDB key", key, { key = it }, modifier = Modifier.focusRequester(first))
        Spacer(Modifier.height(14.dp))
        Hint("Country for \"where to watch\" (two letters, e.g. GB or MT)")
        Spacer(Modifier.height(6.dp))
        Field("Country", region, { region = it.take(2) }, imeAction = ImeAction.Done)
        Spacer(Modifier.height(18.dp))
        Tile(onClick = {
            store.tmdbKey = key
            store.region = region
            toast(ctx, "Saved")
        }) { Text("Save", fontSize = 18.sp) }
        Spacer(Modifier.height(24.dp))
        Hint("Hucks Show ${BuildConfig.VERSION_NAME}")
    }
}
