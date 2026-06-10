package com.example.mytube.player

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.mytube.util.Constants
import java.util.concurrent.atomic.AtomicBoolean

class PlaybackManager(private val context: Context) {
    var isPlaying = false
        private set

    val isServiceRunning = AtomicBoolean(false)

    var jsEvaluator: ((String) -> Unit)? = null

    fun startService() {
        if (!isServiceRunning.compareAndSet(false, true)) return
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopService() {
        isServiceRunning.set(false)
        val intent = Intent(context, PlaybackService::class.java)
        context.stopService(intent)
    }

    fun updatePlaybackState(playing: Boolean) {
        isPlaying = playing
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = if (playing) PlaybackService.ACTION_PLAY else PlaybackService.ACTION_PAUSE
        }
        ContextCompat.startForegroundService(context, intent)
    }
    
    fun updateMetadata(playing: Boolean, title: String, duration: Double, position: Double) {
        isPlaying = playing
        if (playing) startService()
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = Constants.ACTION_UPDATE_METADATA
            putExtra("playing", playing)
            putExtra("title", title)
            putExtra("duration", (duration * 1000).toLong())
            putExtra("position", (position * 1000).toLong())
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun startBackgroundPlayback() {
        isPlaying = true
        updatePlaybackState(true)
    }

    fun stopBackgroundPlayback() {
        stopService()
    }

    fun destroy() {
        stopService()
    }
}
