package com.safecomp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.safecomp.AppContainer
import com.safecomp.ui.geofence.GeofenceSetupScreen
import com.safecomp.ui.geofence.GeofenceSetupViewModel
import com.safecomp.ui.home.HomeScreen
import com.safecomp.ui.home.HomeViewModel
import com.safecomp.ui.permissions.OnboardingScreen
import com.safecomp.ui.permissions.PermissionsViewModel

private object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val GEOFENCE = "geofence"
}

@Composable
fun SafeCompNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val start = if (container.preferences.onboardingCompleted) Routes.HOME else Routes.ONBOARDING

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
