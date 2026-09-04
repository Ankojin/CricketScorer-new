package com.example.cricketscorer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cricketscorer.ui.CardBranding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllTeamsScreen(onBack: () -> Unit) {
    val tournaments by TournamentRepository.tournaments.collectAsState()
    val allTeams = tournaments.flatMap { it.teams }.distinctBy { it.id }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Teams", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFFF5F7FA)), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (allTeams.isEmpty()) {
                item { Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { Text("No teams found. Create a series first.", color = Color.Gray) } }
            } else {
                items(allTeams) { team ->
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(modifier = Modifier.size(40.dp), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Box(contentAlignment = Alignment.Center) { Text(team.name.take(1).uppercase(), fontWeight = FontWeight.Bold) }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(team.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("${team.players.size} Players", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                    }
                }
                item { CardBranding() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllPlayersScreen(onBack: () -> Unit) {
    val tournaments by TournamentRepository.tournaments.collectAsState()
    val allPlayers = tournaments.flatMap { t -> t.teams.flatMap { it.players } }.distinctBy { it.id }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Players", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFFF5F7FA)), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (allPlayers.isEmpty()) {
                item { Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { Text("No players found.", color = Color.Gray) } }
            } else {
                items(allPlayers) { player ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(player.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(player.battingStyle?.name ?: "RHB", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    }
                }
                item { CardBranding() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllMatchesScreen(
    onBack: () -> Unit,
    onMatchClick: (Match) -> Unit
) {
    val tournaments by TournamentRepository.tournaments.collectAsState()
    val allMatches = tournaments.flatMap { it.matches }.sortedByDescending { it.dateMillis }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Matches", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFFF5F7FA)), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (allMatches.isEmpty()) {
                item { Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { Text("No matches scheduled.", color = Color.Gray) } }
            } else {
                items(allMatches) { match ->
                    val tournament = tournaments.find { it.id == match.tournamentId }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onMatchClick(match) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("${tournament?.name ?: "Series"}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text("${match.teamA.name} vs ${match.teamB.name}", fontWeight = FontWeight.Bold)
                            Text("Score: ${match.totalRuns}/${match.totalWickets} (${match.totalBalls/6}.${match.totalBalls%6} Ov)", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item { CardBranding() }
            }
        }
    }
}
