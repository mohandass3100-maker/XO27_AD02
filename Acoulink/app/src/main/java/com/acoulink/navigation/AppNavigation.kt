package com.acoulink.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.acoulink.ui.HistoryScreen
import com.acoulink.ui.HomeScreen
import com.acoulink.ui.MessageDetailsScreen
import com.acoulink.ui.MessageReceivedScreen
import com.acoulink.ui.ReceiveScreen
import com.acoulink.ui.ReceivingScreen
import com.acoulink.ui.RecoveryScreen
import com.acoulink.ui.SendScreen
import com.acoulink.ui.SettingsScreen
import com.acoulink.ui.SplashScreen
import com.acoulink.ui.TransmissionScreen
import com.acoulink.viewmodel.HistoryViewModel
import com.acoulink.viewmodel.HomeViewModel
import com.acoulink.viewmodel.ReceiveViewModel
import com.acoulink.viewmodel.SendViewModel
import com.acoulink.viewmodel.SettingsViewModel

object AppRoutes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val SEND = "send"
    const val TRANSMISSION = "transmission"
    const val RECEIVE = "receive"
    const val RECEIVING = "receiving"
    const val RECOVERY = "recovery"
    const val RECEIVED = "received"
    const val HISTORY = "history"
    const val DETAILS = "details"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    homeViewModel: HomeViewModel = viewModel(),
    sendViewModel: SendViewModel = viewModel(),
    receiveViewModel: ReceiveViewModel = viewModel(),
    historyViewModel: HistoryViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    NavHost(
        navController = navController,
        startDestination = AppRoutes.SPLASH
    ) {
        composable(AppRoutes.SPLASH) {
            SplashScreen(
                onNavigateToHome = {
                    navController.navigate(AppRoutes.HOME) {
                        popUpTo(AppRoutes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(AppRoutes.HOME) {
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToSend = { navController.navigate(AppRoutes.SEND) },
                onNavigateToReceive = { navController.navigate(AppRoutes.RECEIVE) },
                onNavigateToHistory = { navController.navigate(AppRoutes.HISTORY) },
                onNavigateToSettings = { navController.navigate(AppRoutes.SETTINGS) }
            )
        }

        composable(AppRoutes.SEND) {
            SendScreen(
                viewModel = sendViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTransmission = { navController.navigate(AppRoutes.TRANSMISSION) }
            )
        }

        composable(AppRoutes.TRANSMISSION) {
            TransmissionScreen(
                viewModel = sendViewModel,
                onNavigateBack = {
                    navController.navigate(AppRoutes.HOME) {
                        popUpTo(AppRoutes.HOME) { inclusive = false }
                    }
                }
            )
        }

        composable(AppRoutes.RECEIVE) {
            ReceiveScreen(
                viewModel = receiveViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToReceiving = { navController.navigate(AppRoutes.RECEIVING) },
                onNavigateToRecovery = { navController.navigate(AppRoutes.RECOVERY) },
                onNavigateToReceived = { navController.navigate(AppRoutes.RECEIVED) }
            )
        }

        composable(AppRoutes.RECEIVING) {
            ReceivingScreen(
                viewModel = receiveViewModel,
                onNavigateBack = {
                    navController.navigate(AppRoutes.HOME) {
                        popUpTo(AppRoutes.HOME) { inclusive = false }
                    }
                },
                onNavigateToRecovery = { navController.navigate(AppRoutes.RECOVERY) },
                onNavigateToReceived = { navController.navigate(AppRoutes.RECEIVED) }
            )
        }

        composable(AppRoutes.RECOVERY) {
            RecoveryScreen(
                viewModel = receiveViewModel,
                onNavigateToReceived = {
                    navController.navigate(AppRoutes.RECEIVED) {
                        popUpTo(AppRoutes.RECEIVE) { inclusive = false }
                    }
                }
            )
        }

        composable(AppRoutes.RECEIVED) {
            MessageReceivedScreen(
                viewModel = receiveViewModel,
                onNavigateToHome = {
                    navController.navigate(AppRoutes.HOME) {
                        popUpTo(AppRoutes.HOME) { inclusive = false }
                    }
                },
                onNavigateToHistory = {
                    navController.navigate(AppRoutes.HISTORY) {
                        popUpTo(AppRoutes.HOME) { inclusive = false }
                    }
                }
            )
        }

        composable(AppRoutes.HISTORY) {
            HistoryScreen(
                viewModel = historyViewModel,
                onNavigateToHome = {
                    navController.navigate(AppRoutes.HOME) {
                        popUpTo(AppRoutes.HOME) { inclusive = false }
                    }
                },
                onNavigateToSettings = { navController.navigate(AppRoutes.SETTINGS) },
                onNavigateToDetails = { navController.navigate(AppRoutes.DETAILS) }
            )
        }

        composable(AppRoutes.DETAILS) {
            val historyUiState by historyViewModel.uiState.collectAsState()
            MessageDetailsScreen(
                message = historyUiState.selectedMessageForDetails,
                onNavigateBack = { navController.popBackStack() },
                onDeleteMessage = { id -> historyViewModel.deleteMessage(id) }
            )
        }

        composable(AppRoutes.SETTINGS) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateToHome = {
                    navController.navigate(AppRoutes.HOME) {
                        popUpTo(AppRoutes.HOME) { inclusive = false }
                    }
                },
                onNavigateToHistory = { navController.navigate(AppRoutes.HISTORY) }
            )
        }
    }
}
