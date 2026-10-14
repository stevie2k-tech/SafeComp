package com.safecomp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.safecomp.AppContainer
import com.safecomp.ui.components.LocalTopBarAccessory
import com.safecomp.ui.focus.FocusSettingsScreen
import com.safecomp.ui.focus.FocusSettingsViewModel
import com.safecomp.ui.geofence.GeofenceSetupScreen
import com.safecomp.ui.geofence.GeofenceSetupViewModel
import com.safecomp.ui.home.HomeScreen
import com.safecomp.ui.log.EventLogScreen
import com.safecomp.ui.log.EventLogViewModel
import com.safecomp.ui.home.HomeViewModel
import com.safecomp.ui.permissions.OnboardingScreen
import com.safecomp.ui.permissions.PermissionsViewModel
import com.safecomp.ui.vault.VaultScreen
import com.safecomp.ui.vault.VaultViewModel
import com.safecomp.ui.viewer.ViewerScreen
import com.safecomp.ui.viewer.ViewerViewModel

private object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val GEOFENCE = "geofence"
    const val VAULT = "vault"
    const val LOG = "log"
    const val FOCUS = "focus"
    const val VIEWER = "viewer/{fileId}"
    fun viewer(fileId: String) = "viewer/$fileId"
}

@Composable
fun SafeCompNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val start = if (container.preferences.onboardingCompleted) Routes.HOME else Routes.ONBOARDING

    // Close any open file when the vault locks or the app goes to the background.
    // The viewer has already wiped its content by then; this takes the user back to the list.
    LaunchedEffect(navController) {
        container.vaultSession.closeViewers.collect {
            if (navController.currentDestination?.route == Routes.VIEWER) {
                navController.popBackStack(Routes.VIEWER, inclusive = true)
            }
        }
    }

    // Debug builds draw a DEMO MODE badge in every top bar while demo mode is on, so a demo
    // is never mistaken for the real thing. (In release builds the badge draws nothing.)
    CompositionLocalProvider(LocalTopBarAccessory provides { container.demoMode.Badge(Modifier) }) {
        NavHost(navController = navController, startDestination = start) {
            composable(Routes.ONBOARDING) {
                val vm: PermissionsViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { PermissionsViewModel(container.permissionChecker, container.preferences) }
                    },
                )
                OnboardingScreen(
                    viewModel = vm,
                    onFinished = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Routes.HOME) {
                val vm: HomeViewModel = viewModel(
                    factory = viewModelFactory { initializer { HomeViewModel(container) } },
                )
                HomeScreen(
                    viewModel = vm,
                    onReviewPermissions = {
                        navController.navigate(Routes.ONBOARDING) { launchSingleTop = true }
                    },
                    onEditOffice = { navController.navigate(Routes.GEOFENCE) { launchSingleTop = true } },
                    onOpenVault = { navController.navigate(Routes.VAULT) { launchSingleTop = true } },
                    onOpenLog = { navController.navigate(Routes.LOG) { launchSingleTop = true } },
                    onOpenFocus = { navController.navigate(Routes.FOCUS) { launchSingleTop = true } },
                    demoPanel = { container.demoMode.ControlPanel(Modifier) },
                )
            }
            composable(Routes.LOG) {
                val vm: EventLogViewModel = viewModel(
                    factory = viewModelFactory { initializer { EventLogViewModel(container) } },
                )
                EventLogScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(Routes.FOCUS) {
                val vm: FocusSettingsViewModel = viewModel(
                    factory = viewModelFactory { initializer { FocusSettingsViewModel(container) } },
                )
                FocusSettingsScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(Routes.VAULT) {
                val vm: VaultViewModel = viewModel(
                    factory = viewModelFactory { initializer { VaultViewModel(container) } },
                )
                VaultScreen(
                    viewModel = vm,
                    graceSecondsLeft = container.vaultSession.graceSecondsLeft,
                    onBack = { navController.popBackStack() },
                    onOpenFile = { id -> navController.navigate(Routes.viewer(id)) { launchSingleTop = true } },
                )
            }
            composable(
                route = Routes.VIEWER,
                arguments = listOf(navArgument("fileId") { type = NavType.StringType }),
            ) { entry ->
                val fileId = entry.arguments?.getString("fileId").orEmpty()
                val vm: ViewerViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            ViewerViewModel(this[APPLICATION_KEY]!!, container, fileId)
                        }
                    },
                )
                ViewerScreen(
                    viewModel = vm,
                    graceSecondsLeft = container.vaultSession.graceSecondsLeft,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.GEOFENCE) {
                val vm: GeofenceSetupViewModel = viewModel(
                    factory = viewModelFactory { initializer { GeofenceSetupViewModel(container) } },
                )
                GeofenceSetupScreen(viewModel = vm, onDone = { navController.popBackStack() })
            }
        }
    }
}
