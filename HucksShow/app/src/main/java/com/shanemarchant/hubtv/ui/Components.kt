package com.shanemarchant.hubtv.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.shanemarchant.hubtv.PlayerActivity
import com.shanemarchant.hubtv.Screen
import com.shanemarchant.hubtv.data.AppLauncher
import com.shanemarchant.hubtv.data.MediaItem
import kotlinx.coroutines.delay

val Bg = Color(0xFF0E1116)
val Panel = Color(0xFF1A1F27)
val PanelFocus = Color(0xFF2B3341)
val PanelSelected = Color(0xFF3A2F14)
val Accent = Color(0xFFFFB020)
val Dim = Color(0xFF9AA4B2)
val ErrorRed = Color(0xFFFF7A7A)

@Composable
fun HubTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, onPrimary = Color.Black,
            background = Bg, onBackground = Color.White,
            surface = Panel, onSurface = Color.White,
        )
    ) {
        Surface(color = Bg, contentColor = Color.White, content = content)
    }
}

/** A remote-friendly button/card: grows and gets a yellow outline when focused. */
@Composable
fun Tile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    selected: Boolean = false,
    onFocus: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val withFocus = if (focusRequester != null) modifier.focusRequester(focusRequester) else modifier
    Column(
        withFocus
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus?.invoke()
            }
            .graphicsLayer {
                val s = if (focused) 1.05f else 1f
                scaleX = s; scaleY = s
            }
            .clip(shape)
            .background(
                when {
                    focused -> PanelFocus
                    selected -> PanelSelected
                    else -> Panel
                }
            )
            .border(2.dp, if (focused) Accent else Color.Transparent, shape)
            .clickable { onClick() }
            .padding(12.dp),
        content = content,
    )
}

@Composable
fun ScreenTitle(text: String) {
    Text(text, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
}

@Composable
fun Hint(text: String, color: Color = Dim) {
    Text(text, fontSize = 15.sp, color = color)
}

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }, onSearch = { onDone() }, onGo = { onDone() }),
        modifier = modifier.width(640.dp),
    )
}

/** Puts the remote's focus on something when a screen opens. */
@Composable
fun FocusOnStart(requester: FocusRequester, key: Any? = Unit) {
    LaunchedEffect(key) {
        repeat(6) {
            delay(80)
            if (runCatching { requester.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
}

@Composable
fun AppIcon(pkg: String, size: Dp) {
    val ctx = LocalContext.current
    val bmp = remember(pkg) {
        AppLauncher.icon(ctx, pkg)?.let { d -> runCatching { d.toBitmap(160, 160).asImageBitmap() }.getOrNull() }
    }
    if (bmp != null) Image(bmp, contentDescription = null, modifier = Modifier.size(size))
    else Box(Modifier.size(size).background(PanelFocus))
}

fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

/** Plays a channel/film, or opens the episode list for a series. */
fun openItem(ctx: Context, item: MediaItem, nav: (Screen) -> Unit) {
    val url = item.streamUrl
    when {
        url != null -> PlayerActivity.start(ctx, url, item.title)
        item.seriesId != null -> nav(Screen.Series(item))
        else -> toast(ctx, "Nothing to play for this one")
    }
}
