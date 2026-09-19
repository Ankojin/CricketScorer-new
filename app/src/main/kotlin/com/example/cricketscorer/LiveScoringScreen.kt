package com.example.cricketscorer

import com.example.cricketscorer.ui.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale
import android.widget.Toast
import kotlin.time.Duration.Companion.milliseconds
import com.example.cricketscorer.ui.CaptureArea
import com.example.cricketscorer.ui.CardBranding
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScoringScreen(
    viewModel: ScoringViewModel,
    onNavigateToDashboard: () -> Unit,
    onNavigateToMatches: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val match = uiState.match
    
    var selectedTabIndex by remember(match?.id) { mutableIntStateOf(if (match?.status == MatchStatus.COMPLETED) 1 else 0) }
    var viewedInnings by remember(match?.currentInnings) { mutableIntStateOf(match?.currentInnings ?: 1) }
    var showMatchFinishedDialog by remember { mutableStateOf(false) }
    
    // v2.26.72: Auto-trigger celebration when match state changes to COMPLETED 🏏🚀⚖️🏅
    LaunchedEffect(match?.status, match?.id) {
        if (match?.status == MatchStatus.COMPLETED && match?.ballHistory?.isNotEmpty() == true) {
            showMatchFinishedDialog = true
        }
    }
    val statsGraphicsLayer = rememberGraphicsLayer()
    val scorecardGraphicsLayer = rememberGraphicsLayer()
    val oversGraphicsLayer = rememberGraphicsLayer()
    var showOversDialog by remember { mutableStateOf(false) }
    var showManageSquads by remember { mutableStateOf(false) }
    var showWicketDialog by remember { mutableStateOf(false) }
    var showRetireHurtDialog by remember { mutableStateOf(false) }
    var showExtraRunsDialog by remember { mutableStateOf<ExtrasType?>(null) }
    var showOtherRunsDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val isSyncEnabled = uiState.isSyncEnabled
    val connectedDevicesCount = uiState.connectedDevicesCount
    val bowlerNotification = uiState.bowlerNotification

    if (bowlerNotification != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearBowlerNotification() },
            title = { Text("Spell Completed", fontWeight = FontWeight.Black) },
            text = { Text(bowlerNotification, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                Button(onClick = { viewModel.clearBowlerNotification() }) {
                    Text("OK")
                }
            }
        )
    }

    LaunchedEffect(match, connectedDevicesCount) {
        if (isSyncEnabled && match != null && connectedDevicesCount > 0) {
            NearbyManager.broadcastMatch(context, match)
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Match Center", fontWeight = FontWeight.Black)
                            }
                            match?.let { m ->
                                val indicator = when {
                                    m.status == MatchStatus.COMPLETED -> "MATCH COMPLETED"
                                    m.pendingAction == PendingAction.START_SECOND_INNINGS -> "1ST INNINGS COMPLETED"
                                    m.pendingAction != PendingAction.NONE && m.pendingAction != PendingAction.START_SECOND_INNINGS && m.pendingAction != PendingAction.TOSS_REQUIRED -> "ACTION REQUIRED: ${m.pendingAction.toString().replace("_", " ")}"
                                    selectedTabIndex == 1 && viewedInnings == 1 && m.currentInnings == 2 -> "1ST INNINGS COMPLETED"
                                    else -> "LIVE"
                                }
                                val tickerText = if (selectedTabIndex == 1) {
                                    val team = if (viewedInnings == 1) {
                                        if (m.initialBattingTeamId == m.teamA.id) m.teamA else m.teamB
                                    } else {
                                        if (m.initialBattingTeamId == m.teamA.id) m.teamB else m.teamA
                                    }
                                    val runs = if (viewedInnings == 1) (m.innings1Data?.runs ?: if (m.currentInnings == 1) m.totalRuns else 0) else m.totalRuns
                                    val wkts = if (viewedInnings == 1) (m.innings1Data?.wickets ?: if (m.currentInnings == 1) m.totalWickets else 0) else m.totalWickets
                                    val balls = if (viewedInnings == 1) (m.innings1Data?.balls ?: if (m.currentInnings == 1) m.totalBalls else 0) else m.totalBalls
                                    val crr = if (balls > 0) (runs.toDouble() / (balls / 6.0 + (balls % 6) / 6.0)) else 0.0
                                    
                                    "[$indicator] ${team.name} $runs/$wkts (${balls / 6}.${balls % 6}) • CRR: ${String.format(java.util.Locale.US, "%.2f", crr)}"
                                } else {
                                    val battingTeamName = if (m.battingTeamId == m.teamA.id) m.teamA.name else m.teamB.name
                                    val crr = if (m.totalBalls > 0) (m.totalRuns.toDouble() / (m.totalBalls / 6.0 + (m.totalBalls % 6) / 6.0)) else 0.0
                                    
                                    if (m.status == MatchStatus.COMPLETED && showMatchFinishedDialog) {
                                        // Moved celebration to main layer
                                    }

                                    buildString {
                                        append("[$indicator] $battingTeamName ${m.totalRuns}/${m.totalWickets} (${m.totalBalls / 6}.${m.totalBalls % 6})")
                                        append(" • CRR: ${String.format(java.util.Locale.US, "%.2f", crr)}")
                                        if (m.currentInnings == 2 && m.status != MatchStatus.COMPLETED) {
                                            val runsNeeded = (m.target ?: 0) - m.totalRuns
                                            val ballsRemaining = (m.oversPerInnings * 6) - m.totalBalls
                                            append(" • Target: ${m.target} • Need $runsNeeded off $ballsRemaining")
                                        }
                                    }
                                }
                                Text(
                                    text = tickerText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    modifier = Modifier.basicMarquee(
                                        iterations = Int.MAX_VALUE,
                                        velocity = 80.dp // v2.27.0: Ultra-fast scrolling 🏏🚀⚖️🏅
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        // Clean Top Bar v2.19
                    },
                    actions = {
                        IconButton(onClick = { showManageSquads = true }) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Manage Squads")
                        }
                        IconButton(onClick = { showOversDialog = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
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
                        Text("SCORE", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
                    }
                    Tab(selected = selectedTabIndex == 2, onClick = { selectedTabIndex = 2 }) {
                        Text("OVERS", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
                    }
                    Tab(selected = selectedTabIndex == 3, onClick = { selectedTabIndex = 3 }) {
                        Text("STATS", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { padding ->
        match?.let { m ->
            Box(modifier = Modifier.padding(padding)) {
                when (selectedTabIndex) {
                    0 -> LiveTab(
                        uiState = uiState,
                        viewModel = viewModel,
                        onShowWicket = { showWicketDialog = true },
                        onShowExtraRuns = { type ->
                            showExtraRunsDialog = type
                        },
                        onShowOtherRuns = { showOtherRunsDialog = true },
                        onShowRetireHurt = { showRetireHurtDialog = true }
                    )
                    1 -> ScorecardTab(uiState, viewedInnings, scorecardGraphicsLayer) { viewedInnings = it }
                    2 -> OversTab(uiState, viewModel, oversGraphicsLayer)
                    3 -> StatsTab(uiState, statsGraphicsLayer)
                }

                if (match.pendingAction == PendingAction.TOSS_REQUIRED || match.pendingAction == PendingAction.SELECT_MATCH_SETTINGS) {
                    MatchSettingsDialog(uiState, viewModel, onDismiss = onNavigateToDashboard)
                } else if (match.pendingAction == PendingAction.START_SECOND_INNINGS) {
                    InningsOverOverlay(uiState, viewModel)
                } else if (match.pendingAction == PendingAction.SELECT_RUNS_DROPPED_CATCH) {
                    DroppedCatchRunsOverlay(viewModel)
                } else if (match.pendingAction == PendingAction.SELECT_RUNS_WICKET) {
                    RunOutRunsOverlay(viewModel)
                } else if (match.pendingAction != PendingAction.NONE && match.status != MatchStatus.COMPLETED) {
                    PlayerSelectionOverlay(uiState, viewModel)
                }
                
                if (match.status == MatchStatus.COMPLETED && showMatchFinishedDialog) {
                    MatchCelebrationDialog(
                        uiState = uiState, 
                        onNavigateToDashboard = onNavigateToDashboard,
                        onDismiss = { showMatchFinishedDialog = false }
                    )
                }

                if (showManageSquads) {
                    ManageSquadsOverlay(uiState, viewModel) { showManageSquads = false }
                }

                if (showWicketDialog) {
                    WicketDialog(uiState, viewModel) { showWicketDialog = false }
                }

                if (showRetireHurtDialog) {
                    RetireHurtDialog(uiState, viewModel) { showRetireHurtDialog = false }
                }

                showExtraRunsDialog?.let { type: ExtrasType ->
                    ExtraRunsDialog(type, viewModel) { showExtraRunsDialog = null }
                }

                if (showOtherRunsDialog) {
                    OtherRunsDialog(viewModel) { showOtherRunsDialog = false }
                }

                if (showOversDialog) {
                    MatchSettingsDialog(uiState, viewModel) { showOversDialog = false }
                }
            }
        } ?: Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "No Active Match Selected",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Select a match from the Matches tab to begin scoring.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onNavigateToMatches,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("VIEW MATCHES", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun OversTab(uiState: MatchUiState, viewModel: ScoringViewModel, graphicsLayer: GraphicsLayer) {
    val match = uiState.match ?: return
    var selectedInnings by remember { mutableIntStateOf(match.currentInnings) }
    var editingBallIndex by remember { mutableStateOf<Int?>(null) }
    val teamA = match.teamA
    val teamB = match.teamB
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var shareTrigger by remember { mutableIntStateOf(0) }

    val battingTeam = if (selectedInnings == 1) (if (match.initialBattingTeamId == teamA.id) teamA else teamB) else (if (match.initialBattingTeamId == teamA.id) teamB else teamA)
    val bowlingTeam = if (battingTeam.id == teamA.id) teamB else teamA

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
        Button(
            onClick = {
                scope.launch {
                    shareTrigger++
                    kotlinx.coroutines.delay(300.milliseconds)
                    val fileName = if (selectedInnings == 1) "innings_1_overs" else "innings_2_overs"
                    shareComposableScreenshot(context, graphicsLayer, fileName)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF003366), contentColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("SHARE FULL OVERS HISTORY", fontWeight = FontWeight.Black)
        }

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

        // v2.19 (Engine Hardened & Draw Fix) 🏏🚀⚖️🏅: Precise splitIdx derivation
        val splitIdx = if (match.currentInnings == 1) match.ballHistory.size else (match.innings1Data?.recordedBallsCount ?: 0)
        val inningsBallsWithIndices = if (selectedInnings == 1) {
            match.ballHistory.take(splitIdx).mapIndexed { index, ball -> index to ball }
        } else {
            match.ballHistory.drop(splitIdx).mapIndexed { index, ball -> (index + splitIdx) to ball }
        }

        Box(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
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
                    Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                        val overs = mutableListOf<List<Pair<Int, Ball>>>()
                        var currentOver = mutableListOf<Pair<Int, Ball>>()
                        
                        inningsBallsWithIndices.forEach { (idx, ball) ->
                            currentOver.add(idx to ball)
                            if (ball.isPhysicalBall && currentOver.count { it.second.isPhysicalBall } == 6) {
                                overs.add(currentOver.toList())
                                currentOver = mutableListOf()
                            }
                        }
                        if (currentOver.isNotEmpty()) overs.add(currentOver)
                        
                        val reversedOvers = overs.reversed()
                        reversedOvers.forEachIndexed { index, overBalls ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    val overNum = overs.size - index
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column {
                                            Text("Over $overNum", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                            
                                            // v2.27.1: Filter out adjustments for sequence and display 🏏🚀⚖️🏅
                                            val validBalls = overBalls.filter { !it.second.isAdjustment }
                                            
                                            val sequence = mutableListOf<Pair<String, Int>>()
                                            validBalls.forEach { (_, b) ->
                                                val name = recoverName(b.bowlerId, match, "Bowler")
                                                if (sequence.isEmpty() || sequence.last().first != name) {
                                                    sequence.add(name to if (b.isPhysicalBall) 1 else 0)
                                                } else {
                                                    val last = sequence.removeAt(sequence.size - 1)
                                                    sequence.add(last.first to (last.second + (if (b.isPhysicalBall) 1 else 0)))
                                                }
                                            }
                                            val bowlersInOver = sequence.joinToString(", ") { "${it.first} (${it.second})" }
                                            
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CricketBallIcon(modifier = Modifier.size(10.dp).padding(end = 4.dp))
                                                Text("Bowlers: $bowlersInOver", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        val validBalls = overBalls.filter { !it.second.isAdjustment }
                                        val overRuns = validBalls.sumOf { it.second.runs + it.second.extraRuns }
                                        val overWickets = validBalls.count { it.second.wicketType != WicketType.NONE && it.second.wicketType != WicketType.RETIRED_HURT }
                                        Text("$overRuns Runs" + (if (overWickets > 0) ", $overWickets Wkts" else ""), fontWeight = FontWeight.Bold, color = Color.DarkGray)
                                    }
                                    val validBalls = overBalls.filter { !it.second.isAdjustment }
                                    val droppedInOver = validBalls.filter { it.second.isDroppedCatch }
                                    if (droppedInOver.isNotEmpty()) {
                                        val droppedNames = droppedInOver.map { recoverName(it.second.fielderId, match, "Fielder") }.joinToString(", ")
                                        Text("🤲 Dropped by: $droppedNames", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE65100), fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        validBalls.forEach { (idx, ball) ->
                                            BallBox(
                                                ball = ball, 
                                                onClick = { 
                                                    if (match.status != MatchStatus.COMPLETED) {
                                                        editingBallIndex = idx 
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        CardBranding()
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    if (editingBallIndex != null) {
        val ball = match.ballHistory[editingBallIndex!!]
        var runs by remember { mutableIntStateOf(ball.runs) }
        var extraType by remember { mutableStateOf(ball.extrasType) }
        var extraRuns by remember { mutableIntStateOf(ball.extraRuns) }
        var wicketType by remember { mutableStateOf(ball.wicketType) }
        var sId by remember { mutableStateOf(ball.strikerId) }
        var nsId by remember { mutableStateOf(ball.nonStrikerId) }
        var bId by remember { mutableStateOf(ball.bowlerId) }

        val battingTeam = if (match.battingTeamId == match.teamA.id) match.teamA else match.teamB
        val bowlingTeam = if (match.battingTeamId == match.teamA.id) match.teamB else match.teamA

        AlertDialog(
            onDismissRequest = { editingBallIndex = null },
            title = { Text("Edit Ball ${editingBallIndex!! + 1}", fontWeight = FontWeight.Black) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Runs Faced: $runs", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(0, 1, 2, 3, 4, 6).forEach { r ->
                            val isSelected = runs == r
                            FilledTonalButton(
                                onClick = { runs = r },
                                modifier = Modifier.weight(1f).height(40.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            ) { Text("$r", color = if (isSelected) Color.White else Color.Black) }
                        }
                    }
                    
                    Text("Extra Type: ${extraType.name}", fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ExtrasType.entries.filter { it != ExtrasType.NONE }.forEach { type ->
                            val isSelected = extraType == type
                            OutlinedButton(
                                onClick = { extraType = if (isSelected) ExtrasType.NONE else type },
                                modifier = Modifier.weight(1f).height(40.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = if (isSelected) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else ButtonDefaults.outlinedButtonColors()
                            ) { Text(type.name.take(2), fontSize = 10.sp) }
                        }
                    }

                    if (extraType != ExtrasType.NONE) {
                        Text("Extra Runs: $extraRuns", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(0, 1, 2, 3, 4).forEach { r ->
                                val isSelected = extraRuns == r
                                OutlinedButton(
                                    onClick = { extraRuns = r },
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = if (isSelected) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else ButtonDefaults.outlinedButtonColors()
                                ) { Text("$r") }
                            }
                        }
                    }

                    Text("Wicket: ${wicketType.name}", fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val wTypes = listOf(WicketType.BOWLED, WicketType.CAUGHT, WicketType.LBW, WicketType.RUN_OUT, WicketType.STUMPED)
                        wTypes.forEach { type ->
                            val isSelected = wicketType == type
                            OutlinedButton(
                                onClick = { wicketType = if (isSelected) WicketType.NONE else type },
                                modifier = Modifier.weight(1f).height(40.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = if (isSelected) ButtonDefaults.outlinedButtonColors(containerColor = Color.Red.copy(alpha = 0.1f)) else ButtonDefaults.outlinedButtonColors()
                            ) { Text(type.name.take(2), fontSize = 10.sp) }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Players Selection", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)

                    Text("Striker", fontWeight = FontWeight.Bold)
                    PlayerDropdown(selectedId = sId, players = battingTeam.players) { sId = it }

                    Text("Non-Striker", fontWeight = FontWeight.Bold)
                    PlayerDropdown(selectedId = nsId, players = battingTeam.players) { nsId = it }

                    Text("Bowler", fontWeight = FontWeight.Bold)
                    PlayerDropdown(selectedId = bId, players = bowlingTeam.players) { bId = it }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val updatedBall = ball.copy(
                        runs = runs,
                        extrasType = extraType,
                        extraRuns = if (extraType == ExtrasType.NONE) 0 else extraRuns,
                        wicketType = wicketType,
                        isLegalBall = extraType != ExtrasType.WIDE && extraType != ExtrasType.NO_BALL,
                        strikerId = sId,
                        nonStrikerId = nsId,
                        bowlerId = bId
                    )
                    viewModel.editBall(editingBallIndex!!, updatedBall)
                    editingBallIndex = null
                }) {
                    Text("SAVE")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val newHistory = match.ballHistory.toMutableList()
                        newHistory.removeAt(editingBallIndex!!)
                        viewModel.loadMatch(match.copy(ballHistory = newHistory, strikerId = null, nonStrikerId = null, currentBowlerId = null))
                        editingBallIndex = null
                    }) {
                        Text("DELETE", color = Color.Red)
                    }
                    TextButton(onClick = { editingBallIndex = null }) {
                        Text("CANCEL")
                    }
                }
            }
        )
    }
}

@Composable
fun MatchSummaryCard(uiState: MatchUiState) {
    val match = uiState.match ?: return
    val teamA = match.teamA
    val teamB = match.teamB
    val i1Data = match.innings1Data

    fun getScoreString(teamId: String?): String {
        if (teamId == null) return "DNB"
        return if (match.initialBattingTeamId == teamId) {
            if (match.currentInnings == 1) {
                "${match.totalRuns}/${match.totalWickets} (${match.totalBalls / 6}.${match.totalBalls % 6})"
            } else {
                i1Data?.let { "${it.runs}/${it.wickets} (${it.balls / 6}.${it.balls % 6})" } ?: "DNB"
            }
        } else {
            if (match.currentInnings == 1) {
                "Yet to bat"
            } else {
                "${match.totalRuns}/${match.totalWickets} (${match.totalBalls / 6}.${match.totalBalls % 6})"
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("MATCH SUMMARY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(teamA?.name ?: "Team A", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Text(getScoreString(teamA?.id), fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(teamB?.name ?: "Team B", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Text(getScoreString(teamB?.id), fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyLarge)
            }
            if (match.status == MatchStatus.COMPLETED) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))
                val winnerTeam = if (match.winnerId == teamA.id) teamA else if (match.winnerId == teamB.id) teamB else null
                Text(
                    text = if (winnerTeam != null) "${winnerTeam.name} won! 🏆" else "Match Drawn! 🤝",
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun MotmSection(uiState: MatchUiState) {
    val match = uiState.match ?: return
    val allPlayers = match.teamA.players + match.teamB.players
    
    // v2.29.0: ICC Standard Impact Engine 🏏🚀⚖️🏅
    // Focusing on Match-Turning Milestones and Winning Contribution.
    
    val playerImpacts = mutableMapOf<String, Double>()
    allPlayers.forEach { playerImpacts[it.id] = 0.0 }

    allPlayers.forEach { p ->
        var score = 0.0
        
        // 1. Batting Contribution
        if (p.battingStats.balls > 0) {
            score += p.battingStats.runs * 1.0 // 1 pt per run
            score += p.battingStats.fours * 1.0 // +1 bonus per 4
            score += p.battingStats.sixes * 2.0 // +2 bonus per 6
            
            // ICC Milestone Bonuses
            if (p.battingStats.runs >= 50) score += 20.0
            else if (p.battingStats.runs >= 30) score += 10.0
            
            // Strike Rate Impact (Min 10 balls)
            if (p.battingStats.balls >= 10) {
                if (p.battingStats.strikeRate > 200) score += 15.0
                else if (p.battingStats.strikeRate > 150) score += 8.0
            }
        }

        // 2. Bowling Contribution
        if (p.bowlingStats.balls > 0 || p.bowlingStats.overs > 0) {
            score += p.bowlingStats.wickets * 25.0 // 25 pts per wicket
            
            // ICC Milestone Bonuses
            if (p.bowlingStats.wickets >= 3) score += 25.0
            else if (p.bowlingStats.wickets >= 2) score += 10.0
            
            // Economy Impact (Min 1 over)
            if (p.bowlingStats.overs >= 1) {
                if (p.bowlingStats.economy < 6.0) score += 15.0
                else if (p.bowlingStats.economy < 8.0) score += 5.0
                else if (p.bowlingStats.economy > 11.0) score -= 10.0
            }
            
            // Dot Ball Pressure
            score += p.bowlingStats.dotBalls * 1.0
        }

        // 3. Fielding Contribution
        score += p.fieldingStats.catches * 10.0
        score += p.fieldingStats.stumpings * 10.0
        score += p.fieldingStats.runOuts * 15.0 // Run outs are high impact

        // 4. Winning Contribution (ICC Standard Bias)
        val isWinner = match.winnerId != null && (match.teamA.players.any { it.id == p.id } && match.winnerId == match.teamA.id || match.teamB.players.any { it.id == p.id } && match.winnerId == match.teamB.id)
        if (isWinner) score += 25.0

        playerImpacts[p.id] = score
    }

    val mvpEntry = playerImpacts.maxByOrNull { it.value }
    val mvp = allPlayers.find { it.id == mvpEntry?.key }

    if (mvp != null && (mvpEntry?.value ?: 0.0) > 10.0) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A237E), contentColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(52.dp), shape = CircleShape, color = Color(0xFFFFD700)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🌟", fontSize = 28.sp)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("MAN OF THE MATCH • ICC RANKED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.7f))
                    Text(mvp.name.uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("IMPACT SCORE: ", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.9f))
                        Text(String.format(java.util.Locale.US, "%.0f", mvpEntry?.value ?: 0.0), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black, color = Color(0xFFFFD700))
                    }
                }
            }
        }
    }
}

@Composable
fun MatchForecasterSection(uiState: MatchUiState) {
    val match = uiState.match ?: return
    // pillar 3: ESPNcricinfo Forecaster Suite 🏏🚀⚖️🏅
    val teamA = match.teamA
    val teamB = match.teamB
    val totalBalls = match.oversPerInnings * 6
    val currentBalls = match.totalBalls
    val remainingBalls = totalBalls - currentBalls
    
    // 1. PROJECTED SCORE (Innings 1 or 2)
    val crr = if (currentBalls > 0) (match.totalRuns.toDouble() / currentBalls) * 6 else 0.0
    val projectedAtCurrent = match.totalRuns + (crr * (remainingBalls / 6.0))
    val projectedAt10 = match.totalRuns + (10.0 * (remainingBalls / 6.0))
    
    // 2. WIN PROBABILITY (Context-Aware)
    var teamAWinProb = 50.0
    
    if (match.currentInnings == 1) {
        // Innings 1: Probability driven by projected score vs historical par (assume 160 for T20)
        teamAWinProb = if (match.battingTeamId == teamA.id) {
            (projectedAtCurrent / 320.0) * 100.0 // Simplified par-based scaling
        } else {
            100.0 - (projectedAtCurrent / 320.0) * 100.0
        }
    } else if (match.target != null) {
        // Innings 2: Probability driven by RRR vs CRR & wickets left
        val runsNeeded = match.target - match.totalRuns
        if (remainingBalls > 0) {
            val rrr = (runsNeeded.toDouble() / remainingBalls) * 6
            val wicketFactor = (10 - match.totalWickets) / 10.0
            
            // RRR of 8.0 is roughly 50%
            val baseProb = (1.0 - (rrr / 16.0)).coerceIn(0.0, 1.0)
            teamAWinProb = if (match.battingTeamId == teamA.id) baseProb * 100 * wicketFactor else (1.0 - (baseProb * wicketFactor)) * 100
        } else {
            teamAWinProb = if (match.totalRuns >= match.target) (if (match.battingTeamId == teamA.id) 100.0 else 0.0) else (if (match.battingTeamId == teamA.id) 0.0 else 100.0)
        }
    }
    
    teamAWinProb = teamAWinProb.coerceIn(5.0, 95.0) // Never 0 or 100 until finished
    val teamBWinProb = 100.0 - teamAWinProb

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("MATCH FORECASTER 🔮", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            
            Spacer(modifier = Modifier.height(10.dp))
            
            // Win Probability Meter
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(teamA.name.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(teamB.name.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth().height(10.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxHeight().weight(teamAWinProb.toFloat()).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp)))
                    Box(modifier = Modifier.fillMaxHeight().weight(teamBWinProb.toFloat()).background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp)))
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${teamAWinProb.toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                Text("${teamBWinProb.toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary)
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), thickness = 0.5.dp)
            
            // Projected Scores
            val label = if (match.currentInnings == 2) "PAR SCORE: ${match.target}" else "PROJECTED SCORE"
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Gray)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ForecastItem("At ${String.format(java.util.Locale.US, "%.1f", crr)} RPO", projectedAtCurrent.toInt().toString(), Modifier.weight(1f))
                ForecastItem("At 10.0 RPO", projectedAt10.toInt().toString(), Modifier.weight(1f))
                if (match.currentInnings == 2) {
                    val rrr = if (remainingBalls > 0) ( (match.target!! - match.totalRuns).toDouble() / remainingBalls ) * 6 else 0.0
                    ForecastItem("RRR", String.format(java.util.Locale.US, "%.2f", rrr), Modifier.weight(1f), isHighlight = true)
                }
            }
        }
    }
}

@Composable
fun ForecastItem(label: String, value: String, modifier: Modifier = Modifier, isHighlight: Boolean = false) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = if (isHighlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun LiveTab(
    uiState: MatchUiState,
    viewModel: ScoringViewModel,
    onShowWicket: () -> Unit,
    onShowExtraRuns: (ExtrasType) -> Unit,
    onShowOtherRuns: () -> Unit,
    onShowRetireHurt: () -> Unit
) {
    val match = uiState.match ?: return
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        text = "$tossWinnerName won toss • ${match.tossDecision}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = "INNINGS ${match.currentInnings} • ${battingTeam.name.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Black
                )
            }
        }
        item { ScoreCard(uiState) }
        item { PlayerStatsSection(uiState, viewModel) }
        
        item {
            if (match.status == MatchStatus.COMPLETED) {
                val winnerTeam = if (match.winnerId == match.teamA.id) match.teamA else if (match.winnerId == match.teamB.id) match.teamB else null
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("MATCH SUMMARY", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (winnerTeam != null) "${winnerTeam.name} 🏅" else "MATCH DRAWN 🤝",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                ControlsSection(
                    uiState = uiState,
                    viewModel = viewModel,
                    onShowWicket = onShowWicket,
                    onShowExtraRuns = onShowExtraRuns,
                    onShowOtherRuns = onShowOtherRuns,
                    onShowRetireHurt = onShowRetireHurt
                )
            }
        }

        if (match.status == MatchStatus.LIVE) {
            item { MatchForecasterSection(uiState) }
        }
    }
}

@Composable
fun ScoreCard(uiState: MatchUiState) {
    val match = uiState.match ?: return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val battingTeam = if (match.battingTeamId == match.teamA.id) match.teamA else match.teamB
            val bowlingTeam = if (match.battingTeamId == match.teamA.id) match.teamB else match.teamA
            
            Text(battingTeam.name.uppercase(), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelLarge)
            
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Text(text = "${match.totalRuns}/${match.totalWickets}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = "(${match.totalBalls / 6}.${match.totalBalls % 6})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    val crr = if (match.totalBalls > 0) (match.totalRuns.toDouble() / match.totalBalls) * 6 else 0.0
                    Text(text = "CRR: ${String.format(java.util.Locale.US, "%.2f", crr)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }

            if (match.currentInnings == 2) {
                val needed = (match.target ?: 0) - match.totalRuns
                val ballsLeft = (match.oversPerInnings * 6) - match.totalBalls
                if (needed > 0 && match.status != MatchStatus.COMPLETED) {
                    Text("Need $needed off $ballsLeft balls", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                }
                
                // Show Innings 1 Score for context
                match.innings1Data?.let { i1 ->
                    Text(text = "Target: ${match.target} (${bowlingTeam.name}: ${i1.runs}/${i1.wickets})", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun PlayerStatsSection(uiState: MatchUiState, viewModel: ScoringViewModel) {
    val match = uiState.match ?: return
    val teamPlayers = if (match.battingTeamId == match.teamA.id) match.teamA.players else match.teamB.players
    val striker = if (match.strikerId != null) teamPlayers.find { it.id == match.strikerId } else null
    val nonStriker = if (match.nonStrikerId != null) teamPlayers.find { it.id == match.nonStrikerId } else null
    val bowlingTeam = if (match.battingTeamId == match.teamA.id) match.teamB else match.teamA
    val bowler = if (match.currentBowlerId != null) bowlingTeam.players.find { it.id == match.currentBowlerId } else null

    val isCompleted = match.status == MatchStatus.COMPLETED

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth().background(Color.LightGray.copy(alpha = 0.2f)).padding(8.dp)) {
            Text("🏏 Batter", modifier = Modifier.weight(3f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            Text("R", modifier = Modifier.width(30.dp), fontWeight = FontWeight.Bold, textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
            Text("B", modifier = Modifier.width(30.dp), fontWeight = FontWeight.Bold, textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
            Text("4s", modifier = Modifier.width(30.dp), fontWeight = FontWeight.Bold, textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
            Text("6s", modifier = Modifier.width(30.dp), fontWeight = FontWeight.Bold, textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
            Text("SR", modifier = Modifier.width(40.dp), fontWeight = FontWeight.Bold, textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
        }
        striker?.let { 
            val isCaptain = it.isCaptain
            val isWK = it.id == match.teamAWicketKeeperId || it.id == match.teamBWicketKeeperId
            val roleSuffix = if (isCaptain) " (c)" else if (it.isViceCaptain) " (vc)" else ""
            val nameWithExtras = it.name + " (${(it.battingStyle ?: BattingStyle.RHB).name})" + (if (it.isJoker) " 🃏" else "") + roleSuffix + (if (isWK) " 🧤" else "")
            PlayerRow(nameWithExtras, it.battingStats.runs, it.battingStats.balls, it.battingStats.fours, it.battingStats.sixes, it.battingStats.strikeRate, true, onNameClick = { 
                if (!isCompleted) viewModel.replaceStriker() 
            }) 
        }
        nonStriker?.let { 
            val isCaptain = it.isCaptain
            val isViceCaptain = it.isViceCaptain
            val isWK = it.id == match.teamAWicketKeeperId || it.id == match.teamBWicketKeeperId
            val roleSuffix = if (isCaptain) " (c)" else if (isViceCaptain) " (vc)" else ""
            val nameWithExtras = it.name + " (${(it.battingStyle ?: BattingStyle.RHB).name})" + (if (it.isJoker) " 🃏" else "") + roleSuffix + (if (isWK) " 🧤" else "")
            PlayerRow(nameWithExtras, it.battingStats.runs, it.battingStats.balls, it.battingStats.fours, it.battingStats.sixes, it.battingStats.strikeRate, false, onNameClick = { 
                if (!isCompleted) viewModel.replaceNonStriker() 
            }) 
        }
        
        Button(
            onClick = { viewModel.swapStrike() },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            enabled = !isCompleted,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("SWAP BATSMEN (FIX SELECTION)", fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth().background(Color.LightGray.copy(alpha = 0.2f)).padding(8.dp)) {
            Text("⚾ Bowler", modifier = Modifier.weight(3f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            Text("O", modifier = Modifier.width(30.dp), fontWeight = FontWeight.Bold, textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
            Text("M", modifier = Modifier.width(30.dp), fontWeight = FontWeight.Bold, textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
            Text("NB", modifier = Modifier.width(30.dp), fontWeight = FontWeight.Bold, textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
            Text("WD", modifier = Modifier.width(30.dp), fontWeight = FontWeight.Bold, textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
            Text("R", modifier = Modifier.width(30.dp), fontWeight = FontWeight.Bold, textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
            Text("W", modifier = Modifier.width(30.dp), fontWeight = FontWeight.Bold, textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
            Text("ER", modifier = Modifier.width(40.dp), fontWeight = FontWeight.Bold, textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
        }
        bowler?.let { 
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val isCaptain = it.isCaptain
                val isViceCaptain = it.isViceCaptain
                val isWK = it.id == match.teamAWicketKeeperId || it.id == match.teamBWicketKeeperId
                val roleSuffix = if (isCaptain) " (c)" else if (isViceCaptain) " (vc)" else ""
                val nameWithExtras = it.name + (if (it.isJoker) " 🃏" else "") + roleSuffix + (if (isWK) " 🧤" else "")
                
                Row(modifier = Modifier.weight(3f).clickable { if (!isCompleted) viewModel.replaceBowler() }, verticalAlignment = Alignment.CenterVertically) {
                    Text(nameWithExtras, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (!isCompleted) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(12.dp), tint = Color.Gray)
                    }
                }
                
                Text(it.bowlingStats.formattedOvers, modifier = Modifier.width(30.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall, fontSize = 13.sp)
                Text("${it.bowlingStats.maidens}", modifier = Modifier.width(30.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall, fontSize = 13.sp)
                Text("${it.bowlingStats.noBalls}", modifier = Modifier.width(30.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall, fontSize = 13.sp)
                Text("${it.bowlingStats.wides}", modifier = Modifier.width(30.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall, fontSize = 13.sp)
                Text("${it.bowlingStats.runsConceded}", modifier = Modifier.width(30.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall, fontSize = 13.sp)
                Text("${it.bowlingStats.wickets}", modifier = Modifier.width(30.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(String.format(java.util.Locale.US, "%.2f", it.bowlingStats.economy), modifier = Modifier.width(40.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun PlayerRow(name: String, r: Int, b: Int, s4: Int, s6: Int, sr: Double, isStriker: Boolean, onNameClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = Modifier.weight(3f).clickable { onNameClick() }, verticalAlignment = Alignment.CenterVertically) {
            Text(name + (if (isStriker) "*" else ""), style = MaterialTheme.typography.bodySmall, fontWeight = if (isStriker) FontWeight.Bold else FontWeight.Normal)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(12.dp), tint = Color.Gray)
        }
        Text("$r", modifier = Modifier.width(30.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall)
        Text("$b", modifier = Modifier.width(30.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall)
        Text("$s4", modifier = Modifier.width(30.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall)
        Text("$s6", modifier = Modifier.width(30.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall)
        Text(String.format(java.util.Locale.US, "%.1f", sr), modifier = Modifier.width(40.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ControlsSection(
    uiState: MatchUiState,
    viewModel: ScoringViewModel,
    onShowWicket: () -> Unit,
    onShowExtraRuns: (ExtrasType) -> Unit,
    onShowOtherRuns: () -> Unit,
    onShowRetireHurt: () -> Unit
) {
    val match = uiState.match ?: return
    val androidContext = LocalContext.current
    val isCompleted = match.status == MatchStatus.COMPLETED
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Row 1: DOT, 1, 1D, 2, 3, 4, 6
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RunButton(runs = 0, modifier = Modifier.weight(1f), label = "DOT", enabled = !isCompleted) { viewModel.handleRuns(0, true) }
            RunButton(runs = 1, modifier = Modifier.weight(1f), enabled = !isCompleted) { viewModel.handleRuns(1, true) }
            RunButton(
                runs = 1, 
                modifier = Modifier.weight(1f), 
                label = "1G",
                enabled = !isCompleted,
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) { viewModel.handleExtra(ExtrasType.GRANTED, 1) }
            RunButton(runs = 2, modifier = Modifier.weight(1f), enabled = !isCompleted) { viewModel.handleRuns(2, true) }
            RunButton(runs = 3, modifier = Modifier.weight(1f), enabled = !isCompleted) { viewModel.handleRuns(3, true) }
            RunButton(runs = 4, modifier = Modifier.weight(1f), label = "4 💥", enabled = !isCompleted) { viewModel.handleRuns(4, true) }
            RunButton(runs = 6, modifier = Modifier.weight(1f), label = "6 💥", enabled = !isCompleted) { viewModel.handleRuns(6, true) }
        }
        // Row 2: WIDE, NO-BALL, BYE, L-BYE, OVERTHROW
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExtraButton("WIDE", ExtrasType.WIDE, viewModel, onShowExtraRuns, modifier = Modifier.weight(1f), enabled = !isCompleted)
            ExtraButton("NO-BALL", ExtrasType.NO_BALL, viewModel, onShowExtraRuns, modifier = Modifier.weight(1.3f), enabled = !isCompleted)
            ExtraButton("BYE", ExtrasType.BYE, viewModel, onShowExtraRuns, modifier = Modifier.weight(1f), enabled = !isCompleted)
            ExtraButton("L-BYE", ExtrasType.LEG_BYE, viewModel, onShowExtraRuns, modifier = Modifier.weight(1.1f), enabled = !isCompleted)
            Button(
                onClick = onShowOtherRuns,
                enabled = !isCompleted,
                modifier = Modifier.weight(1.8f).height(48.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
            ) {
                Text("⚾ OVERTHROW", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        // Row 3: WICKET (Red), RETIRE HURT (Grey), UNDO
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onShowWicket,
                modifier = Modifier.weight(1.5f).height(56.dp),
                enabled = !isCompleted,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F), contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("🏏", fontSize = 22.sp)
                    Text("WICKET", fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 0.5.sp)
                }
            }
            Button(
                onClick = onShowRetireHurt,
                modifier = Modifier.weight(1.2f).height(56.dp),
                enabled = !isCompleted,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("🤕 RETIRE", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            }
            Button(
                onClick = { viewModel.undo(androidContext) },
                modifier = Modifier.weight(1.3f).height(56.dp),
                enabled = !isCompleted,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("⏪ UNDO", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

