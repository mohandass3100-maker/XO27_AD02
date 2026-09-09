package com.acoulink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.acoulink.navigation.AppNavigation
import com.acoulink.ui.theme.AcouLinkTheme
import com.acoulink.viewmodel.AppThemeMode
import com.acoulink.viewmodel.HistoryViewModel
import com.acoulink.viewmodel.HomeViewModel
import com.acoulink.viewmodel.ReceiveViewModel
import com.acoulink.viewmodel.SendViewModel
import com.acoulink.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels()
    private val sendViewModel: SendViewModel by viewModels()
    private val receiveViewModel: ReceiveViewModel by viewModels()
    private val historyViewModel: HistoryViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settingsUiState by settingsViewModel.uiState.collectAsState()
            val isDarkTheme = when (settingsUiState.themeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            AcouLinkTheme(darkTheme = isDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        homeViewModel = homeViewModel,
                        sendViewModel = sendViewModel,
                        receiveViewModel = receiveViewModel,
                        historyViewModel = historyViewModel,
                        settingsViewModel = settingsViewModel
                    )
                }
            }
        }
    }
}
