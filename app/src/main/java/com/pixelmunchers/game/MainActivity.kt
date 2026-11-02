package com.pixelmunchers.game

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import android.view.View

class MainActivity : Activity() {

    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gameView = GameView(this)
        setContentView(gameView)

        // Match the system bars to the game background
        window.statusBarColor = gameView.cBg
        window.navigationBarColor = gameView.cBg
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        if (!dark) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Back on the level screen returns to the game instead of closing the app
        if (!gameView.onBack()) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
