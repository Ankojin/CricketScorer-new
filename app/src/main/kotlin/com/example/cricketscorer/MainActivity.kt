package com.example.cricketscorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CricketScorerTheme {
                MainNavigation()
            }
        }
    }
}

@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    val scoringViewModel: ScoringViewModel = viewModel()
    val tournamentViewModel: TournamentViewModel = viewModel()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                val items = listOf(
                    NavigationItem("Home", "home", Icons.Default.Home),
                    NavigationItem("Matches", "dashboard", Icons.AutoMirrored.Filled.List),
                    NavigationItem("Scoring", "live_scoring", Icons.Default.PlayArrow)
                )
                
                items.forEach { item ->
                    val isSelected = currentDestination?.hierarchy?.any { 
                        it.route == item.route || (item.route == "dashboard" && it.route?.startsWith("tournament_details") == true)
                    } == true

                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = isSelected,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                HomeScreen(
                    onNavigateToDashboard = { navController.navigate("dashboard") },
                    onNavigateToLiveScoring = { 
                        // If there's an active match in ViewModel, go to it
                        // For now just navigate
                        navController.navigate("live_scoring")
                    }
                )
            }
            composable("dashboard") {
                DashboardScreen(
                    viewModel = tournamentViewModel,
                    onNavigateToTournament = { tournamentId ->
                        navController.navigate("tournament_details/$tournamentId")
                    }
                )
            }
            composable("live_scoring") {
                LiveScoringScreen(
                    viewModel = scoringViewModel,
                    onNavigateToDashboard = { navController.navigate("dashboard") }
                )
            }
            composable(
                route = "tournament_details/{tournamentId}",
                arguments = listOf(navArgument("tournamentId") { type = NavType.StringType })
            ) { backStackEntry ->
                val tournamentId = backStackEntry.arguments?.getString("tournamentId") ?: ""
                TournamentDetailsScreen(
                    tournamentId = tournamentId,
                    viewModel = tournamentViewModel,
                    onBack = { navController.popBackStack() },
                    onStartMatch = { match ->
                        scoringViewModel.loadMatch(match)
                        navController.navigate("live_scoring")
                    }
                )
            }
        }
    }
}

data class NavigationItem(val label: String, val route: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun CricketScorerTheme(content: @Composable () -> Unit) {
    val primaryColor = Color(0xFF003366) 
    val secondaryColor = Color(0xFF00AA55) 

    val colorScheme = lightColorScheme(
        primary = primaryColor,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE3F2FD),
        onPrimaryContainer = primaryColor,
        secondary = secondaryColor,
        onSecondary = Color.White,
        surface = Color.White,
        background = Color(0xFFF5F5F5)
    )

    MaterialTheme(
        colorScheme = colorScheme,
        content = {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                content()
            }
        }
    )
}
