package com.swcsoftware.valuelens.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.swcsoftware.valuelens.AppState
import com.swcsoftware.valuelens.domain.CompanyRef
import com.swcsoftware.valuelens.ui.screens.CaseStudyScreen
import com.swcsoftware.valuelens.ui.screens.CompanyDetailScreen
import com.swcsoftware.valuelens.ui.screens.GlossaryScreen
import com.swcsoftware.valuelens.ui.screens.IdentityScreen
import com.swcsoftware.valuelens.ui.screens.LensChoiceScreen
import com.swcsoftware.valuelens.ui.screens.SearchScreen
import com.swcsoftware.valuelens.ui.screens.SettingsScreen
import com.swcsoftware.valuelens.ui.screens.WatchlistScreen
import com.swcsoftware.valuelens.ui.theme.VL
import com.swcsoftware.valuelens.ui.theme.ValueLensTheme

@Composable
fun ValueLensApp(state: AppState) {
    // The three axes are resolved here, once, and provided to everything below (Sprint 5 Track B).
    ValueLensTheme(preference = state.themePreference, accentArgb = state.accentArgb) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route ?: ""
        val onboarding = !(state.onboarded && state.identity != null)
        val showTabs = !onboarding && route in setOf("watchlist", "search", "settings")

        // Deep link: navigate once the main UI is up.
        LaunchedEffect(state.pendingTicker, onboarding) {
            val t = state.pendingTicker ?: return@LaunchedEffect
            if (onboarding) return@LaunchedEffect
            state.pendingTicker = null
            val known = state.watchlist.firstOrNull { it.company.ticker == t }?.company
            nav.navigate("company/${t}/${known?.cik ?: 0}/${java.net.URLEncoder.encode(known?.name ?: t, "UTF-8")}")
        }

        Scaffold(containerColor = VL.background, bottomBar = {
            if (showTabs) NavigationBar(containerColor = VL.surface) {
                listOf(Triple("watchlist", "Watchlist", Icons.Filled.List), Triple("search", "Search", Icons.Filled.Search), Triple("settings", "Settings", Icons.Filled.Settings)).forEach { (r, label, icon) ->
                    NavigationBarItem(selected = route == r, onClick = { nav.navigate(r) { popUpTo("watchlist") { saveState = true }; launchSingleTop = true; restoreState = true } },
                        icon = { Icon(icon, label) }, label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = VL.value, selectedTextColor = VL.value, unselectedIconColor = VL.textTertiary, unselectedTextColor = VL.textTertiary, indicatorColor = VL.raised))
                }
            }
        }) { pad ->
            fun open(c: CompanyRef) = nav.navigate("company/${c.ticker}/${c.cik}/${java.net.URLEncoder.encode(c.name, "UTF-8")}")
            NavHost(nav, startDestination = if (onboarding) "identity" else "watchlist", modifier = Modifier.padding(pad)) {
                // Identity → investor lens → guided valuation (Sprint 5 Track D).
                composable("identity") { IdentityScreen(state) { nav.navigate("lens") } }
                composable("lens") { LensChoiceScreen(state, onBack = { nav.popBackStack() }) { nav.navigate("casestudy") } }
                composable("casestudy") { CaseStudyScreen(state) { nav.navigate("watchlist") { popUpTo(0) } } }
                composable("watchlist") { WatchlistScreen(state, ::open) { nav.navigate("search") } }
                composable("search") { SearchScreen(state, ::open) }
                composable("settings") { SettingsScreen(state, onReplayOnboarding = { nav.navigate("identity") { popUpTo(0) } }, onGlossary = { nav.navigate("glossary") }) }
                composable("glossary") { GlossaryScreen(state) { nav.popBackStack() } }
                composable("company/{ticker}/{cik}/{name}", arguments = listOf(navArgument("ticker") { type = NavType.StringType }, navArgument("cik") { type = NavType.LongType }, navArgument("name") { type = NavType.StringType })) { e ->
                    val c = CompanyRef(e.arguments!!.getString("ticker")!!, e.arguments!!.getLong("cik"), java.net.URLDecoder.decode(e.arguments!!.getString("name")!!, "UTF-8"))
                    CompanyDetailScreen(state, c, onBack = { nav.popBackStack() }, onOpenSettings = { nav.navigate("settings") })
                }
            }
        }
    }
}
