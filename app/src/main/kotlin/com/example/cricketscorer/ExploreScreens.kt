package com.example.cricketscorer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonSearch
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
    val globalPlayers by GlobalPlayerRepository.players.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var playerName by remember { mutableStateOf("") }
    var battingStyle by remember { mutableStateOf(BattingStyle.RHB) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Global Playlist", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Add Global Player", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }, containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFFF5F7FA)), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Saved players that can be reused across any series or team. 🌎🏏", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
            }
            if (globalPlayers.isEmpty()) {
                item { Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { Text("Your global playlist is empty. Add players to reuse them!", color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(32.dp)) } }
            } else {
                items(globalPlayers) { player ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(modifier = Modifier.size(32.dp), shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                                Box(contentAlignment = Alignment.Center) { Text(player.name.take(1).uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black) }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(player.name, fontWeight = FontWeight.Bold)
                                Text("Batting: ${player.battingStyle ?: "RHB"}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                            Row {
                                var showEditPlayerDialog by remember { mutableStateOf<Player?>(null) }
                                IconButton(onClick = { showEditPlayerDialog = player }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray, modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { GlobalPlayerRepository.removePlayer(player.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                                }

                                if (showEditPlayerDialog != null) {
                                    val p = showEditPlayerDialog!!
                                    var editedName by remember(p.id) { mutableStateOf(p.name) }
                                    var editedStyle by remember(p.id) { mutableStateOf(p.battingStyle ?: BattingStyle.RHB) }

                                    AlertDialog(
                                        onDismissRequest = { showEditPlayerDialog = null },
                                        title = { Text("Edit Player") },
                                        text = {
                                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                                OutlinedTextField(
                                                    value = editedName,
                                                    onValueChange = { editedName = it },
                                                    label = { Text("Name") },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                Text("Batting Style", fontWeight = FontWeight.Bold)
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    BattingStyle.entries.forEach { style ->
                                                        FilterChip(
                                                            selected = editedStyle == style,
                                                            onClick = { editedStyle = style },
                                                            label = { Text(style.name) },
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        confirmButton = {
                                            Button(onClick = {
                                                if (editedName.isNotBlank()) {
                                                    GlobalPlayerRepository.updatePlayer(p.id, editedName, editedStyle)
                                                    showEditPlayerDialog = null
                                                }
                                            }) { Text("UPDATE") }
                                        },
                                        dismissButton = { TextButton(onClick = { showEditPlayerDialog = null }) { Text("CANCEL") } }
                                    )
                                }
                            }
                        }
                    }
                }
                item { CardBranding() }
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Add Global Player") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = playerName,
                            onValueChange = { playerName = it },
                            label = { Text("Player Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Batting Style", fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BattingStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = battingStyle == style,
                                    onClick = { battingStyle = style },
                                    label = { Text(style.name) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (playerName.isNotBlank()) {
                            GlobalPlayerRepository.addPlayer(playerName, battingStyle)
                            playerName = ""
                            showAddDialog = false
                        }
                    }) { Text("SAVE TO PLAYLIST") }
                },
                dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("CANCEL") } }
            )
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
