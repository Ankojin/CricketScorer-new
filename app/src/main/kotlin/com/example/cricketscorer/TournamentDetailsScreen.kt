package com.example.cricketscorer

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import com.example.cricketscorer.ui.CaptureArea

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentDetailsScreen(
    tournamentId: String,
    viewModel: TournamentViewModel,
    scoringViewModel: ScoringViewModel,
    onBack: () -> Unit,
    onStartMatch: (Match) -> Unit
) {
    val context = LocalContext.current
    val tournaments by viewModel.tournaments.collectAsState()
    val tournament = tournaments.find { it.id == tournamentId }

    val isSyncEnabled by scoringViewModel.isSyncEnabled.collectAsState()
    val connectedEndpoints by NearbyManager.connectedEndpoints.collectAsState()
    val hasConnections = connectedEndpoints.isNotEmpty()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val teamsGraphicsLayer = rememberGraphicsLayer()
    val statsGraphicsLayer = rememberGraphicsLayer()

    var showAddTeamDialog by remember { mutableStateOf(false) }
    var teamNameToAdd by remember { mutableStateOf("") }
    
    var showScheduleDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(tournament?.name ?: "Series Details", fontWeight = FontWeight.Black) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (isSyncEnabled) {
                            TextButton(
                                onClick = {
                                    if (hasConnections) {
                                        viewModel.syncTournament(context, tournamentId)
                                    } else {
                                        Toast.makeText(context, "No devices connected. Check sync settings.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Text(
                                    "SYNC SERIES",
                                    color = if (hasConnections) Color.White else Color.White.copy(alpha = 0.5f),
                                    fontWeight = FontWeight.Black
                                )
                            }
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
                    0 -> TeamsTab(tournament, tournamentId, viewModel, teamsGraphicsLayer)
                    1 -> MatchesTab(tournament, tournamentId, viewModel, onStartMatch)
                    2 -> PointsTableTab(tournament)
                    3 -> TournamentStatsTab(tournament, statsGraphicsLayer)
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
                onSchedule = { teamA, teamB, date, overs, maxOvers, qCount, qLimit ->
                    viewModel.scheduleMatch(tournamentId, teamA, teamB, date, overs, maxOvers, qCount, qLimit)
                    showScheduleDialog = false
                }
            )
        }
    }
}

