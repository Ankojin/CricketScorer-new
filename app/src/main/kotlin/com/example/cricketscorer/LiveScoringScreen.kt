package com.example.cricketscorer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScoringScreen(
    viewModel: ScoringViewModel,
    onNavigateToDashboard: () -> Unit
) {
    val match by viewModel.matchState.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Match Center", fontWeight = FontWeight.Black) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateToDashboard) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.undo() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Undo")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
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
                        Text("LIVE", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
                    }
                    Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }) {
                        Text("SCORECARD", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
                    }
                    Tab(selected = selectedTabIndex == 2, onClick = { selectedTabIndex = 2 }) {
                        Text("OVERS", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { padding ->
        match?.let { m ->
            Box(modifier = Modifier.padding(padding)) {
                when (selectedTabIndex) {
                    0 -> LiveTab(m, viewModel)
                    1 -> ScorecardTab(m)
                    2 -> OversTab(m)
                }

                if (m.pendingAction == PendingAction.TOSS_REQUIRED) {
                    TossOverlay(m, viewModel)
                } else if (m.pendingAction != PendingAction.NONE && m.status != MatchStatus.COMPLETED) {
                    PlayerSelectionOverlay(m, viewModel)
                }
                
                if (m.status == MatchStatus.COMPLETED) {
                    MatchResultOverlay(m, onNavigateToDashboard)
                }
            }
        } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun TossOverlay(match: Match, viewModel: ScoringViewModel) {
    var winnerId by remember { mutableStateOf(match.teamA.id) }
    var decision by remember { mutableStateOf("BAT") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.7f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Card(
                modifier = Modifier.fillMaxWidth(0.9f).padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("MATCH TOSS", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text("Who won the toss?", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { winnerId = match.teamA.id }) {
                        RadioButton(selected = winnerId == match.teamA.id, onClick = { winnerId = match.teamA.id })
                        Text(match.teamA.name, fontWeight = FontWeight.Medium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { winnerId = match.teamB.id }) {
                        RadioButton(selected = winnerId == match.teamB.id, onClick = { winnerId = match.teamB.id })
                        Text(match.teamB.name, fontWeight = FontWeight.Medium)
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Decision?", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { decision = "BAT" }) {
                        RadioButton(selected = decision == "BAT", onClick = { decision = "BAT" })
                        Text("Bat First", fontWeight = FontWeight.Medium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { decision = "BOWL" }) {
                        RadioButton(selected = decision == "BOWL", onClick = { decision = "BOWL" })
                        Text("Bowl First", fontWeight = FontWeight.Medium)
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { viewModel.handleToss(winnerId, decision) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("START MATCH", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun LiveTab(match: Match, viewModel: ScoringViewModel) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F2F5))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            val battingTeam = if (match.battingTeamId == match.teamA.id) match.teamA else match.teamB
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${match.teamA.name} vs ${match.teamB.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (match.tossWinnerId != null) {
                    val tossWinnerName = if (match.tossWinnerId == match.teamA.id) match.teamA.name else match.teamB.name
                    Text(
                        text = "$tossWinnerName won toss & opted to ${match.tossDecision}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = "INNINGS ${match.currentInnings} • ${battingTeam.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Black
                )
            }
        }
        item { ScoreCard(match) }
        item { PlayerStatsSection(match) }
        item { ControlsSection(viewModel) }
    }
}

@Composable
fun ScorecardTab(match: Match) {
    var selectedInnings by remember { mutableIntStateOf(match.currentInnings) }
    val teamA = match.teamA
    val teamB = match.teamB

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F9FA))) {
        ScrollableTabRow(
            selectedTabIndex = selectedInnings - 1,
            containerColor = Color.White,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 16.dp,
            divider = {},
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedInnings - 1]),
                    height = 3.dp
                )
            }
        ) {
            val i1Team = if (match.initialBattingTeamId == teamA.id) teamA else teamB
            Tab(selected = selectedInnings == 1, onClick = { selectedInnings = 1 }) {
                Text("${i1Team.name} Innings", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            if (match.currentInnings == 2 || match.status == MatchStatus.COMPLETED) {
                val i2Team = if (match.initialBattingTeamId == teamA.id) teamB else teamA
                Tab(selected = selectedInnings == 2, onClick = { selectedInnings = 2 }) {
                    Text("${i2Team.name} Innings", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                if (selectedInnings == 1) {
                    val i1Team = if (match.initialBattingTeamId == teamA.id) teamA else teamB
                    InningsScorecard(
                        team = i1Team,
                        strikerId = if (match.currentInnings == 1) match.strikerId else null,
                        nonStrikerId = if (match.currentInnings == 1) match.nonStrikerId else null,
                        wicketHistory = match.innings1Data?.wicketHistory ?: (if (match.currentInnings == 1) match.wicketHistory else emptyList()),
                        wideCount = match.innings1Data?.wideCount ?: (if (match.currentInnings == 1) match.wideCount else 0),
                        noBallCount = match.innings1Data?.noBallCount ?: (if (match.currentInnings == 1) match.noBallCount else 0),
                        byeCount = match.innings1Data?.byeCount ?: (if (match.currentInnings == 1) match.byeCount else 0),
                        legByeCount = match.innings1Data?.legByeCount ?: (if (match.currentInnings == 1) match.legByeCount else 0),
                        totalScore = if (match.innings1Data != null) "${match.innings1Data.runs}/${match.innings1Data.wickets}" else (if (match.currentInnings == 1) "${match.totalRuns}/${match.totalWickets}" else "0/0"),
                        totalOvers = if (match.innings1Data != null) "${match.innings1Data.balls / 6}.${match.innings1Data.balls % 6}" else (if (match.currentInnings == 1) "${match.totalBalls / 6}.${match.totalBalls % 6}" else "0.0"),
                        bowlingTeamPlayers = if (i1Team.id == teamA.id) teamB.players else teamA.players,
                        maxBalls = match.oversPerInnings * 6
                    )
                } else {
                    val i2Team = if (match.initialBattingTeamId == teamA.id) teamB else teamA
                    InningsScorecard(
                        team = i2Team,
                        strikerId = if (match.currentInnings == 2) match.strikerId else null,
                        nonStrikerId = if (match.currentInnings == 2) match.nonStrikerId else null,
                        wicketHistory = if (match.currentInnings == 2) match.wicketHistory else emptyList(),
                        wideCount = if (match.currentInnings == 2) match.wideCount else 0,
                        noBallCount = if (match.currentInnings == 2) match.noBallCount else 0,
                        byeCount = if (match.currentInnings == 2) match.byeCount else 0,
                        legByeCount = if (match.currentInnings == 2) match.legByeCount else 0,
                        totalScore = if (match.currentInnings == 2) "${match.totalRuns}/${match.totalWickets}" else "0/0",
                        totalOvers = if (match.currentInnings == 2) "${match.totalBalls / 6}.${match.totalBalls % 6}" else "0.0",
                        bowlingTeamPlayers = if (i2Team.id == teamA.id) teamB.players else teamA.players,
                        maxBalls = match.oversPerInnings * 6
                    )
                }
            }
        }
    }
}

@Composable
fun InningsScorecard(
    team: Team,
    strikerId: String?,
    nonStrikerId: String?,
    wicketHistory: List<WicketRecord>,
    wideCount: Int,
    noBallCount: Int,
    byeCount: Int,
    legByeCount: Int,
    totalScore: String?,
    totalOvers: String?,
    bowlingTeamPlayers: List<Player>,
    maxBalls: Int
) {
    Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
        Box(
            modifier = Modifier.fillMaxWidth().background(Color(0xFFE3F2FD)).padding(16.dp)
        ) {
            Column {
                Text(text = "${team.name} (Men)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color(0xFF003366))
                Text(text = "($maxBalls balls maximum)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
        
        BattingTable(team.players, strikerId, nonStrikerId, bowlingTeamPlayers)
        
        val totalExtras = wideCount + noBallCount + byeCount + legByeCount
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Extras", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "(b $byeCount, lb $legByeCount, w $wideCount, nb $noBallCount)",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text("$totalExtras", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        }
        HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)

        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFFF8F9FA)).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Total", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            Column(horizontalAlignment = Alignment.End) {
                Text(totalScore ?: "0/0", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                Text("$totalOvers Ov", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
        HorizontalDivider(thickness = 1.dp, color = Color.LightGray)

        val didNotBat = team.players.filter { it.battingStats.balls == 0 && !it.battingStats.isOut && it.id != strikerId && it.id != nonStrikerId }
        if (didNotBat.isNotEmpty()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("DID NOT BAT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.Gray)
                Text(text = didNotBat.joinToString { it.name }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp), fontWeight = FontWeight.Medium)
            }
            HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
        }

        if (wicketHistory.isNotEmpty()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("FALL OF WICKETS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.Gray)
                Text(text = wicketHistory.joinToString { "${it.wicketNumber}-${it.totalRuns} (${it.batterName}, ${it.over} ov)" }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp), lineHeight = 20.sp)
            }
            HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
        }

        Spacer(modifier = Modifier.height(16.dp))
        BowlingTable(bowlingTeamPlayers)
    }
}

@Composable
fun BattingTable(players: List<Player>, strikerId: String?, nonStrikerId: String?, bowlers: List<Player>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFFF5F5F5)).padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("BATTING", modifier = Modifier.weight(4f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Gray)
            Text("R", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = Color.Gray)
            Text("B", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = Color.Gray)
            Text("4s", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = Color.Gray)
            Text("6s", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = Color.Gray)
            Text("SR", modifier = Modifier.width(45.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = Color.Gray)
        }
        
        players.forEach { player ->
            val isCurrentBatter = player.id == strikerId || player.id == nonStrikerId
            if (player.battingStats.balls > 0 || player.battingStats.isOut || isCurrentBatter) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(4f)) {
                        Text(
                            text = player.name + (if (isCurrentBatter && !player.battingStats.isOut) "*" else ""),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (player.battingStats.isOut) Color.Gray else Color.Black
                        )
                        val dismissalBowlerName = bowlers.find { it.id == player.battingStats.dismissalBowlerId }?.name
                        val dismissalText = if (player.battingStats.isOut) {
                            if (dismissalBowlerName != null) "b $dismissalBowlerName" else "out"
                        } else if (isCurrentBatter) "not out" else ""
                        
                        if (dismissalText.isNotEmpty()) {
                            Text(text = dismissalText, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    }
                    Text("${player.battingStats.runs}", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                    Text("${player.battingStats.balls}", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                    Text("${player.battingStats.fours}", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                    Text("${player.battingStats.sixes}", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                    Text(String.format(Locale.getDefault(), "%.1f", player.battingStats.strikeRate), modifier = Modifier.width(45.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
                }
                HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFF0F0F0))
            }
        }
    }
}

@Composable
fun BowlingTable(players: List<Player>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF5F5F5))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("BOWLING", modifier = Modifier.weight(3f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Gray)
            Text("O", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = Color.Gray)
            Text("M", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = Color.Gray)
            Text("R", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = Color.Gray)
            Text("W", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = Color.Gray)
            Text("ECON", modifier = Modifier.width(45.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = Color.Gray)
            Text("WD", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = Color.Gray)
            Text("NB", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = Color.Gray)
        }
        players.forEach { player ->
            if (player.bowlingStats.overs > 0 || player.bowlingStats.balls > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(player.name, modifier = Modifier.weight(3f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(player.bowlingStats.formattedOvers, modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                    Text("${player.bowlingStats.maidens}", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                    Text("${player.bowlingStats.runsConceded}", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                    Text("${player.bowlingStats.wickets}", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.primary)
                    Text(String.format(Locale.getDefault(), "%.2f", player.bowlingStats.economy), modifier = Modifier.width(45.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
                    Text("${player.bowlingStats.wides}", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                    Text("${player.bowlingStats.noBalls}", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                }
                HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFF0F0F0))
            }
        }
    }
}

@Composable
fun OversTab(match: Match) {
    var selectedInnings by remember { mutableIntStateOf(match.currentInnings) }
    val teamA = match.teamA
    val teamB = match.teamB

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
        ScrollableTabRow(
            selectedTabIndex = selectedInnings - 1,
            containerColor = Color.White,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 16.dp,
            divider = {},
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedInnings - 1]),
                    height = 3.dp
                )
            }
        ) {
            val i1Team = if (match.initialBattingTeamId == teamA.id) teamA else teamB
            Tab(selected = selectedInnings == 1, onClick = { selectedInnings = 1 }) {
                Text("${i1Team.name} Innings", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            if (match.currentInnings == 2 || match.status == MatchStatus.COMPLETED) {
                val i2Team = if (match.initialBattingTeamId == teamA.id) teamB else teamA
                Tab(selected = selectedInnings == 2, onClick = { selectedInnings = 2 }) {
                    Text("${i2Team.name} Innings", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }

        val splitIdx = match.innings1Data?.recordedBallsCount ?: match.ballHistory.size
        val balls = if (selectedInnings == 1) {
            match.ballHistory.take(splitIdx)
        } else {
            match.ballHistory.drop(splitIdx)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val reversedBalls = balls.reversed()
            val overs = mutableListOf<List<Ball>>()
            var currentOver = mutableListOf<Ball>()
            
            // Correctly group balls into overs based on isLegalBall
            reversedBalls.forEach { ball ->
                currentOver.add(ball)
                if (ball.isLegalBall && currentOver.count { it.isLegalBall } == 6) {
                    overs.add(currentOver.toList())
                    currentOver = mutableListOf()
                }
            }
            if (currentOver.isNotEmpty()) overs.add(currentOver)
            
            items(overs.size) { index ->
                val overBalls = overs[index]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val overNum = overs.size - index
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Over $overNum", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                            val overRuns = overBalls.sumOf { it.runs + it.extraRuns }
                            Text("$overRuns Runs", fontWeight = FontWeight.Bold, color = Color.DarkGray)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            overBalls.reversed().forEach { ball ->
                                BallCircle(ball)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BallCircle(ball: Ball) {
    val bgColor = when {
        ball.wicketType != WicketType.NONE -> Color(0xFFD32F2F)
        ball.runs == 4 -> Color(0xFF388E3C)
        ball.runs == 6 -> Color(0xFF1976D2)
        ball.extrasType != ExtrasType.NONE -> Color(0xFFFF9800)
        else -> Color(0xFFE0E0E0)
    }
    val contentColor = if (ball.runs == 0 && ball.extrasType == ExtrasType.NONE && ball.wicketType == WicketType.NONE) Color.Black else Color.White

    Surface(
        modifier = Modifier.size(32.dp),
        shape = CircleShape,
        color = bgColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (ball.wicketType != WicketType.NONE) {
                Text("W", color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.Black)
            } else if (ball.runs == 6) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            } else {
                val text = when {
                    ball.extrasType == ExtrasType.WIDE -> "wd"
                    ball.extrasType == ExtrasType.NO_BALL -> "nb"
                    ball.extrasType == ExtrasType.BYE -> "b"
                    ball.extrasType == ExtrasType.LEG_BYE -> "lb"
                    else -> "${ball.runs}"
                }
                Text(text, color = contentColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun PlayerSelectionOverlay(match: Match, viewModel: ScoringViewModel) {
    val battingTeam = if (match.battingTeamId == match.teamA.id) match.teamA else match.teamB
    val bowlingTeam = if (match.bowlingTeamId == match.teamA.id) match.teamA else match.teamB
    
    val title = when (match.pendingAction) {
        PendingAction.SELECT_STRIKER -> "New Batter"
        PendingAction.SELECT_NON_STRIKER -> "Select Non-Striker"
        PendingAction.SELECT_BOWLER -> "Next Bowler"
        else -> ""
    }

    val candidates = when (match.pendingAction) {
        PendingAction.SELECT_STRIKER -> battingTeam.players.filter { !it.battingStats.isOut && it.id != match.nonStrikerId }
        PendingAction.SELECT_NON_STRIKER -> battingTeam.players.filter { !it.battingStats.isOut && it.id != match.strikerId }
        PendingAction.SELECT_BOWLER -> bowlingTeam.players.filter { it.id != match.lastBowlerId }
        else -> emptyList()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.6f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Card(
                modifier = Modifier.fillMaxWidth(0.9f).padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(20.dp))
                    if (candidates.isEmpty()) {
                        Text("No players available! Match might be over.", color = Color.Red, fontWeight = FontWeight.Bold)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                            items(candidates) { player ->
                                ListItem(
                                    headlineContent = { Text(player.name, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                                    modifier = Modifier.clickable { viewModel.selectNewPlayer(player.id) },
                                    leadingContent = { 
                                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    if (match.pendingAction == PendingAction.SELECT_BOWLER) Icons.Default.Refresh else Icons.Default.Person,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                )
                                HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MatchResultOverlay(match: Match, onNavigateToDashboard: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.7f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Card(
                modifier = Modifier.fillMaxWidth(0.85f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("MATCH FINISHED", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val winnerTeam = if (match.winnerId == match.teamA.id) match.teamA else if (match.winnerId == match.teamB.id) match.teamB else null
                    val resultText = if (winnerTeam != null) "${winnerTeam.name}\nWINS!" else "MATCH TIED!"
                    
                    Text(
                        text = resultText,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = if (winnerTeam != null) MaterialTheme.colorScheme.secondary else Color.DarkGray,
                        textAlign = TextAlign.Center,
                        lineHeight = 44.sp
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Button(
                        onClick = onNavigateToDashboard,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("EXIT TO DASHBOARD", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun ScoreCard(match: Match) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${match.totalRuns}/${match.totalWickets}",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF1A1A1A)
                    )
                    Text(
                        text = "OVERS ${match.totalBalls / 6}.${match.totalBalls % 6}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.Gray
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    val crr = if (match.totalBalls > 0) (match.totalRuns.toDouble() / (match.totalBalls / 6.0 + (match.totalBalls % 6) / 6.0)) else 0.0
                    Text(
                        text = "CRR: ${String.format(Locale.getDefault(), "%.2f", crr)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    
                    if (match.currentInnings == 2) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFE3F2FD),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                text = "TARGET ${match.target}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF1976D2)
                            )
                        }
                    }
                }
            }

            if (match.currentInnings == 2) {
                val runsNeeded = (match.target ?: 0) - match.totalRuns
                val ballsRemaining = (match.oversPerInnings * 6) - match.totalBalls
                if (runsNeeded > 0 && ballsRemaining >= 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "NEED $runsNeeded RUNS IN $ballsRemaining BALLS",
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerStatsSection(match: Match) {
    val battingTeam = if (match.battingTeamId == match.teamA.id) match.teamA else match.teamB
    val bowlingTeam = if (match.bowlingTeamId == match.teamA.id) match.teamA else match.teamB
    
    val striker = battingTeam.players.find { it.id == match.strikerId }
    val nonStriker = battingTeam.players.find { it.id == match.nonStrikerId }
    val bowler = bowlingTeam.players.find { it.id == match.currentBowlerId }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("BATSMAN", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Gray)
                Text("R(B)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Gray)
            }
            Spacer(modifier = Modifier.height(8.dp))
            PlayerRow(name = striker?.name ?: "-", stats = "${striker?.battingStats?.runs ?: 0}(${striker?.battingStats?.balls ?: 0})", isStriking = true)
            Spacer(modifier = Modifier.height(8.dp))
            PlayerRow(name = nonStriker?.name ?: "-", stats = "${nonStriker?.battingStats?.runs ?: 0}(${nonStriker?.battingStats?.balls ?: 0})")
            
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("BOWLER", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Gray)
                Text("O-R-W", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Gray)
            }
            Spacer(modifier = Modifier.height(8.dp))
            PlayerRow(
                name = bowler?.name ?: "-",
                stats = "${bowler?.bowlingStats?.formattedOvers ?: "0.0"}-${bowler?.bowlingStats?.runsConceded ?: 0}-${bowler?.bowlingStats?.wickets ?: 0}"
            )
        }
    }
}

@Composable
fun PlayerRow(name: String, stats: String, isStriking: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isStriking) {
                Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = Color(0xFFD32F2F)) {}
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Spacer(modifier = Modifier.width(16.dp))
            }
            Text(
                text = name,
                fontWeight = if (isStriking) FontWeight.Black else FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isStriking) Color.Black else Color.DarkGray
            )
        }
        Text(text = stats, fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ControlsSection(viewModel: ScoringViewModel) {
    Column {
        Text(
            text = "SCORING PANEL",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RunButton("0", modifier = Modifier.weight(1f)) { viewModel.handleRuns(0) }
            RunButton("1", modifier = Modifier.weight(1f)) { viewModel.handleRuns(1) }
            RunButton("2", modifier = Modifier.weight(1f)) { viewModel.handleRuns(2) }
            RunButton("3", modifier = Modifier.weight(1f)) { viewModel.handleRuns(3) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RunButton("4", color = Color(0xFF388E3C), icon = Icons.Default.Star, modifier = Modifier.weight(1.2f)) { viewModel.handleRuns(4) }
            RunButton("6", color = Color(0xFF1976D2), icon = Icons.Default.Star, modifier = Modifier.weight(1.2f)) { viewModel.handleRuns(6) }
            Button(
                onClick = { viewModel.handleWicket() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                modifier = Modifier.weight(2f).height(54.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("WICKET", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExtraButton("WD", modifier = Modifier.weight(1f)) { viewModel.handleExtra(ExtrasType.WIDE) }
            ExtraButton("NB", modifier = Modifier.weight(1f)) { viewModel.handleExtra(ExtrasType.NO_BALL) }
            ExtraButton("BYE", modifier = Modifier.weight(1f)) { viewModel.handleExtra(ExtrasType.BYE) }
            ExtraButton("LB", modifier = Modifier.weight(1f)) { viewModel.handleExtra(ExtrasType.LEG_BYE) }
        }
    }
}

@Composable
fun RunButton(label: String, modifier: Modifier = Modifier, color: Color? = null, icon: ImageVector? = null, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        colors = if (color != null) ButtonDefaults.filledTonalButtonColors(containerColor = color, contentColor = Color.White) 
                 else ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFE0E0E0)),
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(label, fontWeight = FontWeight.Black, fontSize = 22.sp)
        }
    }
}

@Composable
fun ExtraButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(0.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFE0E0E0))
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.DarkGray)
    }
}
