package dev.anilbeesetti.nextplayer

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.compose.BackHandler
import dev.anilbeesetti.nextplayer.core.common.storagePermission
import dev.anilbeesetti.nextplayer.core.media.services.MediaService
import dev.anilbeesetti.nextplayer.core.media.sync.MediaSynchronizer
import dev.anilbeesetti.nextplayer.core.model.ThemeConfig
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.navigation.MediaRootRoute
import dev.anilbeesetti.nextplayer.navigation.mediaNavGraph
import dev.anilbeesetti.nextplayer.navigation.SETTINGS_ROUTE
import dev.anilbeesetti.nextplayer.navigation.settingsNavGraph
import dev.anilbeesetti.nextplayer.ui.BottomNavBar
import dev.anilbeesetti.nextplayer.ui.BottomNavTab
import dev.anilbeesetti.nextplayer.ui.FileTransferScreen
import dev.anilbeesetti.nextplayer.ui.MeDestination
import dev.anilbeesetti.nextplayer.ui.MeScreen
import dev.anilbeesetti.nextplayer.ui.MusicScreen
import dev.anilbeesetti.nextplayer.ui.PrivacyFolderScreen
import dev.anilbeesetti.nextplayer.ui.TelegramScreen
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var synchronizer: MediaSynchronizer

    @Inject
    lateinit var mediaService: MediaService

    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaService.initialize(this@MainActivity)

        var uiState: MainActivityUiState by mutableStateOf(MainActivityUiState.Loading)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    uiState = state
                }
            }
        }

        installSplashScreen().setKeepOnScreenCondition {
            when (uiState) {
                MainActivityUiState.Loading -> true
                is MainActivityUiState.Success -> false
            }
        }

        setContent {
            val shouldUseDarkTheme = shouldUseDarkTheme(uiState = uiState)

            LaunchedEffect(shouldUseDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        lightScrim = Color.TRANSPARENT,
                        darkScrim = Color.TRANSPARENT,
                        detectDarkMode = { shouldUseDarkTheme },
                    ),
                    navigationBarStyle = SystemBarStyle.auto(
                        lightScrim = Color.TRANSPARENT,
                        darkScrim = Color.TRANSPARENT,
                        detectDarkMode = { shouldUseDarkTheme },
                    ),
                )
            }

            NextPlayerTheme(
                darkTheme = shouldUseDarkTheme,
                highContrastDarkTheme = shouldUseHighContrastDarkTheme(uiState = uiState),
                dynamicColor = shouldUseDynamicTheming(uiState = uiState),
            ) {
                val storagePermissionState = rememberPermissionState(permission = storagePermission)

                LifecycleEventEffect(event = Lifecycle.Event.ON_START) {
                    storagePermissionState.launchPermissionRequest()
                }

                LaunchedEffect(key1 = storagePermissionState.status.isGranted) {
                    if (storagePermissionState.status.isGranted) {
                        synchronizer.startSync()
                    }
                }

                var currentTab by remember { mutableStateOf(BottomNavTab.VIDEOS) }
                var meSubScreen by remember { mutableStateOf<MeSubScreen>(MeSubScreen.Main) }

                Scaffold(
                    bottomBar = {
                        BottomNavBar(
                            currentTab = currentTab,
                            onTabSelected = { newTab ->
                                currentTab = newTab
                                if (newTab != BottomNavTab.ME) meSubScreen = MeSubScreen.Main
                            },
                        )
                    },
                ) { paddingValues ->
                    when (currentTab) {
                        BottomNavTab.VIDEOS -> {
                            Surface(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(paddingValues),
                                color = MaterialTheme.colorScheme.surface,
                            ) {
                                val mainNavController = rememberNavController()

                                NavHost(
                                    navController = mainNavController,
                                    startDestination = MediaRootRoute,
                                    enterTransition = {
                                        slideIntoContainer(
                                            towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                            animationSpec = tween(
                                                durationMillis = 200,
                                                easing = LinearEasing,
                                            ),
                                        )
                                    },
                                    exitTransition = {
                                        slideOutOfContainer(
                                            towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                            animationSpec = tween(
                                                durationMillis = 200,
                                                easing = LinearEasing,
                                            ),
                                            targetOffset = { fullOffset -> (fullOffset * 0.3f).toInt() },
                                        )
                                    },
                                    popEnterTransition = {
                                        slideIntoContainer(
                                            towards = AnimatedContentTransitionScope.SlideDirection.End,
                                            animationSpec = tween(
                                                durationMillis = 200,
                                                easing = LinearEasing,
                                            ),
                                            initialOffset = { fullOffset -> (fullOffset * 0.3f).toInt() },
                                        )
                                    },
                                    popExitTransition = {
                                        slideOutOfContainer(
                                            towards = AnimatedContentTransitionScope.SlideDirection.End,
                                            animationSpec = tween(
                                                durationMillis = 200,
                                                easing = LinearEasing,
                                            ),
                                        )
                                    },
                                ) {
                                    mediaNavGraph(
                                        context = this@MainActivity,
                                        navController = mainNavController,
                                    )
                                    settingsNavGraph(navController = mainNavController)
                                }
                            }
                        }
                        BottomNavTab.MUSIC -> {
                            MusicScreen(modifier = Modifier.padding(paddingValues))
                        }
                        BottomNavTab.ME -> {
                            when (meSubScreen) {
                                MeSubScreen.Main -> MeScreen(
                                    modifier = Modifier.padding(paddingValues),
                                    onNavigate = { dest ->
                                        meSubScreen = when (dest) {
                                            MeDestination.PrivacyFolder -> MeSubScreen.PrivacyFolder
                                            MeDestination.FileTransfer -> MeSubScreen.FileTransfer
                                            MeDestination.AboutUs -> MeSubScreen.AboutUs
                                            MeDestination.Settings -> MeSubScreen.Settings
                                        }
                                    },
                                )
                                MeSubScreen.PrivacyFolder -> PrivacyFolderScreen(
                                    modifier = Modifier.padding(paddingValues),
                                    onNavigateUp = { meSubScreen = MeSubScreen.Main },
                                )
                                MeSubScreen.FileTransfer -> FileTransferScreen(
                                    modifier = Modifier.padding(paddingValues),
                                    onNavigateUp = { meSubScreen = MeSubScreen.Main },
                                )
                                MeSubScreen.AboutUs -> TelegramScreen(
                                    modifier = Modifier.padding(paddingValues),
                                )
                                MeSubScreen.Settings -> {
                                    BackHandler { meSubScreen = MeSubScreen.Main }
                                    val settingsNavController = rememberNavController()
                                    Surface(
                                        modifier = Modifier.fillMaxSize().padding(paddingValues),
                                        color = MaterialTheme.colorScheme.surface,
                                    ) {
                                        NavHost(navController = settingsNavController, startDestination = SETTINGS_ROUTE) {
                                            settingsNavGraph(navController = settingsNavController)
                                        }
                                    }
                                }
                            }
                        }
                        BottomNavTab.TELEGRAM -> {
                            TelegramScreen(modifier = Modifier.padding(paddingValues))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun shouldUseDarkTheme(
    uiState: MainActivityUiState,
): Boolean = when (uiState) {
    MainActivityUiState.Loading -> isSystemInDarkTheme()
    is MainActivityUiState.Success -> when (uiState.preferences.themeConfig) {
        ThemeConfig.SYSTEM -> isSystemInDarkTheme()
        ThemeConfig.OFF -> false
        ThemeConfig.ON -> true
    }
}

@Composable
fun shouldUseHighContrastDarkTheme(
    uiState: MainActivityUiState,
): Boolean = when (uiState) {
    MainActivityUiState.Loading -> false
    is MainActivityUiState.Success -> uiState.preferences.useHighContrastDarkTheme
}

@Composable
fun shouldUseDynamicTheming(
    uiState: MainActivityUiState,
): Boolean = when (uiState) {
    MainActivityUiState.Loading -> false
    is MainActivityUiState.Success -> uiState.preferences.useDynamicColors
}


sealed class MeSubScreen {
    object Main : MeSubScreen()
    object PrivacyFolder : MeSubScreen()
    object FileTransfer : MeSubScreen()
    object AboutUs : MeSubScreen()
    object Settings : MeSubScreen()
}
