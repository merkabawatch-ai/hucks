package com.shanemarchant.hubtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shanemarchant.hubtv.data.Epg
import com.shanemarchant.hubtv.data.MediaItem
import com.shanemarchant.hubtv.data.Programme
import com.shanemarchant.hubtv.data.Source
import kotlinx.coroutines.delay

/** Now/next panel for the channel the remote is on. */
@Composable
fun GuidePanel(item: MediaItem?, source: Source?) {
    var programmes by remember { mutableStateOf<List<Programme>?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(item) {
        programmes = null
        failed = false
        if (item == null || source == null) return@LaunchedEffect
        Epg.cached(source, item)?.let { programmes = it; return@LaunchedEffect }
        delay(400) // wait until the remote stops moving before asking the server
        programmes = try { Epg.nowNext(source, item) } catch (e: Exception) { failed = true; emptyList() }
    }

    Column(
        Modifier.fillMaxWidth()
            .height(118.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Panel)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        if (item == null) {
            Hint("Move onto a channel to see what's on.")
            return@Column
        }
        Text(item.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val list = programmes
        when {
            source == null || !Epg.hasGuide(source, item) -> Hint("No TV guide for this playlist.")
            list == null -> Hint("Loading guide…")
            list.isEmpty() -> Hint(if (failed) "Couldn't load the guide right now." else "No guide info for this channel.")
            else -> {
                val now = list[0]
                val total = (now.end - now.start).coerceAtLeast(1)
                val done = (System.currentTimeMillis() - now.start).coerceIn(0, total)
                Text(
                    "Now  ${Epg.time(now.start)}–${Epg.time(now.end)}   ${now.title}",
                    color = Accent, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { done.toFloat() / total },
                    color = Accent,
                    trackColor = PanelFocus,
                    modifier = Modifier.width(320.dp),
                )
                Spacer(Modifier.height(4.dp))
                list.getOrNull(1)?.let { next ->
                    Text(
                        "Next  ${Epg.time(next.start)}   ${next.title}",
                        color = Dim, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
