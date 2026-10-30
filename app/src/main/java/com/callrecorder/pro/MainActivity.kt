package com.callrecorder.pro

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.callrecorder.pro.di.AppContainer
import com.callrecorder.pro.security.AppLock
import com.callrecorder.pro.ui.HomeViewModel
import com.callrecorder.pro.ui.PlayerViewModel
import com.callrecorder.pro.ui.screens.HomeScreen
import com.callrecorder.pro.ui.screens.OnboardingScreen
import com.callrecorder.pro.ui.screens.PlayerScreen
import com.callrecorder.pro.ui.screens.SettingsScreen
import com.callrecorder.pro.ui.screens.SourceTestScreen
import com.callrecorder.pro.ui.theme.CallRecorderTheme
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as CallRecorderApp).container
        setContent { AppRoot(container) }
    }
}

@Composable
private fun AppRoot(container: AppContainer) {
    val settings by container.settings.settings.collectAsState(initial = null)
    val s = settings ?: return

    LaunchedEffect(s.language) {
        val tags = when (s.language) {
            "en" -> LocaleListCompat.forLanguageTags("en")
            "ar" -> LocaleListCompat.forLanguageTags("ar")
            else -> LocaleListCompat.getEmptyLocaleList()
        }
        if (tags != AppCompatDelegate.getApplicationLocales()) {
            AppCompatDelegate.setApplicationLocales(tags)
        }
    }

    CallRecorderTheme {
        if (!s.onboardingDone || !s.legalAccepted) {
            OnboardingScreen(container.settings) {}
            return@CallRecorderTheme
        }

        val nav = rememberNavController()
        var unlocked by remember(s.appLock) { mutableStateOf(!s.appLock) }

        val activity = androidx.compose.ui.platform.LocalContext.current as MainActivity
        LaunchedEffect(s.appLock) {
            if (s.appLock && !unlocked) {
                AppLock.show(
                    activity = activity,
                    onSuccess = { unlocked = true },
                    onUnavailable = {
                        // No biometrics: disable the lock rather than brick the app.
                        unlocked = true
                        activity.lifecycleScope.launch {
                            container.settings.setAppLock(false)
                        }
                    }
                )
            }
        }
        if (!unlocked) return@CallRecorderTheme

        NavHost(navController = nav, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    vm = viewModel(
                        factory = HomeViewModel.factory(
                            container.recordingDao, container.settings
                        )
                    ),
                    onOpenPlayer = { id -> nav.navigate("player/$id") },
                    onOpenSettings = { nav.navigate("settings") }
                )
            }
            composable("settings") {
                SettingsScreen(container, onOpenSourceTest = { nav.navigate("sourcetest") })
            }
            composable("sourcetest") { SourceTestScreen(container) }
            composable(
                route = "player/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                PlayerScreen(
                    vm = viewModel(
                        factory = PlayerViewModel.factory(
                            entry.arguments!!.getLong("id"),
                            container.recordingDao,
                            container.bookmarkDao
                        )
                    ),
                    onBack = { nav.popBackStack() }
                )
            }
        }
    }
}
