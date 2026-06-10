package com.example.mytube.di

import android.content.Context
import com.example.mytube.adblock.AdBlockManager
import com.example.mytube.adblock.FilterListUpdater
import com.example.mytube.browser.WebViewManager
import com.example.mytube.data.LocalDatabase
import com.example.mytube.injection.ScriptManager
import com.example.mytube.injection.ScriptRepository
import com.example.mytube.player.PlaybackManager
import com.example.mytube.util.PreferencesManager

class AppContainer(context: Context) {
    private val db = LocalDatabase(context)
    val prefsManager = PreferencesManager(context)

    val scriptRepository = ScriptRepository(db)
    val webViewManager = WebViewManager()
    val filterListUpdater = FilterListUpdater(context)
    val adBlockManager = AdBlockManager(filterListUpdater)
    val scriptManager = ScriptManager(context, scriptRepository)
    val playbackManager = PlaybackManager(context)
}
