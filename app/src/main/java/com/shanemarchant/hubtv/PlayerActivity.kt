package com.shanemarchant.hubtv

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.shanemarchant.hubtv.data.Epg
import com.shanemarchant.hubtv.data.Http
import com.shanemarchant.hubtv.data.Kind
import com.shanemarchant.hubtv.data.MediaItem
import com.shanemarchant.hubtv.data.Source
import com.shanemarchant.hubtv.data.Store
import com.shanemarchant.hubtv.data.key
import kotlinx.coroutines.launch

/** The list of channels the player can flip through with up/down. */
object PlayQueue {
    var items: List<MediaItem> = emptyList()
    var index: Int = 0
}

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView
    private lateinit var overlay: TextView
    private lateinit var store: Store
    private var sources: Map<String, Source> = emptyMap()

    /** Channels to flip through; empty when playing a single film or episode. */
    private var queue: List<MediaItem> = emptyList()
    private var index = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        store = Store(applicationContext)
        sources = store.sources().associateBy { it.id }
        if (intent.getBooleanExtra(EXTRA_QUEUE, false)) {
            queue = PlayQueue.items
            index = PlayQueue.index.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
        }

        view = PlayerView(this).apply {
            useController = true
            keepScreenOn = true
        }
        overlay = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            setBackgroundColor(Color.argb(200, 14, 17, 22))
            setPadding(36, 24, 36, 24)
            visibility = View.GONE
        }
        val root = FrameLayout(this)
        root.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(
            overlay,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START)
                .apply { setMargins(48, 0, 48, 64) }
        )
        setContentView(root)
    }

    private val current: MediaItem? get() = queue.getOrNull(index)

    // ---------- Info box (channel name + guide) ----------

    private val hideInfo = Runnable { overlay.visibility = View.GONE }

    private fun showText(text: String) {
        overlay.text = text
        overlay.visibility = View.VISIBLE
        overlay.removeCallbacks(hideInfo)
        overlay.postDelayed(hideInfo, 7000)
    }

    private fun showInfo() {
        val item = current
        if (item == null) {
            val title = intent.getStringExtra(EXTRA_TITLE) ?: return
            val info = intent.getStringExtra(EXTRA_INFO)
            showText(if (info.isNullOrBlank()) title else "$title\n$info")
            return
        }
        val fav = if (store.isFavourite(item)) "★ " else ""
        val position = if (queue.size > 1) "   ${index + 1}/${queue.size}" else ""
        val header = "$fav${item.title}$position"
        val src = sources[item.sourceId]
        val cached = src?.let { Epg.cached(it, item) }?.let { Epg.summary(it) }
        showText(if (cached != null) "$header\n$cached" else header)
        // Fetch the guide if we don't have it yet, then update the box.
        if (cached == null && src != null && Epg.hasGuide(src, item)) {
            lifecycleScope.launch {
                val list = runCatching { Epg.nowNext(src, item) }.getOrNull()
                val text = list?.let { Epg.summary(it) }
                if (text != null && current == item) showText("$header\n$text")
            }
        }
    }

    // ---------- Playback ----------

    override fun onStart() {
        super.onStart()
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Http.userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)

        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@PlayerActivity, "Can't play this: ${error.errorCodeName}", Toast.LENGTH_LONG).show()
            }
        })
        view.player = p
        view.requestFocus()
        player = p
        playCurrent()
    }

    private fun playCurrent() {
        val p = player ?: return
        val url = current?.streamUrl ?: intent.getStringExtra(EXTRA_URL)
        if (url == null) { finish(); return }
        val media = androidx.media3.common.MediaItem.Builder().setUri(url).apply {
            if (url.contains(".m3u8", ignoreCase = true)) setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()
        p.setMediaItem(media)
        p.prepare()
        p.playWhenReady = true
        current?.let { store.addRecent(it) }
        showInfo()
    }

    private fun changeChannel(step: Int) {
        if (queue.size < 2) return
        index = (index + step + queue.size) % queue.size
        PlayQueue.index = index
        playCurrent()
    }

    private fun toggleFavourite() {
        val item = current ?: return
        val now = store.toggleFavourite(item)
        Toast.makeText(this, if (now) "★ Added to favourites" else "Removed from favourites", Toast.LENGTH_SHORT).show()
        showInfo()
    }

    /**
     * Remote controls (when the on-screen controls are hidden):
     * up/down or channel +/- = change channel, Menu = favourite, OK = show controls + info.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isLive = current?.kind == Kind.LIVE
        if (event.action == KeyEvent.ACTION_DOWN && !view.isControllerFullyVisible) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP ->
                    if (isLive) { changeChannel(-1); return true }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN ->
                    if (isLive) { changeChannel(1); return true }
                KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BOOKMARK ->
                    if (current != null) { toggleFavourite(); return true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> showInfo()
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        super.onStop()
        view.player = null
        player?.release()
        player = null
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_INFO = "info"
        private const val EXTRA_QUEUE = "queue"

        /** Plays one film or episode. */
        fun start(ctx: Context, url: String, title: String, info: String? = null) {
            ctx.startActivity(
                Intent(ctx, PlayerActivity::class.java)
                    .putExtra(EXTRA_URL, url)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_INFO, info)
            )
        }

        /** Plays item [index] of [items]; up/down flips through the rest (live channels). */
        fun startList(ctx: Context, items: List<MediaItem>, index: Int) {
            val playable = items.filter { it.streamUrl != null }
            val target = items.getOrNull(index) ?: return
            PlayQueue.items = playable
            PlayQueue.index = playable.indexOf(target).coerceAtLeast(0)
            ctx.startActivity(Intent(ctx, PlayerActivity::class.java).putExtra(EXTRA_QUEUE, true))
        }
    }
}
