package com.example.mytube.player

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.scale
import com.example.mytube.MainActivity
import com.example.mytube.R

/**
 * Home-screen widget bound to the [PlaybackService] MediaSession.
 *
 * Button presses go straight to the service using the same actions as the media
 * notification, so the widget and the notification cannot drift apart. Rendering
 * reads a MediaController, so the widget follows the session instead of polling.
 */
class PlaybackWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetSession.connect(context.applicationContext)
        WidgetSession.render(context.applicationContext, appWidgetIds)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetSession.connect(context.applicationContext)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetSession.disconnect()
    }
}

internal object WidgetSession {
    private const val ART_MAX_PX = 256
    private const val REQ_PLAY_PAUSE = 20
    private const val REQ_NEXT = 21
    private const val REQ_PREV = 22
    private const val REQ_OPEN_APP = 23

    private var appContext: Context? = null
    private var browser: MediaBrowserCompat? = null
    private var controller: MediaControllerCompat? = null

    private val connectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            val token = browser?.sessionToken ?: return
            controller = MediaControllerCompat(appContext ?: return, token).also {
                it.registerCallback(controllerCallback)
            }
            refreshAll()
        }

        override fun onConnectionFailed() {
            controller = null
        }
    }

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            refreshAll()
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            refreshAll()
        }
    }

    fun connect(context: Context) {
        appContext = context
        if (browser != null) return
        browser = MediaBrowserCompat(
            context,
            ComponentName(context, PlaybackService::class.java),
            connectionCallback,
            null
        ).also { it.connect() }
    }

    fun disconnect() {
        controller?.unregisterCallback(controllerCallback)
        controller = null
        browser?.disconnect()
        browser = null
        appContext = null
    }

    fun render(context: Context, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        val manager = AppWidgetManager.getInstance(context)
        val views = buildViews(context)
        appWidgetIds.forEach { id ->
            try {
                manager.updateAppWidget(id, views)
            } catch (_: Exception) {
                // Widget was removed between the id lookup and this update.
            }
        }
    }

    private fun refreshAll() {
        val context = appContext ?: return
        val ids = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, PlaybackWidgetProvider::class.java))
        render(context, ids)
    }

    private fun buildViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_playback)
        val metadata = controller?.metadata
        val state = controller?.playbackState

        val isPlaying = state?.state == PlaybackStateCompat.STATE_PLAYING
        val actions = state?.actions ?: 0L
        val hasNext = actions and PlaybackStateCompat.ACTION_SKIP_TO_NEXT ==
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT
        val hasPrev = actions and PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS ==
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS

        views.setTextViewText(
            R.id.widget_title,
            metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
                ?: context.getString(R.string.widget_no_track)
        )

        val art = metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ART)
        if (art != null) {
            views.setImageViewBitmap(R.id.widget_artwork, scaleDown(art, ART_MAX_PX))
        } else {
            views.setImageViewResource(R.id.widget_artwork, R.drawable.ic_youtube_music)
        }

        views.setImageViewResource(
            R.id.widget_play_pause,
            if (isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play
        )

        // RemoteViews has no enabled/disabled support, so reserve the slot with
        // INVISIBLE so the play/pause button stays centred.
        views.setViewVisibility(R.id.widget_next, if (hasNext) View.VISIBLE else View.INVISIBLE)
        views.setViewVisibility(R.id.widget_prev, if (hasPrev) View.VISIBLE else View.INVISIBLE)

        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
        views.setOnClickPendingIntent(
            R.id.widget_play_pause,
            serviceIntent(context, REQ_PLAY_PAUSE, PlaybackService.ACTION_TOGGLE)
        )
        views.setOnClickPendingIntent(
            R.id.widget_next,
            serviceIntent(context, REQ_NEXT, PlaybackService.ACTION_SKIP_NEXT)
        )
        views.setOnClickPendingIntent(
            R.id.widget_prev,
            serviceIntent(context, REQ_PREV, PlaybackService.ACTION_SKIP_PREV)
        )

        return views
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQ_OPEN_APP,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_IMMUTABLE
        )

    private fun serviceIntent(context: Context, requestCode: Int, action: String): PendingIntent =
        PendingIntent.getService(
            context,
            requestCode,
            Intent(context, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE
        )

    /**
     * RemoteViews marshals bitmaps through a binder transaction, so oversized
     * artwork throws TransactionTooLargeException. Cap the longest edge.
     */
    private fun scaleDown(bitmap: Bitmap, maxPx: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxPx) return bitmap
        return bitmap.scale(maxPx, maxPx, true)
    }
}