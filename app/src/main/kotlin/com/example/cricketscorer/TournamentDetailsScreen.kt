package com.example.cricketscorer

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentDetailsScreen(
    tournamentId: String,
    viewModel: TournamentViewModel,
    onBack: () -> Unit,
    onStartMatch: (Match) -> Unit
) {
    val context = LocalContext.current
    val tournaments by viewModel.tournaments.collectAsState()
    val tournament = tournaments.find { it.id == tournamentId }

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    var showAddTeamDialog by remember { mutableStateOf(false) }
    var teamNameToAdd by remember { mutableStateOf("") }
    
    var showScheduleDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(tournament?.name ?: "Series Details", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            val json = viewModel.exportTournament(tournamentId)
                            if (json != null) {
                                val sendIntent: Intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, json)
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Export Series Data")
                                context.startActivity(shareIntent)
                            }
                        }) {
                            Text("EXPORT", color = Color.White, fontWeight = FontWeight.Black)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = Color.White
                        )
                    }
                ) {
                    Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }) {
                        Text("TEAMS", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
                    }
                    Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }) {
                        Text("MATCHES", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
                    }
                    Tab(selected = selectedTabIndex == 2, onClick = { selectedTabIndex = 2 }) {
                        Text("TABLE", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
                    }
                    Tab(selected = selectedTabIndex == 3, onClick = { selectedTabIndex = 3 }) {
                        Text("STATS", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        floatingActionButton = {
            if (selectedTabIndex == 0) {
                FloatingActionButton(
                    onClick = { showAddTeamDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Team")
                }
            } else if (selectedTabIndex == 1 && (tournament?.teams?.size ?: 0) >= 2) {
                FloatingActionButton(
                    onClick = { showScheduleDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Start Match")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF5F7FA))
        ) {
            if (tournament == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Tournament not found")
                }
            } else {
                when (selectedTabIndex) {
                    0 -> TeamsTab(tournament, tournamentId, viewModel)
                    1 -> MatchesTab(tournament, tournamentId, viewModel, onStartMatch)
                    2 -> PointsTableTab(tournament)
                    3 -> TournamentStatsTab(tournament)
                }
            }
        }

        if (showAddTeamDialog) {
            AlertDialog(
                onDismissRequest = { showAddTeamDialog = false },
                title = { Text("Create New Team", fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = teamNameToAdd,
                        onValueChange = { teamNameToAdd = it },
                        label = { Text("Team Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (teamNameToAdd.isNotBlank()) {
                                viewModel.addTeam(tournamentId, teamNameToAdd)
                                teamNameToAdd = ""
                                showAddTeamDialog = false
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddTeamDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showScheduleDialog && tournament != null && tournament.teams.size >= 2) {
            ScheduleMatchDialog(
                tournament = tournament,
                onDismiss = { showScheduleDialog = false },
                onSchedule = { teamAId, teamBId ->
                    viewModel.scheduleMatch(tournament.id, teamAId, teamBId)
                    showScheduleDialog = false
                }
            )
        }
    }
}

@Composable
fun TeamsTab(tournament: Tournament, tournamentId: String, viewModel: TournamentViewModel) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(tournament.teams) { team ->
            TeamCard(
                tournamentId = tournamentId,
                team = team,
                viewModel = viewModel,
                onDeleteTeam = { viewModel.deleteTeam(tournamentId, team.id) }
            )
        }
    }
}

@Composable
fun MatchesTab(tournament: Tournament, tournamentId: String, viewModel: TournamentViewModel, onStartMatch: (Match) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val matches = tournament.matches
        if (matches.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No matches scheduled", color = Color.Gray)
                }
            }
        } else {
            items(matches) { match ->
                MatchRow(match, onStartMatch, onDelete = { viewModel.deleteMatch(tournamentId, match.id) })
            }
        }
    }
}

@Composable
fun PointsTableTab(tournament: Tournament) {
    val sortedTeams = tournament.teams.sortedWith(
        compareByDescending<Team> { it.points }.thenByDescending { it.nrr }
    )
    
    Card(
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFFF5F5F5)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("TEAM", modifier = Modifier.weight(3f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("P", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("W", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("L", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("PTS", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("NRR", modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            sortedTeams.forEachIndexed { index, team ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${index + 1}. ${team.name}", modifier = Modifier.weight(3f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Text("${team.matchesPlayed}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text("${team.wins}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text("${team.losses}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text("${team.points}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Black)
                    Text(String.format(Locale.getDefault(), "%.3f", team.nrr), modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall)
                }
                if (index < sortedTeams.size - 1) HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
            }
        }
    }
}

@Composable
fun TournamentStatsTab(tournament: Tournament) {
    val allPlayers = tournament.teams.flatMap { it.players }
    val topBatters = allPlayers.sortedByDescending { it.battingStats.runs }.take(5)
    val topBowlers = allPlayers.sortedWith(
        compareByDescending<Player> { it.bowlingStats.wickets }.thenBy { it.bowlingStats.economy }
    ).take(5)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text("SERIES LEADERS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        }

        item {
            StatSection("MOST RUNS", topBatters) { player ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(player.name, fontWeight = FontWeight.Bold)
                    Text("${player.battingStats.runs} runs", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Black)
                }
            }
        }

        item {
            StatSection("MOST WICKETS", topBowlers) { player ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(player.name, fontWeight = FontWeight.Bold)
                    Text("${player.bowlingStats.wickets} wkts", color = Color(0xFFD32F2F), fontWeight = FontWeight.Black)
                }
            }
        }

        item {
            Text("TEAM SUMMARIES", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
        }

        items(tournament.teams) { team ->
            TeamSummaryCard(team)
        }
    }
}

@Composable
fun <T> StatSection(title: String, items: List<T>, content: @Composable (T) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.Gray)
            Spacer(modifier = Modifier.height(12.dp))
            items.forEachIndexed { index, item ->
                content(item)
                if (index < items.size - 1) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun TeamSummaryCard(team: Team) {
    val totalSixes = team.players.sumOf { it.battingStats.sixes }
    val totalFours = team.players.sumOf { it.battingStats.fours }
    val totalRuns = team.players.sumOf { it.battingStats.runs }
    val totalWickets = team.players.sumOf { it.bowlingStats.wickets }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(team.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem("RUNS", "$totalRuns")
                StatItem("6s", "$totalSixes")
                StatItem("4s", "$totalFours")
                StatItem("WKTS", "$totalWickets")
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchRow(match: Match, onStartMatch: (Match) -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onStartMatch(match) },
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (match.status == MatchStatus.COMPLETED) "COMPLETED" else if (match.status == MatchStatus.LIVE) "LIVE" else "UPCOMING",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (match.status == MatchStatus.LIVE) Color.Red else Color.Gray,
                    modifier = Modifier.weight(1f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (match.status != MatchStatus.COMPLETED) {
                        IconButton(onClick = { onStartMatch(match) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(match.teamA.name, fontWeight = FontWeight.Bold)
                if (match.status != MatchStatus.UPCOMING) {
                    val score = when {
                        match.innings1Data?.teamId == match.teamA.id -> "${match.innings1Data.runs}/${match.innings1Data.wickets}"
                        match.battingTeamId == match.teamA.id -> "${match.totalRuns}/${match.totalWickets}"
                        match.currentInnings == 2 && match.innings1Data?.teamId == match.teamB.id -> "${match.totalRuns}/${match.totalWickets}"
                        else -> "0/0"
                    }
                    Text(score, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(match.teamB.name, fontWeight = FontWeight.Bold)
                if (match.status != MatchStatus.UPCOMING) {
                    val score = when {
                        match.innings1Data?.teamId == match.teamB.id -> "${match.innings1Data.runs}/${match.innings1Data.wickets}"
                        match.battingTeamId == match.teamB.id -> "${match.totalRuns}/${match.totalWickets}"
                        match.currentInnings == 2 && match.innings1Data?.teamId == match.teamA.id -> "${match.totalRuns}/${match.totalWickets}"
                        else -> "0/0"
                    }
                    Text(score, fontWeight = FontWeight.Bold)
                }
            }
            if (match.status == MatchStatus.COMPLETED) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))
                val winnerTeam = if (match.winnerId == match.teamA.id) match.teamA else if (match.winnerId == match.teamB.id) match.teamB else null
                Text(
                    text = if (winnerTeam != null) "${winnerTeam.name} won" else "Match Drawn",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ScheduleMatchDialog(
    tournament: Tournament,
    onDismiss: () -> Unit,
    onSchedule: (String, String) -> Unit
) {
    var selectedTeamA by remember { mutableStateOf(tournament.teams[0].id) }
    var selectedTeamB by remember { mutableStateOf(tournament.teams[1].id) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule Match") },
        text = {
            Column {
                Text("Select Team A")
                Spacer(modifier = Modifier.height(8.dp))
                tournament.teams.forEach { team ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { selectedTeamA = team.id }
                    ) {
                        RadioButton(selected = selectedTeamA == team.id, onClick = { selectedTeamA = team.id })
                        Text(team.name)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Select Team B")
                tournament.teams.forEach { team ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { selectedTeamB = team.id }
                    ) {
                        RadioButton(selected = selectedTeamB == team.id, onClick = { selectedTeamB = team.id })
                        Text(team.name)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSchedule(selectedTeamA, selectedTeamB) }) {
                Text("Schedule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun TeamCard(
    tournamentId: String,
    team: Team,
    viewModel: TournamentViewModel,
    onDeleteTeam: () -> Unit
) {
    var showAddPlayerDialog by remember { mutableStateOf(false) }
    var playerNameToAdd by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = team.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { showAddPlayerDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                        Text("PLAYER", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDeleteTeam) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                "SQUAD • ${team.players.size} PLAYERS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (team.players.isEmpty()) {
                Text(
                    "No players registered",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    team.players.chunked(2).forEach { playerPair ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            playerPair.forEach { player ->
                                PlayerChip(
                                    name = player.name,
                                    onDelete = { viewModel.deletePlayer(tournamentId, team.id, player.id) },
                                    modifier = Modifier.weight(1f).padding(4.dp)
                                )
                            }
                            if (playerPair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f).padding(4.dp))
                            }
                        }
                    }
                }
            }
        }

        if (showAddPlayerDialog) {
            AlertDialog(
                onDismissRequest = { showAddPlayerDialog = false },
                title = { Text("Register Player", fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = playerNameToAdd,
                        onValueChange = { playerNameToAdd = it },
                        label = { Text("Full Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (playerNameToAdd.isNotBlank()) {
                                viewModel.addPlayer(tournamentId, team.id, playerNameToAdd)
                                playerNameToAdd = ""
                                showAddPlayerDialog = false
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddPlayerDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun PlayerChip(name: String, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF0F2F5),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = Color.Gray
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(16.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.Gray, modifier = Modifier.size(12.dp))
            }
        }
    }
}
