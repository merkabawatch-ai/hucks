package com.shanemarchant.hubtv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanemarchant.hubtv.Screen
import com.shanemarchant.hubtv.data.Repo
import com.shanemarchant.hubtv.data.Source
import com.shanemarchant.hubtv.data.SourceType
import com.shanemarchant.hubtv.data.Store
import com.shanemarchant.hubtv.data.XtreamClient
import com.shanemarchant.hubtv.data.normaliseServer
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun SourcesScreen(store: Store, nav: (Screen) -> Unit) {
    val ctx = LocalContext.current
    var sources by remember { mutableStateOf(store.sources()) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    FocusOnStart(first)

    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp)) {
        ScreenTitle("Playlists")
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Tile(onClick = { nav(Screen.AddSource(SourceType.XTREAM)) }, focusRequester = first) {
                Text("+ Add Xtream login", fontSize = 18.sp)
            }
            Tile(onClick = { nav(Screen.AddSource(SourceType.M3U)) }) {
                Text("+ Add M3U link", fontSize = 18.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
        if (sources.isEmpty()) Hint("No playlists yet.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(6.dp)) {
            items(sources) { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(560.dp)) {
                        Text(s.name, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        Hint(
                            (if (s.type == SourceType.XTREAM) "Xtream · " else "M3U · ") + s.url,
                        )
                    }
                    Tile(onClick = {
                        Repo.clear(s.id)
                        toast(ctx, "${s.name} will reload next time you open it")
                    }) { Text("Refresh") }
                    Spacer(Modifier.width(12.dp))
                    Tile(onClick = {
                        if (confirmDelete == s.id) {
                            sources = sources.filter { it.id != s.id }
                            store.saveSources(sources)
                            Repo.clear(s.id)
                            confirmDelete = null
                        } else {
                            confirmDelete = s.id
                        }
                    }) {
                        Text(if (confirmDelete == s.id) "Press again to delete" else "Delete", color = if (confirmDelete == s.id) ErrorRed else androidx.compose.ui.graphics.Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun AddSourceScreen(type: SourceType, store: Store, back: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var checkFailed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    FocusOnStart(first)

    val xtream = type == SourceType.XTREAM

    fun save() {
        val src = Source(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { if (xtream) "My Xtream" else "My playlist" },
            type = type,
            url = if (xtream) normaliseServer(url) else url.trim(),
            username = user.trim(),
            password = pass.trim(),
        )
        store.saveSources(store.sources() + src)
        back()
    }

    fun submit() {
        if (busy) return
        if (url.isBlank() || (xtream && (user.isBlank() || pass.isBlank()))) {
            status = "Please fill in all the boxes."
            return
        }
        if (!xtream || checkFailed) { save(); return }
        busy = true
        status = "Checking login…"
        scope.launch {
            val ok = try {
                XtreamClient(Source("test", "", type, normaliseServer(url), user.trim(), pass.trim())).checkLogin()
            } catch (e: Exception) {
                status = "Couldn't connect: ${e.message}"
                false
            }
            busy = false
            if (ok) save() else {
                checkFailed = true
                if (status == "Checking login…") status = "The server says the login is wrong."
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp)) {
        ScreenTitle(if (xtream) "Add Xtream login" else "Add M3U link")
        Hint(
            if (xtream) "Fill in the 4 boxes below. Move between them with up and down on the remote."
            else "Fill in the 2 boxes below. Move between them with up and down on the remote."
        )
        Spacer(Modifier.height(16.dp))
        Field(
            "1. Playlist name", name, { name = it },
            modifier = Modifier.focusRequester(first),
            example = "My IPTV",
            help = "Anything you like, so you can tell your playlists apart.",
        )
        if (xtream) {
            Field(
                "2. Server address", url, { url = it; checkFailed = false },
                example = "http://line.example.com:80",
                help = "Also called Host or URL. Include http:// and the :port number.",
            )
            Field("3. Username", user, { user = it; checkFailed = false }, example = "From your provider")
            Field(
                "4. Password", pass, { pass = it; checkFailed = false },
                imeAction = ImeAction.Done, onDone = { submit() },
                example = "From your provider",
            )
        } else {
            Field(
                "2. M3U link", url, { url = it; checkFailed = false },
                imeAction = ImeAction.Done, onDone = { submit() },
                example = "http://line.example.com/get.php?username=…&password=…",
                help = "The full link your provider gave you.",
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Tile(onClick = { submit() }) {
                Text(
                    when {
                        busy -> "Checking…"
                        checkFailed -> "Save anyway"
                        else -> "Save"
                    },
                    fontSize = 18.sp,
                )
            }
            Tile(onClick = back) { Text("Cancel", fontSize = 18.sp) }
        }
        status?.let {
            Spacer(Modifier.height(10.dp))
            Hint(it, if (checkFailed) ErrorRed else Dim)
        }
    }
}
