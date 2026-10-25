package com.shanemarchant.hubtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.shanemarchant.hubtv.data.Kind
import com.shanemarchant.hubtv.data.MediaItem
import com.shanemarchant.hubtv.data.SourceType
import com.shanemarchant.hubtv.data.Store
import com.shanemarchant.hubtv.ui.AddSourceScreen
import com.shanemarchant.hubtv.ui.AppsScreen
import com.shanemarchant.hubtv.ui.BrowseScreen
import com.shanemarchant.hubtv.ui.CategoriesScreen
import com.shanemarchant.hubtv.ui.HomeScreen
import com.shanemarchant.hubtv.ui.HubTheme
import com.shanemarchant.hubtv.ui.SearchScreen
import com.shanemarchant.hubtv.ui.SeriesScreen
import com.shanemarchant.hubtv.ui.SettingsScreen
import com.shanemarchant.hubtv.ui.SourcesScreen

sealed interface Screen {
    data object Home : Screen
    data class Search(val query: String = "") : Screen
    data object MyApps : Screen
    data object Sources : Screen
    data object Settings : Screen
    data class Browse(val kind: Kind) : Screen
    data class Series(val item: MediaItem) : Screen
    data class AddSource(val type: SourceType) : Screen
    data class Categories(val kind: Kind) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(applicationContext)
        com.shanemarchant.hubtv.data.Repo.init(applicationContext)
        installCrashCatcher()
        setContent { HubTheme { App(store) } }
    }
}

/** Saves the error from a crash so the home screen can show it next time. */
private fun ComponentActivity.installCrashCatcher() {
    val prefs = applicationContext.getSharedPreferences("hubtv", android.content.Context.MODE_PRIVATE)
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    if (previous is HucksCrashHandler) return
    Thread.setDefaultUncaughtExceptionHandler(HucksCrashHandler(prefs, previous))
}

private class HucksCrashHandler(
    private val prefs: android.content.SharedPreferences,
    private val previous: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(t: Thread, e: Throwable) {
        runCatching { prefs.edit().putString("last_crash", e.stackTraceToString().take(3000)).commit() }
        previous?.uncaughtException(t, e)
    }
}

@Composable
private fun App(store: Store) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val holder = rememberSaveableStateHolder()
    fun keyOf(i: Int) = "$i:${stack[i]}"
    val nav: (Screen) -> Unit = { stack.add(it) }
    val back: () -> Unit = {
        if (stack.size > 1) {
            holder.removeState(keyOf(stack.lastIndex))
            stack.removeAt(stack.lastIndex)
        }
    }
    BackHandler(enabled = stack.size > 1, onBack = back)

    val screen = stack.last()
    holder.SaveableStateProvider(keyOf(stack.lastIndex)) {
        when (screen) {
            Screen.Home -> HomeScreen(store, nav)
            is Screen.Search -> SearchScreen(store, nav, screen.query)
            Screen.MyApps -> AppsScreen(store)
            Screen.Sources -> SourcesScreen(store, nav)
            Screen.Settings -> SettingsScreen(store)
            is Screen.Browse -> BrowseScreen(screen.kind, store, nav)
            is Screen.Series -> SeriesScreen(screen.item, store)
            is Screen.AddSource -> AddSourceScreen(screen.type, store, back)
            is Screen.Categories -> CategoriesScreen(screen.kind, store, back)
        }
    }
}
