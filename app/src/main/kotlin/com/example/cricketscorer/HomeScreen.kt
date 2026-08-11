package com.example.cricketscorer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ScoringViewModel,
    onNavigateToDashboard: () -> Unit,
    onNavigateToLiveScoring: (Match) -> Unit
) {
    val tournaments by TournamentRepository.tournaments.collectAsState()
    val liveMatch = tournaments.flatMap { it.matches }.find { it.status == MatchStatus.LIVE }
    var showSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "CricScore Pro",
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1).sp,
                        color = Color.White
                    )
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Live Match Section
                if (liveMatch != null) {
                    item {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "LIVE MATCH",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(8.dp))
                            val isSyncEnabled by viewModel.isSyncEnabled.collectAsState()
                            if (isSyncEnabled) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = CircleShape
                                ) {
                                    Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.secondary)
                                        Text(" SYNC ACTIVE", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                            }
                        }
                        
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                                .clickable { onNavigateToLiveScoring(liveMatch) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            "${liveMatch.teamA.name} vs ${liveMatch.teamB.name}",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Black
                                        )
                                        Text(
                                            "Innings ${liveMatch.currentInnings} • Live on ${android.os.Build.MODEL}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            "${liveMatch.totalRuns}/${liveMatch.totalWickets}",
                                            style = MaterialTheme.typography.headlineLarge,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            "(${liveMatch.totalBalls / 6}.${liveMatch.totalBalls % 6} Ov)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(20.dp))
                                
                                Button(
                                    onClick = { onNavigateToLiveScoring(liveMatch) },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("CONTINUE SCORING", fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        "QUICK START",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .clickable { onNavigateToDashboard() },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text("Create Series", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Setup teams and manage multiple matches with full leaderboards.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onNavigateToDashboard,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("GET STARTED", fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "EXPLORE",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item { QuickActionChip("SERIES", onNavigateToDashboard) }
                        item { QuickActionChip("TEAMS", onNavigateToDashboard) }
                        item { QuickActionChip("PLAYERS", onNavigateToDashboard) }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(48.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("CricScore Pro v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text("Prepared by Ankoji", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (showSettings) {
                AlertDialog(
                    onDismissRequest = { showSettings = false },
                    title = { Text("Settings", fontWeight = FontWeight.Black) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Dark Mode", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    Text("Switch to midnight theme", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                                val isDarkMode by viewModel.isDarkMode.collectAsState()
                                Switch(
                                    checked = isDarkMode ?: androidx.compose.foundation.isSystemInDarkTheme(),
                                    onCheckedChange = { viewModel.toggleTheme(it) }
                                )
                            }
                            
                            HorizontalDivider()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Local Sync (Beta)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    Text("Share scores with devices nearby", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                                val isSyncEnabled by viewModel.isSyncEnabled.collectAsState()
                                Switch(
                                    checked = isSyncEnabled,
                                    onCheckedChange = { enabled ->
                                        viewModel.toggleSync(enabled)
                                        if (enabled) {
                                            NearbyManager.startDiscovering(context)
                                            if (liveMatch != null) {
                                                NearbyManager.startBroadcasting(context, liveMatch.teamA.name + " vs " + liveMatch.teamB.name)
                                            }
                                        } else {
                                            NearbyManager.stopAll(context)
                                        }
                                    }
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSettings = false }) {
                            Text("DONE", fontWeight = FontWeight.Black)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun QuickActionChip(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.LightGray)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
