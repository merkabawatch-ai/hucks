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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
    example: String = "",
    help: String = "",
) {
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    Column(Modifier.padding(vertical = 4.dp)) {
        // The name sits above the box so it stays visible while typing.
        Text(
            (if (focused) "▶  " else "") + label,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (focused) Accent else Color.White,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { if (example.isNotBlank()) Text(example, color = Dim) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = { onDone() }, onSearch = { onDone() }, onGo = { onDone() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = PanelFocus,
                focusedContainerColor = Panel,
                unfocusedContainerColor = Panel,
                cursorColor = Accent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
            ),
            modifier = modifier
                .width(640.dp)
                .onFocusChanged { focused = it.isFocused }
                // Firestick remote: OK opens the keyboard, up/down move to the next box.
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { keyboard?.show(); true }
                        Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                        Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                        else -> false
                    }
                },
        )
        if (focused) {
            Spacer(Modifier.height(2.dp))
            Hint("Press OK on the remote to type", Accent)
        }
        if (help.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Hint(help)
        }
    }
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
