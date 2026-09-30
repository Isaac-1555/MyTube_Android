package com.example.mytube.player

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.LruCache
import androidx.core.app.NotificationCompat
import androidx.palette.graphics.Palette
import com.example.mytube.MainActivity
import com.example.mytube.MyTubeApplication
import com.example.mytube.R
import com.example.mytube.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

class PlaybackService : Service() {
    private var mediaSession: MediaSessionCompat? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isPlaying = false
    private var currentTitle = "MyTube"
    private var currentDuration = 0L
    private var currentPosition = 0L
    private var hasNext = false
    private var hasPrev = false
    private var currentArtUrl: String? = null
    private var artBitmap: Bitmap? = null
    private var accentColor: Int = 0

    private val artCache = LruCache<String, Bitmap>(8)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val keepAliveRunnable = object : Runnable {
        override fun run() {
            val pm = (application as? MyTubeApplication)?.container?.playbackManager
            if (pm?.isAppForeground != true) {
                evaluateJs("window.MyTubeBgTick && window.MyTubeBgTick()")
            }
            mainHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }
    private val idleTimeoutRunnable = Runnable { stop() }
    private val bgPauseTimeoutRunnable = Runnable { stop() }
    private var lastNotifiedTitle = ""
    private var lastNotifiedDuration = -1L
    private var lastNotifiedPlaying = false
    private var lastNotifiedArtUrl: String? = null

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                evaluateJs("window.MyTubePause && window.MyTubePause()")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, Constants.WAKE_LOCK_TAG)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), RECEIVER_EXPORTED)
        } else {
            registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        }

        setupMediaSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                isPlaying = true
                cancelBgPauseTimeout()
                startWithNotification()
            }
            ACTION_PLAY -> {
                isPlaying = true
                evaluateJs("window.MyTubePlay && window.MyTubePlay()")
                updatePlaybackState(true)
                startWithNotification()
                startHeartbeat()
                cancelIdleTimeout()
                cancelBgPauseTimeout()
            }
            ACTION_PAUSE -> {
                isPlaying = false
                evaluateJs("window.MyTubePause && window.MyTubePause()")
                updatePlaybackState(false)
                startWithNotification()
                stopHeartbeat()
                releaseWakeLock()
                startIdleTimeout()
                cancelBgPauseTimeout()
            }
            ACTION_SKIP_NEXT -> {
                evaluateJs("window.MyTubeNext && window.MyTubeNext()")
            }
            ACTION_SKIP_PREV -> {
                evaluateJs("window.MyTubePrev && window.MyTubePrev()")
            }
            Constants.ACTION_UPDATE_METADATA -> {
                val wasPlaying = isPlaying
                isPlaying = intent.getBooleanExtra("playing", false)
                currentTitle = intent.getStringExtra("title") ?: "MyTube"
                currentDuration = intent.getLongExtra("duration", 0L)
                currentPosition = intent.getLongExtra("position", 0L)
                hasNext = intent.getBooleanExtra("hasNext", false)
                hasPrev = intent.getBooleanExtra("hasPrev", false)

                updateMediaMetadata()
                updatePlaybackState(isPlaying)

                val artUrl = intent.getStringExtra("artUrl")
                val artChanged = artUrl != lastNotifiedArtUrl
                lastNotifiedArtUrl = artUrl
                if (artChanged) setArtwork(artUrl)

                val notifyChanged = currentTitle != lastNotifiedTitle ||
                    currentDuration != lastNotifiedDuration ||
                    isPlaying != lastNotifiedPlaying ||
                    artChanged
                lastNotifiedTitle = currentTitle
                lastNotifiedDuration = currentDuration
                lastNotifiedPlaying = isPlaying
                if (notifyChanged) startWithNotification()
                val appForeground = (application as? MyTubeApplication)?.container?.playbackManager?.isAppForeground != false
                if (isPlaying) {
                    acquireWakeLock()
                    startHeartbeat()
                    cancelIdleTimeout()
                    cancelBgPauseTimeout()
                } else if (wasPlaying) {
                    if (appForeground) {
                        stopHeartbeat()
                        releaseWakeLock()
                        startIdleTimeout()
                    } else {
                        startHeartbeat()
                        cancelIdleTimeout()
                        startBgPauseTimeout()
                    }
                }
            }
            ACTION_STOP -> stop()
        }
        return START_NOT_STICKY
    }

    private fun startHeartbeat() {
        mainHandler.removeCallbacks(keepAliveRunnable)
        mainHandler.post(keepAliveRunnable)
    }

    private fun stopHeartbeat() {
        mainHandler.removeCallbacks(keepAliveRunnable)
    }

    private fun startIdleTimeout() {
        mainHandler.removeCallbacks(idleTimeoutRunnable)
        mainHandler.postDelayed(idleTimeoutRunnable, 30_000L)
    }

    private fun cancelIdleTimeout() {
        mainHandler.removeCallbacks(idleTimeoutRunnable)
    }

    private fun startBgPauseTimeout() {
        mainHandler.removeCallbacks(bgPauseTimeoutRunnable)
        mainHandler.postDelayed(bgPauseTimeoutRunnable, BG_PAUSE_TIMEOUT_MS)
    }

    private fun cancelBgPauseTimeout() {
        mainHandler.removeCallbacks(bgPauseTimeoutRunnable)
    }

    private fun setupMediaSession() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        mediaSession = MediaSessionCompat(this, "MyTubePlayback").apply {
            setCallback(MediaSessionCallback())
            setSessionActivity(pi)
            isActive = true
        }
    }

    private fun updateMediaMetadata() {
        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, currentDuration)
        artBitmap?.let {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, it)
        }
        mediaSession?.setMetadata(builder.build())
    }

    private fun setArtwork(url: String?) {
        if (url.isNullOrBlank()) {
            if (artBitmap != null) {
                currentArtUrl = null
                artBitmap = null
                accentColor = 0
                updateMediaMetadata()
                startWithNotification()
            }
            return
        }
        if (url == currentArtUrl && artBitmap != null) return
        currentArtUrl = url

        artCache.get(url)?.let {
            applyArtwork(it)
            return
        }
        scope.launch {
            val bitmap = downloadBitmap(url) ?: return@launch
            artCache.put(url, bitmap)
            mainHandler.post {
                if (currentArtUrl == url) applyArtwork(bitmap)
            }
        }
    }

    private fun applyArtwork(bitmap: Bitmap) {
        artBitmap = bitmap
        accentColor = extractAccentColor(bitmap)
        updateMediaMetadata()
        startWithNotification()
    }

    private fun downloadBitmap(url: String): Bitmap? {
        return try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = ART_TIMEOUT_MS
                readTimeout = ART_TIMEOUT_MS
                instanceFollowRedirects = true
            }
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractAccentColor(bitmap: Bitmap): Int {
        val palette = Palette.from(bitmap).generate()
        return palette.getVibrantColor(0)
            .takeIf { it != 0 }
            ?: palette.getDarkVibrantColor(0).takeIf { it != 0 }
            ?: palette.getMutedColor(0).takeIf { it != 0 }
            ?: palette.getDarkMutedColor(0)
    }

    private fun startWithNotification() {
        val actions = mutableListOf<NotificationCompat.Action>()
        var playPauseIndex = 0

        if (hasPrev) {
            actions += NotificationCompat.Action(
                R.drawable.ic_media_prev, "Previous",
                PendingIntent.getService(
                    this, 4,
                    Intent(this, PlaybackService::class.java).setAction(ACTION_SKIP_PREV),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
        }

        playPauseIndex = actions.size
        if (isPlaying) {
            actions += NotificationCompat.Action(
                R.drawable.ic_media_pause, "Pause",
                PendingIntent.getService(
                    this, 1,
                    Intent(this, PlaybackService::class.java).setAction(ACTION_PAUSE),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
        } else {
            actions += NotificationCompat.Action(
                R.drawable.ic_media_play, "Play",
                PendingIntent.getService(
                    this, 2,
                    Intent(this, PlaybackService::class.java).setAction(ACTION_PLAY),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
        }

        if (hasNext) {
            actions += NotificationCompat.Action(
                R.drawable.ic_media_next, "Next",
                PendingIntent.getService(
                    this, 5,
                    Intent(this, PlaybackService::class.java).setAction(ACTION_SKIP_NEXT),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
        }

        actions += NotificationCompat.Action(
            R.drawable.ic_media_stop, "Stop",
            PendingIntent.getService(
                this, 3,
                Intent(this, PlaybackService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE
            )
        )

        val compact = when {
            hasPrev && hasNext -> intArrayOf(playPauseIndex - 1, playPauseIndex, playPauseIndex + 1)
            hasPrev || hasNext -> intArrayOf(0, 1)
            else -> intArrayOf(playPauseIndex)
        }

        val builder = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(if (isPlaying) "Playing in background" else "Paused")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(*compact)
                    .setMediaSession(mediaSession?.sessionToken)
            )
            .setOngoing(isPlaying)

        artBitmap?.let { builder.setLargeIcon(it) }
        if (accentColor != 0) {
            builder.setColor(accentColor)
            builder.setColorized(true)
        }

        actions.forEach { builder.addAction(it) }

        startForeground(Constants.NOTIFICATION_ID, builder.build())
    }

    private fun skipActions(): Long {
        var actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_STOP
        if (hasNext) actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
        if (hasPrev) actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        return actions
    }

    private fun updatePlaybackState(playing: Boolean) {
        val state = if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, currentPosition, 1f)
                .setActions(skipActions())
                .build()
        )
    }

    private fun acquireWakeLock() {
        wakeLock?.let {
            if (!it.isHeld) it.acquire(10 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }

    private fun evaluateJs(script: String) {
        try {
            (application as? MyTubeApplication)?.container?.playbackManager?.jsEvaluator?.invoke(script)
        } catch (_: Exception) { }
    }

    private fun stop() {
        stopHeartbeat()
        cancelIdleTimeout()
        cancelBgPauseTimeout()
        releaseWakeLock()
        mediaSession?.isActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopHeartbeat()
        cancelIdleTimeout()
        cancelBgPauseTimeout()
        releaseWakeLock()
        unregisterReceiver(noisyReceiver)
        mediaSession?.release()
        super.onDestroy()
    }

    private inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        override fun onPause() {
            isPlaying = false
            evaluateJs("window.MyTubePause && window.MyTubePause()")
            updatePlaybackState(false)
            startWithNotification()
            stopHeartbeat()
            releaseWakeLock()
            startIdleTimeout()
            cancelBgPauseTimeout()
        }

        override fun onPlay() {
            isPlaying = true
            evaluateJs("window.MyTubePlay && window.MyTubePlay()")
            updatePlaybackState(true)
            startWithNotification()
            acquireWakeLock()
            startHeartbeat()
            cancelIdleTimeout()
            cancelBgPauseTimeout()
        }

        override fun onSkipToNext() {
            evaluateJs("window.MyTubeNext && window.MyTubeNext()")
        }

        override fun onSkipToPrevious() {
            evaluateJs("window.MyTubePrev && window.MyTubePrev()")
        }

        override fun onStop() {
            stop()
        }
    }

    companion object {
        const val ACTION_START = "com.example.mytube.action.START"
        const val ACTION_PLAY = "com.example.mytube.action.PLAY"
        const val ACTION_PAUSE = "com.example.mytube.action.PAUSE"
        const val ACTION_STOP = "com.example.mytube.action.STOP"
        const val ACTION_SKIP_NEXT = "com.example.mytube.action.SKIP_NEXT"
        const val ACTION_SKIP_PREV = "com.example.mytube.action.SKIP_PREV"
        private const val HEARTBEAT_INTERVAL_MS = 2000L
        private const val BG_PAUSE_TIMEOUT_MS = 60_000L
        private const val ART_TIMEOUT_MS = 8000
    }
}
