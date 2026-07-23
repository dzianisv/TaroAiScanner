package com.example
 
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.billing.BillingManager
import com.example.data.TarotDatabase
import com.example.data.TarotRepository
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.PaywallScreen
import com.example.ui.screens.TarotDashboardScreen
import com.example.ui.screens.TarotScannerScreen
import com.example.ui.screens.ReadingResultScreen
import com.example.ui.screens.TarotChatScreen
import com.example.ui.screens.TarotVirtualDrawScreen
import com.example.ui.viewmodel.TarotViewModel
import com.example.ui.viewmodel.TarotViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private lateinit var billingManager: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // BillingClient needs an Activity for launchBillingFlow; hold it here.
        billingManager = BillingManager(this)
        billingManager.startConnection()

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

                    // Feed billing entitlement into the ViewModel once.
                    LaunchedEffect(Unit) {
                        tarotViewModel.bindPremiumFlow(billingManager.isPremium)
                    }

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
                                    onScanCard = { spreadId ->
                                        tarotViewModel.setSpreadType(spreadId)
                                        if (tarotViewModel.canDoReading()) {
                                            navController.navigate("scanner")
                                        } else {
                                            navController.navigate("paywall")
                                        }
                                    },
                                    onNavToChat = {
                                        navController.navigate("chat")
                                    },
                                    onNavToVirtualDraw = {
                                        if (tarotViewModel.canDoReading()) {
                                            navController.navigate("virtual_draw")
                                        } else {
                                            navController.navigate("paywall")
                                        }
                                    },
                                    onUpgrade = {
                                        navController.navigate("paywall")
                                    }
                                )
                            }
                            composable("paywall") {
                                val priceText by billingManager.priceText.collectAsState()
                                PaywallScreen(
                                    priceText = priceText,
                                    onSubscribe = {
                                        billingManager.launchBillingFlow(this@MainActivity)
                                    },
                                    onDismiss = { navController.popBackStack() }
                                )
                            }
                            composable("chat") {
                                TarotChatScreen(
                                    viewModel = tarotViewModel,
                                    onBack = {
                                        navController.popBackStack()
                                    }
                                )
                            }
                            composable("virtual_draw") {
                                TarotVirtualDrawScreen(
                                    viewModel = tarotViewModel,
                                    onBack = {
                                        navController.popBackStack()
                                    },
                                    onNavToReading = {
                                        navController.navigate("reading")
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

    override fun onResume() {
        super.onResume()
        // Restore entitlement on return (e.g. after out-of-app purchase changes).
        if (::billingManager.isInitialized) billingManager.queryPurchases()
    }

    override fun onDestroy() {
        if (::billingManager.isInitialized) billingManager.endConnection()
        super.onDestroy()
    }
}
