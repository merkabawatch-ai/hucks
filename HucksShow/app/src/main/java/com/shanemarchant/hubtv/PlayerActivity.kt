package com.shanemarchant.hubtv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.shanemarchant.hubtv.data.Http

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = PlayerView(this).apply {
            useController = true
            keepScreenOn = true
        }
        setContentView(view)
    }

    override fun onStart() {
        super.onStart()
        val url = intent.getStringExtra(EXTRA_URL)
        if (url == null) { finish(); return }

        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Http.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)

        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()

        val item = MediaItem.Builder().setUri(url).apply {
            if (url.contains(".m3u8", ignoreCase = true)) setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()

        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@PlayerActivity, "Can't play this: ${error.errorCodeName}", Toast.LENGTH_LONG).show()
            }
        })
        p.setMediaItem(item)
        p.prepare()
        p.playWhenReady = true
        view.player = p
        view.requestFocus()
        player = p
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

        fun start(ctx: Context, url: String, title: String) {
            ctx.startActivity(
                Intent(ctx, PlayerActivity::class.java)
                    .putExtra(EXTRA_URL, url)
                    .putExtra(EXTRA_TITLE, title)
            )
        }
    }
}
