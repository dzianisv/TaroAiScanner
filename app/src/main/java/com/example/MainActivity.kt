package com.example
 
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.TarotDatabase
import com.example.data.TarotRepository
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.TarotDashboardScreen
import com.example.ui.screens.TarotScannerScreen
import com.example.ui.screens.ReadingResultScreen
import com.example.ui.viewmodel.TarotViewModel
import com.example.ui.viewmodel.TarotViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Initialize local Room Database & Repository
                    val database = TarotDatabase.getDatabase(applicationContext)
                    val repository = TarotRepository(database.tarotDao())
                    
                    // Create TarotViewModel with custom Factory
                    val tarotViewModel: TarotViewModel = viewModel(
                        factory = TarotViewModelFactory(repository)
                    )

                    // Reactively observe settings and Auth state
                    val settingsState by tarotViewModel.settingsState.collectAsState()

                    if (!settingsState.isSignedIn) {
                        // User is unauthenticated - show onboarding / login options
                        AuthScreen(viewModel = tarotViewModel)
                    } else {
                        // User is authenticated - show the core application experience
                        val navController = rememberNavController()

                        NavHost(
                            navController = navController,
                            startDestination = "dashboard",
                            enterTransition = { fadeIn() },
                            exitTransition = { fadeOut() }
                        ) {
                            composable("dashboard") {
                                TarotDashboardScreen(
                                    viewModel = tarotViewModel,
                                    onSelectSpread = { spreadId ->
                                        tarotViewModel.setSpreadType(spreadId)
                                        navController.navigate("scanner")
                                    }
                                )
                            }
                            composable("scanner") {
                                TarotScannerScreen(
                                    viewModel = tarotViewModel,
                                    onBack = {
                                        navController.popBackStack()
                                    },
                                    onNavToReading = {
                                        navController.navigate("reading")
                                    }
                                )
                            }
                            composable("reading") {
                                ReadingResultScreen(
                                    viewModel = tarotViewModel,
                                    onRestart = {
                                        navController.navigate("dashboard") {
                                            popUpTo(0) // Clear backstack
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