@Composable
fun TeamsTab(tournament: Tournament, tournamentId: String, viewModel: TournamentViewModel, graphicsLayer: GraphicsLayer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var shareTrigger by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Button(
            onClick = {
                scope.launch {
                    shareTrigger++
                    kotlinx.coroutines.delay(300.milliseconds)
                    shareComposableScreenshot(context, graphicsLayer, "full_teams_squad")
                }
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF003366), contentColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("SHARE FULL TEAMS SQUAD", fontWeight = FontWeight.Black)
        }

        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .background(Color.White)
                .drawWithContent {
                    this@drawWithContent.drawContent()
                    if (shareTrigger > 0) {
                        graphicsLayer.record {
                            drawRect(Color.White)
                            this@drawWithContent.drawContent()
                        }
                    }
                    drawLayer(graphicsLayer)
                }
        ) {
            CaptureArea {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    tournament.teams.forEach { team ->
                        TeamCard(
                            tournamentId = tournamentId,
                            team = team,
                            viewModel = viewModel,
                            onDeleteTeam = { viewModel.deleteTeam(tournamentId, team.id) }
                        )
                    }
                    CardBranding()
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()
    var shareTrigger by remember { mutableIntStateOf(0) }
    
    val sortedTeams = tournament.teams.sortedWith(
        compareByDescending<Team> { it.points }.thenByDescending { it.nrr }
    )
    
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Button(
            onClick = {
                scope.launch {
                    shareTrigger++
                    kotlinx.coroutines.delay(300.milliseconds)
                    shareComposableScreenshot(context, graphicsLayer, "points_table")
                }
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF003366), contentColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("SHARE FULL POINTS TABLE", fontWeight = FontWeight.Black)
        }

        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .background(Color.White)
                .drawWithContent {
                    this@drawWithContent.drawContent()
                    if (shareTrigger > 0) {
                        graphicsLayer.record {
                            drawRect(Color.White)
                            this@drawWithContent.drawContent()
                        }
                    }
                    drawLayer(graphicsLayer)
                }
        ) {
            CaptureArea {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().background(Color(0xFFF5F5F5)).padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("STANDINGS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = Color.Gray, modifier = Modifier.weight(1f))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().background(Color(0xFFF8F9FA)).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("TEAM", modifier = Modifier.weight(3f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text("P", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Text("W", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Text("L", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Text("PTS", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Text("NRR", modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                        }
                        sortedTeams.forEachIndexed { index, team ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${index + 1}. ${team.name}", modifier = Modifier.weight(3f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(team.matchesPlayed.toString(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                                Text(team.wins.toString(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                                Text(team.losses.toString(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                                Text(team.points.toString(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                                Text(String.format(Locale.getDefault(), "%.3f", team.nrr), modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                            }
                            if (index < sortedTeams.size - 1) HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
                        }
                        CardBranding()
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun TournamentStatsTab(tournament: Tournament, graphicsLayer: GraphicsLayer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var shareTrigger by remember { mutableIntStateOf(0) }

    val allPlayersWithTeam = tournament.teams.flatMap { team ->
        team.players.map { player -> player to team }
    }
    
    val topBatters = allPlayersWithTeam
        .filter { it.first.battingStats.balls > 0 }
        .sortedByDescending { it.first.battingStats.runs }
        .take(5)

    val topBowlers = allPlayersWithTeam
        .filter { it.first.bowlingStats.balls > 0 || it.first.bowlingStats.overs > 0 }
        .sortedWith(
            compareByDescending<Pair<Player, Team>> { it.first.bowlingStats.wickets }
                .thenBy { it.first.bowlingStats.economy }
        )
        .take(5)

    val topSixes = allPlayersWithTeam
        .filter { it.first.battingStats.sixes > 0 }
        .sortedByDescending { it.first.battingStats.sixes }
        .take(3)
        
    val topFours = allPlayersWithTeam
        .filter { it.first.battingStats.fours > 0 }
        .sortedByDescending { it.first.battingStats.fours }
        .take(3)

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Button(
            onClick = {
                scope.launch {
                    shareTrigger++
                    kotlinx.coroutines.delay(300.milliseconds)
                    shareComposableScreenshot(context, graphicsLayer, "full_series_leaders")
                }
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF003366), contentColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("SHARE FULL SERIES LEADERS", fontWeight = FontWeight.Black)
        }

        Surface(
            color = Color.White,
            contentColor = Color.Black,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .drawWithContent {
                    this@drawWithContent.drawContent()
                    if (shareTrigger > 0) {
                        graphicsLayer.record {
                            drawRect(Color.White)
                            this@drawWithContent.drawContent()
                        }
                    }
                    drawLayer(graphicsLayer)
                }
        ) {
            CaptureArea {
                Column(
                    modifier = Modifier.fillMaxWidth().background(Color.White).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Text(
                        "SERIES LEADERBOARD", 
                        style = MaterialTheme.typography.headlineSmall, 
                        fontWeight = FontWeight.Black, 
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )

                    LeaderboardCard(
                        title = "MOST RUNS 🏏",
                        headers = listOf("PLAYER", "TEAM", "R", "SR", "4s", "6s"),
                        items = topBatters
                    ) { item ->
                        val player = item.first
                        val team = item.second
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(player.name + " (${(player.battingStyle ?: BattingStyle.RHB).name})", modifier = Modifier.weight(3f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(getTeamAbbr(team.name), modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall, color = Color.Gray, textAlign = TextAlign.Center)
                            Text("${player.battingStats.runs}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Black, textAlign = TextAlign.End)
                            Text(String.format(Locale.getDefault(), "%.0f", player.battingStats.strikeRate), modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                            Text("${player.battingStats.fours}", modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                            Text("${player.battingStats.sixes}", modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                        }
                    }

                    LeaderboardCard(
                        title = "MOST WICKETS ⚾",
                        headers = listOf("PLAYER", "TEAM", "W", "ECO", "RUNS"),
                        items = topBowlers
                    ) { item ->
                        val player = item.first
                        val team = item.second
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            val roleSuffix = if (player.isCaptain) " (c)" else if (player.isViceCaptain) " (vc)" else ""
                            val bowlStyle = (player.bowlingStyle ?: BowlingStyle.RFM).name
                            Text(player.name + roleSuffix + " ($bowlStyle)", modifier = Modifier.weight(3f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(getTeamAbbr(team.name), modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall, color = Color.Gray, textAlign = TextAlign.Center)
                            Text("${player.bowlingStats.wickets}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Black, color = Color(0xFFD32F2F), textAlign = TextAlign.End)
                            Text(String.format(Locale.getDefault(), "%.2f", player.bowlingStats.economy), modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                            Text("${player.bowlingStats.runsConceded}", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                        }
                    }

                    Column {
                        Text("BOUNDARY KINGS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BoundaryBox("MOST 6s", topSixes, modifier = Modifier.weight(1f)) { it.first.battingStats.sixes }
                            BoundaryBox("MOST 4s", topFours, modifier = Modifier.weight(1f)) { it.first.battingStats.fours }
                        }
                    }

                    Text("TEAM SUMMARIES", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)

                    tournament.teams.forEach { team ->
                        val teamMatches = tournament.matches.filter { 
                            it.status == MatchStatus.COMPLETED && (it.teamA.id == team.id || it.teamB.id == team.id) 
                        }
                        val matchesPlayed = teamMatches.size
                        var totalTeamRuns = 0
                        var highScore = 0

                        teamMatches.forEach { match ->
                            val runsInMatch = if (match.initialBattingTeamId == team.id) {
                                match.innings1Data?.runs ?: 0
                            } else {
                                match.totalRuns
                            }
                            totalTeamRuns += runsInMatch
                            if (runsInMatch > highScore) highScore = runsInMatch
                        }

                        TeamSummaryCard(
                            team = team,
                            totalTeamRuns = totalTeamRuns,
                            matchesPlayed = matchesPlayed,
                            highScore = highScore
                        )
                    }
                    
                    CardBranding()
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun CardBranding() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text("Prepared by Ankoji | v1.98 🏏🚀⚖️🏅", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun <T> LeaderboardCard(title: String, headers: List<String>, items: List<T>, content: @Composable (T) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFF8F9FA), RoundedCornerShape(4.dp)).padding(8.dp)) {
                headers.forEachIndexed { index, header ->
                    val colWeight = when (index) {
                        0 -> 3f
                        1 -> 1.5f
                        else -> 1f + (if (header == "ECO" || header == "SR") 0.2f else if (header == "PLAYER") 0f else -0.2f)
                    }
                    Text(
                        header, 
                        modifier = Modifier.weight(colWeight), 
                        style = MaterialTheme.typography.labelSmall, 
                        fontWeight = FontWeight.Bold, 
                        color = Color.Gray,
                        textAlign = if (index == 0) TextAlign.Start else if (index == 1) TextAlign.Center else TextAlign.End
                    )
                }
            }
            items.forEachIndexed { index, item ->
                content(item)
                if (index < items.size - 1) HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
            }
        }
    }
}

@Composable
fun BoundaryBox(title: String, players: List<Pair<Player, Team>>, modifier: Modifier = Modifier, statSelector: (Pair<Player, Team>) -> Int) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            players.forEach { p ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(p.first.name + " (${(p.first.battingStyle ?: BattingStyle.RHB).name})", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                    Text("${statSelector(p)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary)
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

fun getTeamAbbr(name: String): String {
    val words = name.split(" ").filter { it.isNotBlank() }
    return if (words.size >= 2) {
        words.mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
    } else {
        name.take(3).uppercase()
    }
}

@Composable
fun TeamSummaryCard(team: Team, totalTeamRuns: Int, matchesPlayed: Int, highScore: Int) {
    val totalSixes = team.players.sumOf { it.battingStats.sixes }
    val totalFours = team.players.sumOf { it.battingStats.fours }
    val totalWickets = team.players.sumOf { it.bowlingStats.wickets }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(team.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem("MATCHES", "$matchesPlayed")
                StatItem("RUNS", "$totalTeamRuns")
                StatItem("6s", "$totalSixes")
                StatItem("4s", "$totalFours")
                StatItem("WKTS", "$totalWickets")
                StatItem("HIGH SCORE", "$highScore")
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
    val dateFormat = remember { java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    val dateText = remember(match.dateMillis) { dateFormat.format(java.util.Date(match.dateMillis)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onStartMatch(match) },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (match.status == MatchStatus.COMPLETED) "COMPLETED" else if (match.status == MatchStatus.LIVE) "LIVE" else "UPCOMING",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (match.status == MatchStatus.LIVE) Color.Red else Color.Gray
                    )
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray
                    )
                }
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
    onSchedule: (String, String, Long?, Int?, Int?, Int?, Int?) -> Unit
) {
    var selectedTeamA by remember { mutableStateOf(tournament.teams[0].id) }
    var selectedTeamB by remember { mutableStateOf(tournament.teams[1].id) }
    var selectedDateMillis by remember { mutableStateOf<Long?>(null) }
    
    var oversText by remember { mutableStateOf(tournament.settings.overs.toString()) }
    var maxOversText by remember { mutableStateOf(tournament.settings.maxOversPerBowler?.toString() ?: "") }
    var qCountText by remember { mutableStateOf(tournament.settings.quotaBowlersCount?.toString() ?: "") }
    var qLimitText by remember { mutableStateOf(tournament.settings.quotaMaxOvers?.toString() ?: "") }

    val context = LocalContext.current
    val calendar = remember { java.util.Calendar.getInstance() }
    val dateFormat = remember { java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule Match", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Select Team A", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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
                Text("Select Team B", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                tournament.teams.forEach { team ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { selectedTeamB = team.id }
                    ) {
                        RadioButton(selected = selectedTeamB == team.id, onClick = { selectedTeamB = team.id })
                        Text(team.name)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Match Time", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedCard(
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                calendar.set(java.util.Calendar.YEAR, year)
                                calendar.set(java.util.Calendar.MONTH, month)
                                calendar.set(java.util.Calendar.DAY_OF_MONTH, dayOfMonth)
                                
                                TimePickerDialog(
                                    context,
                                    { _, hourOfDay, minute ->
                                        calendar.set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                                        calendar.set(java.util.Calendar.MINUTE, minute)
                                        selectedDateMillis = calendar.timeInMillis
                                    },
                                    calendar.get(java.util.Calendar.HOUR_OF_DAY),
                                    calendar.get(java.util.Calendar.MINUTE),
                                    false
                                ).show()
                            },
                            calendar.get(java.util.Calendar.YEAR),
                            calendar.get(java.util.Calendar.MONTH),
                            calendar.get(java.util.Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = selectedDateMillis?.let { dateFormat.format(java.util.Date(it)) } ?: "Now",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Match Rules", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = oversText,
                    onValueChange = { if (it.all { char -> char.isDigit() }) oversText = it },
                    label = { Text("Match Overs") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = maxOversText,
                    onValueChange = { if (it.all { char -> char.isDigit() }) maxOversText = it },
                    label = { Text("Base Bowler Limit (e.g. 2)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qCountText,
                        onValueChange = { if (it.all { char -> char.isDigit() }) qCountText = it },
                        label = { Text("Number of Bowlers") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("e.g. 3") }
                    )
                    OutlinedTextField(
                        value = qLimitText,
                        onValueChange = { if (it.all { char -> char.isDigit() }) qLimitText = it },
                        label = { Text("Per Bowler Limit") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("e.g. 3") }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { 
                onSchedule(
                    selectedTeamA, 
                    selectedTeamB, 
                    selectedDateMillis,
                    oversText.toIntOrNull(),
                    maxOversText.toIntOrNull(),
                    qCountText.toIntOrNull(),
                    qLimitText.toIntOrNull()
                ) 
            }) {
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
    
    var showEditPlayerDialog by remember { mutableStateOf(false) }
    var playerToEdit by remember { mutableStateOf<Player?>(null) }
    var editedPlayerName by remember { mutableStateOf("") }
    var editedPlayerBattingStyle by remember { mutableStateOf(BattingStyle.RHB) }
    var editedPlayerBowlingStyle by remember { mutableStateOf(BowlingStyle.RFM) }
    var editedIsCaptain by remember { mutableStateOf(false) }
    var editedIsViceCaptain by remember { mutableStateOf(false) }
    var editedCanBowl by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
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
                                    player = player,
                                    onEdit = {
                                        playerToEdit = player
                                        editedPlayerName = player.name
                                        editedPlayerBattingStyle = player.battingStyle ?: BattingStyle.RHB
                                        editedPlayerBowlingStyle = player.bowlingStyle ?: BowlingStyle.RFM
                                        editedIsCaptain = player.isCaptain
                                        editedIsViceCaptain = player.isViceCaptain
                                        editedCanBowl = player.canBowl
                                        showEditPlayerDialog = true
                                    },
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
            var selectedBattingStyle by remember { mutableStateOf(BattingStyle.RHB) }
            var selectedBowlingStyle by remember { mutableStateOf(BowlingStyle.RFM) }
            var isCaptain by remember { mutableStateOf(false) }
            var isViceCaptain by remember { mutableStateOf(false) }
            var canBowl by remember { mutableStateOf(true) }
            AlertDialog(
                onDismissRequest = { showAddPlayerDialog = false },
                title = { Text("Register Player", fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        OutlinedTextField(
                            value = playerNameToAdd,
                            onValueChange = { playerNameToAdd = it },
                            label = { Text("Full Name") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Batting Style", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BattingStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = selectedBattingStyle == style,
                                    onClick = { selectedBattingStyle = style },
                                    label = { Text(style.name) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Bowling Style", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BowlingStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = selectedBowlingStyle == style,
                                    onClick = { selectedBowlingStyle = style },
                                    label = { Text(style.name) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Switch(checked = isCaptain, onCheckedChange = { isCaptain = it; if (it) isViceCaptain = false })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Capt (C)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Switch(checked = isViceCaptain, onCheckedChange = { isViceCaptain = it; if (it) isCaptain = false })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("VC", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Switch(checked = canBowl, onCheckedChange = { canBowl = it })
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Can Bowl? ⚾", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                confirmButton = {
                    val context = LocalContext.current
                    Button(
                        onClick = {
                            if (playerNameToAdd.isNotBlank()) {
                                viewModel.addPlayer(context, tournamentId, team.id, playerNameToAdd, selectedBattingStyle, selectedBowlingStyle, isCaptain, isViceCaptain, canBowl)
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

        if (showEditPlayerDialog && playerToEdit != null) {
            val currentPlayerState = team.players.find { it.id == playerToEdit!!.id } ?: playerToEdit!!
            
            AlertDialog(
                onDismissRequest = { showEditPlayerDialog = false },
                title = { Text("Edit Player", fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        OutlinedTextField(
                            value = editedPlayerName,
                            onValueChange = { editedPlayerName = it },
                            label = { Text("Full Name") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Batting Style", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BattingStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = editedPlayerBattingStyle == style,
                                    onClick = { editedPlayerBattingStyle = style },
                                    label = { Text(style.name) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Bowling Style", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BowlingStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = editedPlayerBowlingStyle == style,
                                    onClick = { editedPlayerBowlingStyle = style },
                                    label = { Text(style.name) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Switch(checked = editedIsCaptain, onCheckedChange = { editedIsCaptain = it; if (it) editedIsViceCaptain = false })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Capt (C)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Switch(checked = editedIsViceCaptain, onCheckedChange = { editedIsViceCaptain = it; if (it) editedIsCaptain = false })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("VC", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Switch(checked = editedCanBowl, onCheckedChange = { editedCanBowl = it })
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Can Bowl? ⚾", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.togglePlayerJokerStatus(tournamentId, team.id, currentPlayerState.id)
                            }
                        ) {
                            Switch(
                                checked = currentPlayerState.isJoker,
                                onCheckedChange = { viewModel.togglePlayerJokerStatus(tournamentId, team.id, currentPlayerState.id) }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Two-Side Player (Joker) 🃏", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (editedPlayerName.isNotBlank()) {
                                viewModel.updatePlayerDetails(
                                    tournamentId,
                                    team.id,
                                    currentPlayerState.id,
                                    editedPlayerName,
                                    editedPlayerBattingStyle,
                                    editedPlayerBowlingStyle,
                                    editedIsCaptain,
                                    editedIsViceCaptain,
                                    editedCanBowl
                                )
                                showEditPlayerDialog = false
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Update")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditPlayerDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun PlayerChip(player: Player, onEdit: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val bStyle = (player.battingStyle ?: BattingStyle.RHB).name
    val bowlStyle = (player.bowlingStyle ?: BowlingStyle.RFM).name
    val roleSuffix = if (player.isCaptain) " (c)" else if (player.isViceCaptain) " (vc)" else ""

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (player.isJoker) Color(0xFFFFE082) else Color(0xFFF0F2F5),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Icon(
                if (player.isJoker) Icons.Default.Star else Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (player.isJoker) Color(0xFFF57F17) else Color.Gray
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = (if (player.isJoker) "${player.name} 🃏" else player.name) + "$roleSuffix ($bStyle, $bowlStyle)",
                modifier = Modifier
                    .weight(1f)
                    .clickable { onEdit() },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (player.isJoker || player.isCaptain) FontWeight.Black else FontWeight.Medium,
                maxLines = 1
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(16.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.Gray, modifier = Modifier.size(12.dp))
            }
        }
    }
}
