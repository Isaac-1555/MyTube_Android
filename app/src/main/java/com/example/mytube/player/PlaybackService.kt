package com.example.mytube.player

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.media.AudioManager
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import com.example.mytube.MainActivity
import com.example.mytube.MyTubeApplication
import com.example.mytube.R
import com.example.mytube.util.Constants

class PlaybackService : Service() {
    private var mediaSession: MediaSessionCompat? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isPlaying = false
    private var currentTitle = "MyTube"
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
    private var lastNotifiedTitle = ""
    private var lastNotifiedDuration = -1L
    private var lastNotifiedPlaying = false

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
                startWithNotification()
            }
            ACTION_PLAY -> {
                isPlaying = true
                evaluateJs("window.MyTubePlay && window.MyTubePlay()")
                updatePlaybackState(true)
                startWithNotification()
                startHeartbeat()
                cancelIdleTimeout()
            }
            ACTION_PAUSE -> {
                isPlaying = false
                evaluateJs("window.MyTubePause && window.MyTubePause()")
                updatePlaybackState(false)
                startWithNotification()
                stopHeartbeat()
                releaseWakeLock()
                startIdleTimeout()
            }
            Constants.ACTION_UPDATE_METADATA -> {
                val wasPlaying = isPlaying
                isPlaying = intent.getBooleanExtra("playing", false)
                currentTitle = intent.getStringExtra("title") ?: "MyTube"
                val duration = intent.getLongExtra("duration", 0L)
                val position = intent.getLongExtra("position", 0L)
                
                mediaSession?.setMetadata(
                    MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                        .build()
                )
                
                mediaSession?.setPlaybackState(
                    PlaybackStateCompat.Builder()
                        .setState(
                            if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED, 
                            position, 
                            1f
                        )
                        .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_STOP)
                        .build()
                )
                val notifyChanged = currentTitle != lastNotifiedTitle ||
                    duration != lastNotifiedDuration ||
                    isPlaying != lastNotifiedPlaying
                lastNotifiedTitle = currentTitle
                lastNotifiedDuration = duration
                lastNotifiedPlaying = isPlaying
                if (notifyChanged) startWithNotification()
                val appForeground = (application as? MyTubeApplication)?.container?.playbackManager?.isAppForeground != false
                if (isPlaying) {
                    acquireWakeLock()
                    startHeartbeat()
                    cancelIdleTimeout()
                } else if (wasPlaying) {
                    if (appForeground) {
                        stopHeartbeat()
                        releaseWakeLock()
                        startIdleTimeout()
                    } else {
                        startHeartbeat()
                        cancelIdleTimeout()
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

    private fun startWithNotification() {
        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(android.R.drawable.ic_media_pause, "Pause", 
                PendingIntent.getService(this, 1, Intent(this, PlaybackService::class.java).setAction(ACTION_PAUSE), PendingIntent.FLAG_IMMUTABLE))
        } else {
            NotificationCompat.Action(android.R.drawable.ic_media_play, "Play", 
                PendingIntent.getService(this, 2, Intent(this, PlaybackService::class.java).setAction(ACTION_PLAY), PendingIntent.FLAG_IMMUTABLE))
        }
        
        val stopAction = NotificationCompat.Action(android.R.drawable.ic_menu_close_clear_cancel, "Stop",
            PendingIntent.getService(this, 3, Intent(this, PlaybackService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE))

        val notification = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText("Playing in background")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .addAction(playPauseAction)
            .addAction(stopAction)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1)
                .setMediaSession(mediaSession?.sessionToken))
            .setOngoing(isPlaying)
            .build()

        startForeground(Constants.NOTIFICATION_ID, notification)
    }

    private fun updatePlaybackState(playing: Boolean) {
        val state = if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_STOP)
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
        releaseWakeLock()
        mediaSession?.isActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopHeartbeat()
        cancelIdleTimeout()
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
            stopHeartbeat()
            releaseWakeLock()
            startIdleTimeout()
        }

        override fun onPlay() {
            isPlaying = true
            evaluateJs("window.MyTubePlay && window.MyTubePlay()")
            updatePlaybackState(true)
            acquireWakeLock()
            startHeartbeat()
            cancelIdleTimeout()
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
        private const val HEARTBEAT_INTERVAL_MS = 2000L
    }
}
