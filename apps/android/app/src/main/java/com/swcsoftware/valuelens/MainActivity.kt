package com.swcsoftware.valuelens

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.swcsoftware.valuelens.ui.ValueLensApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val state get() = (application as ValueLensApplication).state

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state.handleDeepLink(intent?.data)
        // Health check + watchlist refresh whenever the app comes to the foreground.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                state.refreshRates()
                val stale = state.watchlistStore.lastUpdated?.let { System.currentTimeMillis() - it > 15 * 60 * 1000 } ?: true
                if (state.identity != null && stale) state.refreshWatchlist()
            }
        }
        setContent { ValueLensApp(state) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        state.handleDeepLink(intent.data)
    }
}
