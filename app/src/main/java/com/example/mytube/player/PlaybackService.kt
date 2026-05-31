package com.example.mytube.player

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
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
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isPlaying = false
    private var currentTitle = "MyTube"

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                evaluateJs("window.MyTubePause && window.MyTubePause()")
            }
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                evaluateJs("window.MyTubePause && window.MyTubePause()")
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                evaluateJs("window.MyTubePlay && window.MyTubePlay()")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
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
                requestAudioFocus()
                if (wakeLock?.isHeld == false) wakeLock?.acquire(10 * 60 * 1000L /*10 minutes*/)
                startWithNotification()
            }
            ACTION_PLAY -> {
                isPlaying = true
                evaluateJs("window.MyTubePlay && window.MyTubePlay()")
                updatePlaybackState(true)
                startWithNotification()
            }
            ACTION_PAUSE -> {
                isPlaying = false
                evaluateJs("window.MyTubePause && window.MyTubePause()")
                updatePlaybackState(false)
                startWithNotification()
            }
            Constants.ACTION_UPDATE_METADATA -> {
                isPlaying = intent.getBooleanExtra("playing", false)
                currentTitle = intent.getStringExtra("title") ?: "MyTube"
                val duration = intent.getLongExtra("duration", 0L)
                val position = intent.getLongExtra("position", 0L)
                
                if (isPlaying) {
                    requestAudioFocus()
                    if (wakeLock?.isHeld == false) wakeLock?.acquire(10 * 60 * 1000L)
                }
                
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
                startWithNotification()
            }
            ACTION_STOP -> stop()
        }
        return START_STICKY
    }
    
    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }
    
    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
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
    
    private fun evaluateJs(script: String) {
        (application as? MyTubeApplication)?.container?.playbackManager?.jsEvaluator?.invoke(script)
    }

    private fun stop() {
        mediaSession?.isActive = false
        if (wakeLock?.isHeld == true) wakeLock?.release()
        abandonAudioFocus()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        unregisterReceiver(noisyReceiver)
        mediaSession?.release()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        abandonAudioFocus()
        super.onDestroy()
    }

    private inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        override fun onPause() {
            evaluateJs("window.MyTubePause && window.MyTubePause()")
        }

        override fun onPlay() {
            evaluateJs("window.MyTubePlay && window.MyTubePlay()")
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
    }
}
