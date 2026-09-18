package com.example.cricketscorer

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
    val match by viewModel.matchState.collectAsState()
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
    val isSyncEnabled by viewModel.isSyncEnabled.collectAsState()
    val connectedDevices by NearbyManager.connectedEndpoints.collectAsState()
    val bowlerNotification by viewModel.bowlerNotification.collectAsState()

    if (bowlerNotification != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearBowlerNotification() },
            title = { Text("Spell Completed", fontWeight = FontWeight.Black) },
            text = { Text(bowlerNotification!!, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                Button(onClick = { viewModel.clearBowlerNotification() }) {
                    Text("OK")
                }
            }
        )
    }

    LaunchedEffect(match, connectedDevices.size) {
        if (isSyncEnabled && match != null && connectedDevices.isNotEmpty()) {
            NearbyManager.broadcastMatch(context, match!!)
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
                        match = m,
                        viewModel = viewModel,
                        onShowWicket = { showWicketDialog = true },
                        onShowExtraRuns = { type ->
                            showExtraRunsDialog = type
                        },
                        onShowOtherRuns = { showOtherRunsDialog = true },
                        onShowRetireHurt = { showRetireHurtDialog = true }
                    )
                    1 -> ScorecardTab(m, viewedInnings, scorecardGraphicsLayer) { viewedInnings = it }
                    2 -> OversTab(m, viewModel, oversGraphicsLayer)
                    3 -> StatsTab(m, statsGraphicsLayer)
                }

                if (m.pendingAction == PendingAction.TOSS_REQUIRED || m.pendingAction == PendingAction.SELECT_MATCH_SETTINGS) {
                    MatchSettingsDialog(m, viewModel, onDismiss = onNavigateToDashboard)
                } else if (m.pendingAction == PendingAction.START_SECOND_INNINGS) {
                    InningsOverOverlay(m, viewModel)
                } else if (m.pendingAction == PendingAction.SELECT_RUNS_DROPPED_CATCH) {
                    DroppedCatchRunsOverlay(viewModel)
                } else if (m.pendingAction == PendingAction.SELECT_RUNS_WICKET) {
                    RunOutRunsOverlay(viewModel)
                } else if (m.pendingAction != PendingAction.NONE && m.status != MatchStatus.COMPLETED) {
                    PlayerSelectionOverlay(m, viewModel)
                }
                
                if (m.status == MatchStatus.COMPLETED && showMatchFinishedDialog) {
                    MatchCelebrationDialog(
                        match = m, 
                        onNavigateToDashboard = onNavigateToDashboard,
                        onDismiss = { showMatchFinishedDialog = false }
                    )
                }

                if (showManageSquads) {
                    ManageSquadsOverlay(m, viewModel) { showManageSquads = false }
                }

                if (showWicketDialog) {
                    WicketDialog(m, viewModel) { showWicketDialog = false }
                }

                if (showRetireHurtDialog) {
                    RetireHurtDialog(m, viewModel) { showRetireHurtDialog = false }
                }

                showExtraRunsDialog?.let { type: ExtrasType ->
                    ExtraRunsDialog(type, viewModel) { showExtraRunsDialog = null }
                }

                if (showOtherRunsDialog) {
                    OtherRunsDialog(viewModel) { showOtherRunsDialog = false }
                }

                if (showOversDialog) {
                    MatchSettingsDialog(m, viewModel) { showOversDialog = false }
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
fun ScorecardTab(
    match: Match,
    viewedInnings: Int,
    graphicsLayer: GraphicsLayer,
    onInningsChange: (Int) -> Unit
) {
    val teamA = match.teamA
    val teamB = match.teamB
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var shareTrigger by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F9FA))) {
        Button(
            onClick = {
                scope.launch {
                    shareTrigger++
                    kotlinx.coroutines.delay(300.milliseconds)
                    val fileName = if (viewedInnings == 1) "innings_1_scorecard" else "innings_2_scorecard"
                    shareComposableScreenshot(context, graphicsLayer, fileName)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF003366), contentColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("SHARE FULL SCORECARD", fontWeight = FontWeight.Black)
        }

        ScrollableTabRow(
            selectedTabIndex = viewedInnings - 1,
            containerColor = Color.White,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 16.dp,
            divider = {},
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[viewedInnings - 1]),
                    height = 3.dp
                )
            }
        ) {
            val i1Team = if (match.initialBattingTeamId == teamA.id) teamA else teamB
            Tab(selected = viewedInnings == 1, onClick = { onInningsChange(1) }) {
                Text("${i1Team.name} Innings", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            if (match.currentInnings == 2 || match.status == MatchStatus.COMPLETED) {
                val i2Team = if (match.initialBattingTeamId == teamA.id) teamB else teamA
                Tab(selected = viewedInnings == 2, onClick = { onInningsChange(2) }) {
                    Text("${i2Team.name} Innings", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
                        if (viewedInnings == 1) {
                            val i1Team = if (match.initialBattingTeamId == teamA.id) teamA else teamB
                            InningsScorecard(
                                match = match,
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
                                maxBalls = match.oversPerInnings * 6,
                                numericRuns = match.innings1Data?.runs ?: (if (match.currentInnings == 1) match.totalRuns else 0),
                                numericBalls = match.innings1Data?.balls ?: (if (match.currentInnings == 1) match.totalBalls else 0),
                                durationMinutes = match.innings1Data?.durationMinutes ?: (
                                    if (match.currentInnings == 1) {
                                        match.startTimeMillis?.let { ((System.currentTimeMillis() - it) / 60000).toInt() } ?: 0
                                    } else 0
                                ),
                                battingOrder = (match.innings1Data?.battingOrder ?: match.battingOrder) ?: emptyList()
                            )
                        } else {
                            val i2Team = if (match.initialBattingTeamId == teamA.id) teamB else teamA
                            val i2Duration = run {
                                val start = match.innings2StartTimeMillis ?: match.startTimeMillis ?: System.currentTimeMillis()
                                val end = match.endTimeMillis ?: System.currentTimeMillis()
                                ((end - start) / 60000).toInt().coerceAtLeast(0)
                            }
                            InningsScorecard(
                                match = match,
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
                                maxBalls = match.oversPerInnings * 6,
                                numericRuns = if (match.currentInnings == 2) match.totalRuns else 0,
                                numericBalls = if (match.currentInnings == 2) match.totalBalls else 0,
                                durationMinutes = i2Duration,
                                battingOrder = (if (match.currentInnings == 2) match.battingOrder else emptyList()) ?: emptyList()
                            )
                        }
                        CardBranding()
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun InningsScorecard(
    match: Match,
    team: Team,
    strikerId: String?,
    nonStrikerId: String?,
    wicketHistory: List<WicketRecord>?,
    wideCount: Int,
    noBallCount: Int,
    byeCount: Int,
    legByeCount: Int,
    totalScore: String?,
    totalOvers: String?,
    bowlingTeamPlayers: List<Player>?,
    maxBalls: Int,
    numericRuns: Int,
    numericBalls: Int,
    durationMinutes: Int,
    battingOrder: List<String>?
) {
    val safeBattingOrder = battingOrder ?: emptyList()
    val safeWicketHistory = wicketHistory ?: emptyList()
    val safeBowlingPlayers = bowlingTeamPlayers ?: emptyList()

    Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
        Box(
            modifier = Modifier.fillMaxWidth().background(Color(0xFFE3F2FD)).padding(16.dp)
        ) {
            Column {
                Text(text = "🏏 ${team.name} 🏆", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color(0xFF003366))
                Text(text = "($maxBalls balls maximum)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
        
        BattingTable(match, team.players, strikerId, nonStrikerId, safeBowlingPlayers, safeBattingOrder)
        
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
            val crr = if (numericBalls > 0) (numericRuns.toDouble() / (numericBalls / 6.0 + (numericBalls % 6) / 6.0)) else 0.0
            Column {
                Text("Total", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "$totalOvers Ov (RR: ${String.format(java.util.Locale.US, "%.2f", crr)}, $durationMinutes Mins)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = totalScore ?: "0/0",
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        HorizontalDivider(thickness = 1.dp, color = Color.LightGray)

        val didNotBat = team.players.filter { it.battingStats.balls == 0 && !it.battingStats.isOut && it.id != strikerId && it.id != nonStrikerId }
        if (didNotBat.isNotEmpty()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("DID NOT BAT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.Gray, fontSize = 12.sp)
                Text(text = didNotBat.joinToString { 
                    val style = (it.battingStyle ?: BattingStyle.RHB).name
                    val roleSuffix = if (it.isCaptain) " (c)" else if (it.isViceCaptain) " (vc)" else ""
                    (if (it.isJoker) "${it.name} 🃏" else it.name) + roleSuffix + " ($style)"
                }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
        }

        if (safeWicketHistory.isNotEmpty()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("☝️ FALL OF WICKETS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.Gray)
                Text(text = safeWicketHistory.joinToString { "☝️ ${it.wicketNumber}-${it.totalRuns} (${it.batterName}, ${it.over} ov) 🚩" }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp), lineHeight = 20.sp)
            }
            HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
        }

        Spacer(modifier = Modifier.height(16.dp))
        BowlingTable(match, safeBowlingPlayers)
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun BattingTable(match: Match, players: List<Player>, strikerId: String?, nonStrikerId: String?, bowlers: List<Player>, battingOrder: List<String>?) {
    val safeOrder = battingOrder ?: emptyList()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFFF5F5F5)).padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("BATTING", modifier = Modifier.weight(4f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 12.sp)
            Text("R", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = Color.Gray, fontSize = 12.sp)
            Text("B", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = Color.Gray, fontSize = 12.sp)
            Text("4s", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = Color.Gray, fontSize = 12.sp)
            Text("6s", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = Color.Gray, fontSize = 12.sp)
            Text("SR", modifier = Modifier.width(45.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = Color.Gray, fontSize = 12.sp)
        }
        
        val sortedPlayers = try {
            if (safeOrder.isEmpty()) players else players.sortedBy { player ->
                val pId = player.id
                val idx = if (pId != null) safeOrder.indexOf(pId) else -1
                if (idx == -1) Int.MAX_VALUE else idx
            }
        } catch (e: Exception) {
            players
        }
        
        sortedPlayers.forEach { player ->
            val isCurrentBatter = player.id == strikerId || player.id == nonStrikerId
            if (player.battingStats.balls > 0 || player.battingStats.isOut || isCurrentBatter) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(4f)) {
                        val isCaptain = player.isCaptain
                        val isViceCaptain = player.isViceCaptain
                        val isWK = player.id == match.teamAWicketKeeperId || player.id == match.teamBWicketKeeperId
                        val isRH = player.battingStats.isRetiredHurt
                        val isJoker = player.isJoker
                        val bStyle = (player.battingStyle ?: BattingStyle.RHB).name
                        val roleSuffix = if (isCaptain) " (c)" else if (isViceCaptain) " (vc)" else ""
                        Text(
                            text = "🏏 " + player.name + " ($bStyle)" + (if (isJoker) " 🃏" else "") + roleSuffix + (if (isWK) " 🧤" else "") + (if (isCurrentBatter && !player.battingStats.isOut) "*" else ""),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (player.battingStats.isOut) Color.Gray else Color.Black,
                            fontSize = 14.sp
                        )
                        val dbId = player.battingStats.dismissalBowlerId
                        val dismissalBowlerName = recoverName(dbId, match, "Bowler")
                        val dfId = player.battingStats.dismissalFielderId
                        val dismissalFielderName = recoverName(dfId, match, "Fielder")
                        
                        val type = player.battingStats.wicketType
                        val dismissalText = when (type) {
                            WicketType.BOWLED -> "b $dismissalBowlerName"
                            WicketType.CAUGHT -> "c $dismissalFielderName b $dismissalBowlerName"
                            WicketType.LBW -> "lbw b $dismissalBowlerName"
                            WicketType.STUMPED -> "st $dismissalFielderName b $dismissalBowlerName"
                            WicketType.RUN_OUT -> "run out ($dismissalFielderName)"
                            WicketType.HIT_WICKET -> "hit wicket b $dismissalBowlerName"
                            WicketType.RETIRED_HURT -> "retired hurt"
                            WicketType.NONE -> if (isCurrentBatter) "not out" else if (isRH) "retired hurt" else ""
                            else -> "out"
                        }
                        
                        if (dismissalText.isNotEmpty()) {
                            Text(text = dismissalText, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    }
                    Text("${player.battingStats.runs}", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, fontSize = 13.sp)
                    Text("${player.battingStats.balls}", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, fontSize = 13.sp)
                    Text("${player.battingStats.fours}", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, fontSize = 13.sp)
                    Text("${player.battingStats.sixes}", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, fontSize = 13.sp)
                    Text(String.format(java.util.Locale.US, "%.1f", player.battingStats.strikeRate), modifier = Modifier.width(45.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End, fontSize = 13.sp)
                }
                HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFF0F0F0))
            }
        }
    }
}

@Composable
fun BowlingTable(match: Match, players: List<Player>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF5F5F5))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("BOWLING", modifier = Modifier.weight(3f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 12.sp)
            Text("O", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = Color.Gray, fontSize = 12.sp)
            Text("M", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = Color.Gray, fontSize = 12.sp)
            Text("NB", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = Color.Gray, fontSize = 12.sp)
            Text("WD", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = Color.Gray, fontSize = 12.sp)
            Text("R", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = Color.Gray, fontSize = 12.sp)
            Text("W", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = Color.Gray, fontSize = 12.sp)
            Text("ER", modifier = Modifier.width(45.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = Color.Gray, fontSize = 12.sp)
        }
        players.forEach { player ->
            if (player.bowlingStats.overs > 0 || player.bowlingStats.balls > 0 || player.id == match.currentBowlerId) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(3f), verticalAlignment = Alignment.CenterVertically) {
                        val isCaptain = player.isCaptain
                        val isViceCaptain = player.isViceCaptain
                        val roleSuffix = if (isCaptain) " (c)" else if (isViceCaptain) " (vc)" else ""
                        Text("⚾ " + player.name + (if (player.isJoker) " 🃏" else "") + roleSuffix, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        val isWK = player.id == match.teamAWicketKeeperId || player.id == match.teamBWicketKeeperId
                        if (isWK) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("🧤", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(player.bowlingStats.formattedOvers, modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, fontSize = 13.sp)
                    Text("${player.bowlingStats.maidens}", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, fontSize = 13.sp)
                    Text("${player.bowlingStats.noBalls}", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, fontSize = 13.sp)
                    Text("${player.bowlingStats.wides}", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, fontSize = 13.sp)
                    Text("${player.bowlingStats.runsConceded}", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, fontSize = 13.sp)
                    Text("${player.bowlingStats.wickets}", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    Text(String.format(java.util.Locale.US, "%.2f", player.bowlingStats.economy), modifier = Modifier.width(45.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End, fontSize = 13.sp)
                }
                HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFF0F0F0))
            }
        }
    }
}

@Composable
fun OversTab(match: Match, viewModel: ScoringViewModel, graphicsLayer: GraphicsLayer) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerDropdown(selectedId: String?, players: List<Player>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedPlayer = players.find { it.id == selectedId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedPlayer?.name ?: "Select Player",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            players.forEach { player ->
                DropdownMenuItem(
                    text = { Text(player.name) },
                    onClick = {
                        onSelect(player.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun CricketBallIcon(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(12.dp),
        shape = CircleShape,
        color = Color(0xFFC62828), // Cricket Red
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth().height(1.dp),
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun MatchSummaryCard(match: Match) {
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
fun MatchSettingsDialog(match: Match, viewModel: ScoringViewModel, onDismiss: (() -> Unit)? = null) {
    val currentOvers = match.oversPerInnings
    val currentMaxOvers = match.maxOversPerBowler
    val currentQuotaCount = match.quotaBowlersCount
    val currentQuotaLimit = match.quotaMaxOvers

    var oversText by remember { mutableStateOf(currentOvers.toString()) }
    var maxOversText by remember { mutableStateOf(currentMaxOvers?.toString() ?: "") }
    var quotaCountText by remember { mutableStateOf(currentQuotaCount?.toString() ?: "") }
    var quotaLimitText by remember { mutableStateOf(currentQuotaLimit?.toString() ?: "") }
    
    // v2.30.1: Unified Toss integration
    var tempTossWinnerId by remember(match.id) { mutableStateOf<String?>(null) }
    var tempTossDecision by remember(match.id) { mutableStateOf<String?>(null) }
    var showFlipDialog by remember { mutableStateOf(false) }

    val onActionDismiss = {
        if (onDismiss != null) onDismiss()
        else viewModel.cancelPendingAction()
    }

    AlertDialog(
        onDismissRequest = onActionDismiss,
        title = { Text("Match Settings", fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text("Overs per Innings", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = oversText,
                    onValueChange = { if (it.all { char -> char.isDigit() }) oversText = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Max Overs per Bowler", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = maxOversText,
                    onValueChange = { if (it.all { char -> char.isDigit() }) maxOversText = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("No limit") }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Advanced Bowler Restrictions", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quotaCountText,
                        onValueChange = { if (it.all { char -> char.isDigit() }) quotaCountText = it },
                        label = { Text("Number of Bowlers") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text("e.g. 3") }
                    )
                    OutlinedTextField(
                        value = quotaLimitText,
                        onValueChange = { if (it.all { char -> char.isDigit() }) quotaLimitText = it },
                        label = { Text("Per Bowler Limit") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text("e.g. 3") }
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Toss", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Button(onClick = { showFlipDialog = true }, shape = RoundedCornerShape(8.dp)) {
                        Text("FLIP COIN 🪙")
                    }
                }
                
                if (showFlipDialog) {
                    CoinFlipDialog(
                        match = match,
                        onResult = { winnerId, decision ->
                            tempTossWinnerId = winnerId
                            tempTossDecision = decision
                            showFlipDialog = false
                        },
                        onDismiss = { showFlipDialog = false }
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text("Winner", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = tempTossWinnerId == match.teamA.id, onClick = { tempTossWinnerId = match.teamA.id }, label = { Text(match.teamA.name.uppercase(), fontWeight = FontWeight.Black) })
                    FilterChip(selected = tempTossWinnerId == match.teamB.id, onClick = { tempTossWinnerId = match.teamB.id }, label = { Text(match.teamB.name.uppercase(), fontWeight = FontWeight.Black) })
                }

                Spacer(Modifier.height(8.dp))
                Text("Decision", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = tempTossDecision == "BAT", onClick = { tempTossDecision = "BAT" }, label = { Text("Bat First") })
                    FilterChip(selected = tempTossDecision == "BOWL", onClick = { tempTossDecision = "BOWL" }, label = { Text("Bowl First") })
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dark Mode", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    val isDarkMode by viewModel.isDarkMode.collectAsState()
                    Switch(
                        checked = isDarkMode ?: isSystemInDarkTheme(),
                        onCheckedChange = { viewModel.toggleTheme(it) }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val o = oversText.toIntOrNull() ?: currentOvers
                    val mO = maxOversText.toIntOrNull()
                    val qC = quotaCountText.toIntOrNull()
                    val qL = quotaLimitText.toIntOrNull()
                    
                    if (tempTossWinnerId != null && tempTossDecision != null) {
                        viewModel.handleToss(tempTossWinnerId!!, tempTossDecision!!)
                    }
                    
                    viewModel.updateMatchSettings(o, mO, qC, qL)
                    // v2.31.5: Removed onActionDismiss() from confirm button. 
                    // Let the engine automatically dismiss the dialog via state change. 🏏🚀⚖️🏅
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("SAVE & START SCORING", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (onDismiss != null) onDismiss()
                else viewModel.cancelPendingAction()
            }) {
                Text("CANCEL")
            }
        }
    )
}

@Composable
fun CoinFlipDialog(match: Match, onResult: (String, String) -> Unit, onDismiss: () -> Unit) {
    var winnerId by remember { mutableStateOf<String?>(null) }
    var decision by remember { mutableStateOf<String?>(null) }
    
    // v2.30.0: Interactive Coin Toss State 🏏🚀⚖️🏅
    var isFlipping by remember { mutableStateOf(false) }
    var coinResult by remember { mutableStateOf<String?>(null) } // "HEADS" or "TAILS"
    var callerChoice by remember { mutableStateOf<String?>(null) }
    var tossCallerId by remember { mutableStateOf(match.teamA.id) } // v2.31.0: Added Toss Caller selection 🏏🚀⚖️🏅
    val rotation = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Match Toss", fontWeight = FontWeight.Black) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Coin Flip Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Toss Call by", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(
                                    selected = tossCallerId == match.teamA.id,
                                    onClick = { if (!isFlipping && coinResult == null) tossCallerId = match.teamA.id },
                                    label = { Text(match.teamA.name.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = if(tossCallerId == match.teamA.id) FontWeight.Black else FontWeight.Normal) }
                                )
                                FilterChip(
                                    selected = tossCallerId == match.teamB.id,
                                    onClick = { if (!isFlipping && coinResult == null) tossCallerId = match.teamB.id },
                                    label = { Text(match.teamB.name.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = if(tossCallerId == match.teamB.id) FontWeight.Black else FontWeight.Normal) }
                                )
                            }
                        }
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("HEADS", "TAILS").forEach { choice ->
                                    FilterChip(
                                        selected = callerChoice == choice,
                                        onClick = { if (!isFlipping) callerChoice = choice },
                                        label = { Text(choice, style = MaterialTheme.typography.labelSmall) },
                                        enabled = !isFlipping && coinResult == null
                                    )
                                }
                            }

                            // Smaller Coin
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .graphicsLayer {
                                        rotationY = rotation.value
                                        cameraDistance = 12f * density
                                    }
                                    .background(Color(0xFFFFD700), CircleShape)
                                    .border(3.dp, Color(0xFFDAA520), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                val side = if ((rotation.value / 180).toInt() % 2 == 0) "C" else "S"
                                Text(
                                    text = if (coinResult != null) coinResult!!.take(1) else side,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF8B4513)
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Button(
                            onClick = {
                                scope.launch {
                                    isFlipping = true
                                    coinResult = null
                                    winnerId = null
                                    
                                    // Animate multiple rotations
                                    val targetRotation = 180f * 10 + (if (java.util.Random().nextBoolean()) 0f else 180f)
                                    rotation.animateTo(
                                        targetValue = targetRotation,
                                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 1500, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                                    )
                                    
                                    val isHeads = (targetRotation / 180).toInt() % 2 == 0
                                    val result = if (isHeads) "HEADS" else "TAILS"
                                    coinResult = result
                                    isFlipping = false
                                    
                                    // Auto-assign winner based on call
                                    if (callerChoice != null) {
                                        val otherTeamId = if (tossCallerId == match.teamA.id) match.teamB.id else match.teamA.id
                                        winnerId = if (callerChoice == result) tossCallerId else otherTeamId
                                    }
                                }
                            },
                            enabled = !isFlipping && callerChoice != null && coinResult == null,
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (isFlipping) "FLIPPING..." else "FLIP COIN", style = MaterialTheme.typography.labelLarge)
                        }
                        
                        if (coinResult != null) {
                            val winnerName = if (winnerId == match.teamA.id) match.teamA.name else match.teamB.name
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("Result: $coinResult", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                                    Text("$winnerName won!", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), style = MaterialTheme.typography.labelSmall)
                                }
                                TextButton(onClick = { 
                                    coinResult = null; callerChoice = null; winnerId = null;
                                    scope.launch { rotation.snapTo(0f) }
                                }) {
                                    Text("RE-FLIP", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.5f))

                Text("Toss Winner", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = winnerId == match.teamA.id,
                        onClick = { if (!isFlipping) winnerId = match.teamA.id },
                        label = { Text(match.teamA.name.uppercase(), fontWeight = FontWeight.Black) },
                        enabled = !isFlipping
                    )
                    FilterChip(
                        selected = winnerId == match.teamB.id,
                        onClick = { if (!isFlipping) winnerId = match.teamB.id },
                        label = { Text(match.teamB.name.uppercase(), fontWeight = FontWeight.Black) },
                        enabled = !isFlipping
                    )
                }
                
                // 3. Decision
                if (winnerId != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("Decision", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { decision = "BAT" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (decision == "BAT") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (decision == "BAT") Color.White else Color.Black
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("BAT FIRST")
                        }
                        Button(
                            onClick = { decision = "BOWL" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (decision == "BOWL") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (decision == "BOWL") Color.White else Color.Black
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("BOWL FIRST")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    winnerId?.let { w -> 
                        decision?.let { d -> 
                            onResult(w, d)
                        } 
                    } 
                },
                enabled = winnerId != null && decision != null && !isFlipping,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("CONFIRM TOSS", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL") }
        }
    )
}

@Composable
fun MatchCelebrationDialog(match: Match, onNavigateToDashboard: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("🎊 CHAMPIONS! 🏆", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                val winner = if (match.winnerId == match.teamA.id) match.teamA.name else if (match.winnerId == match.teamB.id) match.teamB.name else "Match Drawn"
                Text(
                    text = if (match.winnerId != null) "$winner won the match!" else "The match ended in a draw.",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text("Great game played by both teams! 🏏🔥", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("FINAL SCORE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("${match.totalRuns}/${match.totalWickets} (${match.totalBalls/6}.${match.totalBalls%6} Ov)", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onNavigateToDashboard, modifier = Modifier.fillMaxWidth()) {
                Text("EXIT")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("VIEW SCORECARD")
            }
        }
    )
}

@Composable
fun MotmSection(match: Match) {
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
fun MatchForecasterSection(match: Match) {
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
fun StatsTab(match: Match, graphicsLayer: GraphicsLayer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var shareTrigger by remember { mutableIntStateOf(0) }
    val teamA = match.teamA; val teamB = match.teamB
    val i1Team = if (match.initialBattingTeamId == teamA.id) teamA else teamB
    val i2Team = if (match.initialBattingTeamId == teamA.id) teamB else teamA

    val splitIdx = match.innings1Data?.recordedBallsCount ?: match.ballHistory.size
    val i1Balls = match.ballHistory.take(splitIdx)
    val i2Balls = match.ballHistory.drop(splitIdx)

    val i1Stats = calculateInningsStats(i1Balls)
    val i2Stats = calculateInningsStats(i2Balls)

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Button(
            onClick = {
                scope.launch {
                    shareTrigger++
                    kotlinx.coroutines.delay(300.milliseconds)
                    shareComposableScreenshot(context, graphicsLayer, "full_match_stats")
                }
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF003366), contentColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("SHARE FULL MATCH STATS", fontWeight = FontWeight.Black)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
                    Column(modifier = Modifier.fillMaxWidth().background(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        MatchSummaryCard(match)
                        MotmSection(match) // v2.26.61: Added Man of the Match statistics 🏏🚀⚖️🏅
                        ScoringBreakdownCard(match, i1Stats, i2Stats)
                        BestPerformancesBatters(teamA, teamB)
                        BestPerformancesBowlers(teamA, teamB)
                        PartnershipsSection(match, i1Team, i2Team)
                        CardBranding()
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun LiveTab(
    match: Match,
    viewModel: ScoringViewModel,
    onShowWicket: () -> Unit,
    onShowExtraRuns: (ExtrasType) -> Unit,
    onShowOtherRuns: () -> Unit,
    onShowRetireHurt: () -> Unit
) {
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
        item { ScoreCard(match) }
        item { PlayerStatsSection(match, viewModel) }
        
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
                    match = match,
                    viewModel = viewModel,
                    onShowWicket = onShowWicket,
                    onShowExtraRuns = onShowExtraRuns,
                    onShowOtherRuns = onShowOtherRuns,
                    onShowRetireHurt = onShowRetireHurt
                )
            }
        }

        if (match.status == MatchStatus.LIVE) {
            item { MatchForecasterSection(match) }
        }
    }
}

@Composable
fun InningsOverOverlay(match: Match, viewModel: ScoringViewModel) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Innings Completed", fontWeight = FontWeight.Black) },
        text = {
            val teamName = if (match.initialBattingTeamId == match.teamA.id) match.teamA.name else match.teamB.name
            val i1Data = match.innings1Data
            val runs = i1Data?.runs ?: 0
            val wickets = i1Data?.wickets ?: 0
            val balls = i1Data?.balls ?: 0
            val target = match.target ?: (runs + 1)
            
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "$teamName Score",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray
                )
                Text(
                    text = "$runs/$wickets",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "(${balls / 6}.${balls % 6} Overs)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TARGET", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("$target", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { viewModel.startSecondInnings() }, modifier = Modifier.fillMaxWidth()) {
                Text("START 2ND INNINGS")
            }
        }
    )
}

@Composable
fun DroppedCatchRunsOverlay(viewModel: ScoringViewModel) {
    AlertDialog(
        onDismissRequest = { viewModel.cancelPendingAction() },
        title = { 
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Runs on Dropped Catch", fontWeight = FontWeight.Black)
                IconButton(onClick = { viewModel.cancelPendingAction() }) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                }
            }
        },
        text = {
            Column {
                Text("Select runs taken by batters during the dropped catch.")
                Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 1, 2, 3).forEach { r ->
                            Button(onClick = { viewModel.handleRunsForDroppedCatch(r, true) }, modifier = Modifier.weight(1f)) { Text("$r") }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(4, 6).forEach { r ->
                            val label = if (r == 4) "4 💥" else "6 💥"
                            Button(onClick = { viewModel.handleRunsForDroppedCatch(r, true) }, modifier = Modifier.weight(1f)) { Text(label) }
                        }
                    }
                }
            }
        },
        confirmButton = { }
    )
}

@Composable
fun RunOutRunsOverlay(viewModel: ScoringViewModel) {
    val match by viewModel.matchState.collectAsState()
    var selectedRuns by remember { mutableIntStateOf(0) }
    var selectedVictimId by remember { mutableStateOf<String?>(null) }
    var hadCrossed by remember { mutableStateOf(false) }
    var step by remember { mutableIntStateOf(1) } // 1: Runs, 2: Victim, 3: Crossing, 4: Reason

    val batTeam = if (match?.battingTeamId == match?.teamA?.id) match?.teamA else match?.teamB
    val striker = batTeam?.players?.find { it.id == match?.strikerId }
    val nonStriker = batTeam?.players?.find { it.id == match?.nonStrikerId }

    if (step == 1) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelPendingAction() },
            title = { 
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Runs before Run-Out", fontWeight = FontWeight.Black)
                    IconButton(onClick = { viewModel.cancelPendingAction() }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                }
            },
            text = {
                Column {
                    Text("Select runs completed before the wicket.")
                    Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val runsList = listOf(0, 1, 2, 3, 4, 5, 6)
                        runsList.chunked(4).forEach { rowRuns ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowRuns.forEach { r ->
                                    Button(onClick = { 
                                        selectedRuns = r
                                        step = 2
                                    }, modifier = Modifier.weight(1f)) { Text("$r") }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { }
        )
    } else if (step == 2) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelPendingAction() },
            title = { 
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Who was Run Out?", fontWeight = FontWeight.Black)
                    IconButton(onClick = { viewModel.cancelPendingAction() }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    striker?.let {
                        Button(onClick = { 
                            selectedVictimId = it.id
                            // v2.27.1: Optimize UI Friction. Skip crossing if striker out on 0 runs. 🏏🚀⚖️🏅
                            if (selectedRuns == 0) {
                                hadCrossed = false
                                step = 4
                            } else {
                                step = 3
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Striker: ${it.name}")
                        }
                    }
                    nonStriker?.let {
                        Button(onClick = { selectedVictimId = it.id; step = 3 }, modifier = Modifier.fillMaxWidth()) {
                            Text("Non-Striker: ${it.name}")
                        }
                    }
                }
            },
            confirmButton = { }
        )
    } else if (step == 3) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelPendingAction() },
            title = { 
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Had they crossed?", fontWeight = FontWeight.Black)
                    IconButton(onClick = { viewModel.cancelPendingAction() }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Had the batters crossed paths for the attempted run (Run ${selectedRuns + 1})?")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { hadCrossed = true; step = 4 }, modifier = Modifier.weight(1f)) {
                            Text("YES")
                        }
                        Button(onClick = { hadCrossed = false; step = 4 }, modifier = Modifier.weight(1f)) {
                            Text("NO")
                        }
                    }
                }
            },
            confirmButton = { }
        )
    } else {
        AlertDialog(
            onDismissRequest = { viewModel.cancelPendingAction() },
            title = { 
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Dismissal Reason", fontWeight = FontWeight.Black)
                    IconButton(onClick = { viewModel.cancelPendingAction() }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                }
            },
            text = {
                val reasons = listOf("Quick single", "Risky second run", "Miscommunication", "Direct hit", "Attempting 3rd run")
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(reasons) { reason ->
                        TextButton(
                            onClick = { 
                                viewModel.handleRunOutWicket(selectedRuns, selectedVictimId!!, hadCrossed, reason) 
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(reason, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                        }
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                    item {
                        TextButton(
                            onClick = { 
                                viewModel.handleRunOutWicket(selectedRuns, selectedVictimId!!, hadCrossed, "Other") 
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Other", textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = { }
        )
    }
}

@Composable
fun PlayerSelectionOverlay(match: Match, viewModel: ScoringViewModel) {
    val context = LocalContext.current
    val title = when (match.pendingAction ?: PendingAction.NONE) {
        PendingAction.SELECT_STRIKER -> "SELECT STRIKER"
        PendingAction.SELECT_NON_STRIKER -> "SELECT NON-STRIKER"
        PendingAction.SELECT_BOWLER -> "SELECT BOWLER"
        PendingAction.REPLACE_STRIKER -> "REPLACE STRIKER"
        PendingAction.REPLACE_NON_STRIKER -> "REPLACE NON-STRIKER"
        PendingAction.REPLACE_BOWLER -> "REPLACE BOWLER"
        PendingAction.SELECT_FIELDER -> "SELECT FIELDER"
        PendingAction.SELECT_FIELDER_DROPPED_CATCH -> "DROPPED CATCH BY?"
        PendingAction.SELECT_WK_A -> "SELECT WK (${match.teamA.name})"
        PendingAction.SELECT_WK_B -> "SELECT WK (${match.teamB.name})"
        else -> "SELECT PLAYER"
    }

    val team = when (match.pendingAction) {
        PendingAction.SELECT_WK_A -> match.teamA
        PendingAction.SELECT_WK_B -> match.teamB
        PendingAction.SELECT_BOWLER, PendingAction.REPLACE_BOWLER, 
        PendingAction.SELECT_FIELDER, PendingAction.SELECT_FIELDER_DROPPED_CATCH -> 
            if (match.battingTeamId == match.teamA.id) match.teamB else match.teamA
        else -> 
            // v2.31.5: Safer team selection logic 🏏🚀⚖️🏅
            if (match.battingTeamId == match.teamA.id) match.teamA 
            else if (match.battingTeamId == match.teamB.id) match.teamB
            else match.teamA // Fallback to Team A if batting team ID is not found or empty
    }

    AlertDialog(
        onDismissRequest = { viewModel.cancelPendingAction() },
        title = { 
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Black)
                IconButton(onClick = { viewModel.cancelPendingAction() }) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                }
            }
        },
        text = {
            val availablePlayers = team.players.filter { player ->
                when (match.pendingAction ?: PendingAction.NONE) {
                    PendingAction.SELECT_STRIKER, PendingAction.SELECT_NON_STRIKER,
                    PendingAction.REPLACE_STRIKER, PendingAction.REPLACE_NON_STRIKER ->
                        !player.battingStats.isOut && 
                        player.id != match.strikerId && player.id != match.nonStrikerId
                    PendingAction.SELECT_BOWLER, PendingAction.REPLACE_BOWLER ->
                        player.id != match.lastBowlerId
                    else -> true
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                if (availablePlayers.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("No players found in this team.", textAlign = TextAlign.Center, color = Color.Gray)
                        }
                    }
                } else {
                    items(availablePlayers) { player ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (match.pendingAction == PendingAction.SELECT_FIELDER || match.pendingAction == PendingAction.SELECT_FIELDER_DROPPED_CATCH) {
                                        viewModel.selectFielder(player.id)
                                    } else {
                                        viewModel.assignPlayerToAction(player.id)
                                    }
                                    Toast
                                        .makeText(context, "${player.name} selected! ✅", Toast.LENGTH_SHORT)
                                        .show()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(player.name.take(1).uppercase(), fontWeight = FontWeight.Black)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                val isCaptain = player.isCaptain
                                val isWK = player.id == match.teamAWicketKeeperId || player.id == match.teamBWicketKeeperId
                                val roleSuffix = if (isCaptain) " (c)" else if (player.isViceCaptain) " (vc)" else ""
                                Text(player.name + roleSuffix + (if (isWK) " 🧤" else ""), fontWeight = FontWeight.Bold)
                                
                                val action = match.pendingAction ?: PendingAction.NONE
                                val isBowlerAction = action == PendingAction.SELECT_BOWLER || action == PendingAction.REPLACE_BOWLER
                                val isFielderAction = action == PendingAction.SELECT_FIELDER || action == PendingAction.SELECT_FIELDER_DROPPED_CATCH
                                
                                val bStyle = (player.battingStyle ?: BattingStyle.RHB).name
                                
                                val subText = when {
                                    isBowlerAction -> "Bowler"
                                    isFielderAction -> "Fielder"
                                    else -> "Batting: $bStyle"
                                }
                                
                                Text(
                                    text = subText, 
                                    style = MaterialTheme.typography.bodyMedium, 
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFielderAction) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.5f))
                    }
                }
                if (match.pendingAction == PendingAction.SELECT_BOWLER || match.pendingAction == PendingAction.REPLACE_BOWLER) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.forceChangeBowler() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("SAME BOWLER", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { viewModel.cancelPendingAction() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f))
                    ) {
                        Text("CANCEL", fontWeight = FontWeight.Black, color = Color.Red)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        },
        confirmButton = { }
    )
}



@Composable
fun ManageSquadsOverlay(match: Match, viewModel: ScoringViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Squads", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SquadList(match, match.teamA, "Team A: ${match.teamA.name}", viewModel)
                SquadList(match, match.teamB, "Team B: ${match.teamB.name}", viewModel)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("CLOSE") }
        }
    )
}

@Composable
fun SquadList(match: Match, team: Team, title: String, viewModel: ScoringViewModel) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<Player?>(null) }
    var newPlayerName by remember { mutableStateOf("") }
    val context = LocalContext.current
    
    val globalPlayers by GlobalPlayerRepository.players.collectAsState()

    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            if (match.status != MatchStatus.COMPLETED) {
                IconButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, contentDescription = null) }
            }
        }
        LazyColumn(modifier = Modifier.height(150.dp)) {
            items(team.players) { player ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    val isCaptain = player.isCaptain
                    val isViceCaptain = player.isViceCaptain
                    val isWK = player.id == match.teamAWicketKeeperId || player.id == match.teamBWicketKeeperId
                    val bStyle = (player.battingStyle ?: BattingStyle.RHB).name
                    val roleSuffix = if (isCaptain) " (c)" else if (isViceCaptain) " (vc)" else ""
                    Text(
                        text = player.name + " ($bStyle)" + (if (player.isJoker) " 🃏" else "") + roleSuffix + (if (isWK) " 🧤" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Row {
                        if (match.status != MatchStatus.COMPLETED) {
                            IconButton(onClick = { showEditDialog = player }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { viewModel.deletePlayerFromMatch(player.id) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Red, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
        
        if (showEditDialog != null) {
            val player = showEditDialog!!
            var editName by remember(player.id) { mutableStateOf(player.name) }
            var editStyle by remember(player.id) { mutableStateOf(player.battingStyle ?: BattingStyle.RHB) }
            var isCaptain by remember(player.id) { mutableStateOf(player.isCaptain) }
            var isViceCaptain by remember(player.id) { mutableStateOf(player.isViceCaptain) }

            AlertDialog(
                onDismissRequest = { showEditDialog = null },
                title = { Text("Edit Player") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                        Text("Batting Style", fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BattingStyle.entries.forEach { style ->
                                FilterChip(selected = editStyle == style, onClick = { editStyle = style }, label = { Text(style.name) }, modifier = Modifier.weight(1f))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isCaptain, onCheckedChange = { isCaptain = it; if (it) isViceCaptain = false })
                            Text("Captain (c)")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isViceCaptain, onCheckedChange = { isViceCaptain = it; if (it) isCaptain = false })
                            Text("Vice Captain (vc)")
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.updatePlayerInMatch(player.id, editName, editStyle, isCaptain, isViceCaptain)
                        showEditDialog = null
                    }) { Text("UPDATE") }
                },
                dismissButton = {
                    TextButton(onClick = { showEditDialog = null }) { Text("CANCEL") }
                }
            )
        }

        if (showAddDialog) {
            var selectedStyle by remember { mutableStateOf(BattingStyle.RHB) }
            var showGlobalPlaylist by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text(if (showGlobalPlaylist) "Pick from Playlist" else "Add Player to ${team.name}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (!showGlobalPlaylist) {
                            OutlinedTextField(
                                value = newPlayerName, 
                                onValueChange = { newPlayerName = it }, 
                                label = { Text("Name") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("Batting Style", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BattingStyle.entries.forEach { style ->
                                    FilterChip(
                                        selected = selectedStyle == style,
                                        onClick = { selectedStyle = style },
                                        label = { Text(style.name) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            
                            if (globalPlayers.isNotEmpty()) {
                                TextButton(onClick = { showGlobalPlaylist = true }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.PersonSearch, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("PICK FROM GLOBAL PLAYLIST")
                                }
                            }
                        } else {
                            Text("Select players to add:", fontWeight = FontWeight.Bold)
                            val existingInBoth = (match.teamA.players + match.teamB.players).map { it.name.lowercase() }
                            val filteredGlobal = globalPlayers.filter { gp -> gp.name.lowercase() !in existingInBoth }
                            val selectedPlayers = remember { mutableStateListOf<Player>() }

                            LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                                if (filteredGlobal.isEmpty()) {
                                    item { Text("No new players to add.", color = Color.Gray, modifier = Modifier.padding(16.dp)) }
                                } else {
                                    items(filteredGlobal) { gp ->
                                        val isSelected = selectedPlayers.contains(gp)
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                if (isSelected) selectedPlayers.remove(gp) else selectedPlayers.add(gp)
                                            }.padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(checked = isSelected, onCheckedChange = {
                                                if (it) selectedPlayers.add(gp) else selectedPlayers.remove(gp)
                                            })
                                            Text(gp.name + " (${gp.battingStyle})")
                                        }
                                        HorizontalDivider(thickness = 0.5.dp)
                                    }
                                }
                            }
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (selectedPlayers.isNotEmpty()) {
                                            viewModel.addGlobalPlayersToMatch(context, selectedPlayers.toList())
                                            showAddDialog = false
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = selectedPlayers.isNotEmpty()
                                ) {
                                    Text("ADD SELECTED (${selectedPlayers.size})")
                                }
                                TextButton(onClick = { showGlobalPlaylist = false }) { Text("BACK") }
                            }
                        }
                    }
                },
                confirmButton = {
                    if (!showGlobalPlaylist) {
                        Button(onClick = {
                            if (newPlayerName.isNotBlank()) {
                                viewModel.addNewPlayerToMatch(context, newPlayerName, selectedStyle)
                                newPlayerName = ""
                                showAddDialog = false
                            }
                        }) { Text("ADD") }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) { Text("CANCEL") }
                }
            )
        }
    }
}

@Composable
fun ScoreCard(match: Match) {
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
fun PlayerStatsSection(match: Match, viewModel: ScoringViewModel) {
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
    match: Match,
    viewModel: ScoringViewModel,
    onShowWicket: () -> Unit,
    onShowExtraRuns: (ExtrasType) -> Unit,
    onShowOtherRuns: () -> Unit,
    onShowRetireHurt: () -> Unit
) {
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

@Composable
fun RunButton(
    runs: Int,
    modifier: Modifier = Modifier,
    label: String? = null,
    containerColor: Color? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.aspectRatio(1f),
        contentPadding = PaddingValues(0.dp),
        shape = CircleShape,
        colors = if (containerColor != null) ButtonDefaults.filledTonalButtonColors(containerColor = containerColor) else ButtonDefaults.filledTonalButtonColors()
    ) {
        Text(label ?: "$runs", fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ExtraButton(label: String, type: ExtrasType, viewModel: ScoringViewModel, onShowExtraRuns: (ExtrasType) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = { 
            if (type == ExtrasType.WIDE) {
                viewModel.handleExtra(ExtrasType.WIDE, 0)
            } else {
                onShowExtraRuns(type)
            }
        },
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

data class InningsStats(
    val singlesRuns: Int = 0,
    val doublesRuns: Int = 0,
    val triplesRuns: Int = 0,
    val foursRuns: Int = 0,
    val sixesRuns: Int = 0,
    val otherBatRuns: Int = 0,
    val dots: Int = 0,
    val extrasRuns: Int = 0,
    val wickets: Int = 0,
    val droppedCatches: Int = 0,
    val wideCount: Int = 0,
    val noBallCount: Int = 0,
    // v2.31.7: Cricinfo Standard Stats 🏏🚀⚖️🏅
    val ppRuns: Int = 0,
    val ppWickets: Int = 0,
    val midRuns: Int = 0,
    val midWickets: Int = 0,
    val finRuns: Int = 0,
    val finWickets: Int = 0,
    val boundaryRuns: Int = 0,
    val dotPercent: Int = 0,
    val totalLegalBalls: Int = 0,
    val hasMid: Boolean = false,
    val hasFin: Boolean = false
)

fun calculateInningsStats(balls: List<Ball>): InningsStats {
    var sR = 0; var dR = 0; var tR = 0; var fR = 0; var siR = 0; var oR = 0; var dots = 0; var exR = 0; var w = 0; var dC = 0
    var wC = 0; var nbC = 0
    
    var ppR = 0; var ppW = 0
    var midR = 0; var midW = 0
    var finR = 0; var finW = 0
    var pB = 0; var lB = 0
    
    balls.forEach { ball ->
        if (ball.isAdjustment) return@forEach

        val ballTotal = ball.runs + ball.extraRuns
        val isW = ball.wicketType != WicketType.NONE && ball.wicketType != WicketType.RETIRED_HURT
        
        if (pB < 36) { // Powerplay: 1-6 Overs
            ppR += ballTotal
            if (isW) ppW++
        } else if (pB < 90) { // Middle: 7-15 Overs
            midR += ballTotal
            if (isW) midW++
        } else { // Death: 16-20 Overs
            finR += ballTotal
            if (isW) finW++
        }

        if (ball.wicketType != WicketType.NONE && ball.wicketType != WicketType.RETIRED_HURT) w++
        if (ball.extrasType != ExtrasType.NONE) exR += ball.extraRuns
        
        if (ball.extrasType == ExtrasType.WIDE) wC++
        if (ball.extrasType == ExtrasType.NO_BALL) nbC++
        
        if (ball.isDroppedCatch) dC++
        
        val runs = ball.runs
        if (ball.isPhysicalBall) pB++
        if (ball.isLegalBall) lB++

        when (runs) {
            0 -> if (ball.isLegalBall && ball.extraRuns == 0) dots++
            1 -> sR += 1
            2 -> dR += 2
            3 -> tR += 3
            4 -> fR += 4
            6 -> siR += 6
            else -> if (runs > 0) oR += runs
        }
    }
    
    val dP = if (lB > 0) (dots * 100) / lB else 0
    
    return InningsStats(
        sR, dR, tR, fR, siR, oR, dots, exR, w, dC, wC, nbC,
        ppR, ppW, midR, midW, finR, finW,
        fR + siR, dP, lB,
        pB > 36, pB > 90
    )
}

fun recoverName(id: String?, match: Match, defaultName: String = "Player"): String {
    if (id.isNullOrEmpty()) return defaultName
    
    // Priority 1: Check both teams comprehensively (v2.19 (Engine Hardened & Draw Fix) 🏏🚀⚖️🏅 Fix)
    match.teamA.players.find { it.id == id }?.name?.let { return it }
    match.teamB.players.find { it.id == id }?.name?.let { return it }
    
    // Priority 2: ID itself (if legacy name)
    if (id.length < 30 || id.contains(" ")) return id
    
    return "Guest Player"
}

fun calculatePartnerships(balls: List<Ball>, match: Match): List<Partnership> {
    val partnerships = mutableListOf<Partnership>()
    if (balls.isEmpty()) return partnerships
    
    var currentB1Id: String? = null
    var currentB2Id: String? = null
    var runs1 = 0; var balls1 = 0
    var runs2 = 0; var balls2 = 0
    var pExtras = 0
    
    balls.forEach { ball ->
        if (currentB1Id == null) {
            currentB1Id = ball.strikerId
            currentB2Id = ball.nonStrikerId
        }
        
        if (ball.strikerId == currentB1Id) {
            runs1 += ball.runs
            balls1 += if (ball.extrasType != ExtrasType.WIDE) 1 else 0
        } else if (ball.strikerId == currentB2Id) {
            runs2 += ball.runs
            balls2 += if (ball.extrasType != ExtrasType.WIDE) 1 else 0
        }
        
        pExtras += ball.extraRuns
        
        if (ball.wicketType != WicketType.NONE && ball.wicketType != WicketType.RETIRED_HURT) {
            val b1Name = recoverName(currentB1Id, match, "Striker")
            val b2Name = recoverName(currentB2Id, match, "Non-Striker")
            partnerships.add(Partnership(
                currentB1Id ?: "", b1Name, runs1, balls1,
                currentB2Id ?: "", b2Name, runs2, balls2,
                runs1 + runs2 + pExtras, balls1 + balls2
            ))
            currentB1Id = null; currentB2Id = null
            runs1 = 0; balls1 = 0; runs2 = 0; balls2 = 0
            pExtras = 0
        }
    }
    
    if (currentB1Id != null) {
        val b1Name = recoverName(currentB1Id, match, "Striker")
        val b2Name = recoverName(currentB2Id, match, "Non-Striker")
        partnerships.add(Partnership(
            currentB1Id ?: "", b1Name, runs1, balls1,
            currentB2Id ?: "", b2Name, runs2, balls2,
            runs1 + runs2 + pExtras, balls1 + balls2
        ))
    }
    
    return partnerships
}

@Composable
fun PartnershipsSection(match: Match, team1: Team, team2: Team) {
    val splitIdx = match.innings1Data?.recordedBallsCount ?: match.ballHistory.size
    val i1Balls = match.ballHistory.take(splitIdx)
    val i2Balls = match.ballHistory.drop(splitIdx)
    
    val inningsPairs = listOf(
        "1st Innings 🏏" to i1Balls,
        "2nd Innings 🚀" to i2Balls
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("PARTNERSHIPS 🏏🤝", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            
            inningsPairs.forEach { (label, balls) ->
                if (balls.isNotEmpty()) {
                    val isFirst = label.contains("1st")
                    val partnerships = calculatePartnerships(balls, match)
                    
                    if (partnerships.isNotEmpty()) {
                        val inningsTotalRuns = balls.sumOf { it.runs + it.extraRuns }
                        val inningsTotalWickets = balls.count { it.wicketType != WicketType.NONE && it.wicketType != WicketType.RETIRED_HURT }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Innings Header with distinct background
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                                .padding(vertical = 6.dp, horizontal = 10.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    label.uppercase(),
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                val teamName = if (isFirst) {
                                    if (match.initialBattingTeamId == match.teamA.id) match.teamA.name else match.teamB.name
                                } else {
                                    if (match.initialBattingTeamId == match.teamA.id) match.teamB.name else match.teamA.name
                                }
                                Text(
                                    "${teamName.uppercase()}  $inningsTotalRuns/$inningsTotalWickets",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.DarkGray
                                )
                            }
                        }
                        
                        partnerships.forEach { p ->
                            PartnershipRow(p)
                            HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PartnershipRow(p: Partnership) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(p.batter1Name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("${p.batter1Runs} (${p.batter1Balls})", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 13.sp)
        }
        
        Column(
            modifier = Modifier
                .width(80.dp)
                .background(Color(0xFFF0F4F8), RoundedCornerShape(8.dp))
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("${p.totalRuns}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
            Text("${p.totalBalls}b", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 13.sp)
        }
        
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(p.batter2Name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("${p.batter2Runs} (${p.batter2Balls})", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 13.sp)
        }
    }
}

@Composable
fun ScoringBreakdownCard(match: Match, i1Stats: InningsStats, i2Stats: InningsStats) {
    val teamA = match.teamA
    val teamB = match.teamB
    val i1Name = if (match.initialBattingTeamId == teamA.id) teamA.name else teamB.name
    val i2Name = if (match.initialBattingTeamId == teamA.id) teamB.name else teamA.name

    Card(
        modifier = Modifier.fillMaxWidth(), 
        colors = CardDefaults.cardColors(containerColor = Color.White), 
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), 
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("SCORING BREAKDOWN", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(16.dp))
            
            // Header Names
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(i1Name.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = Color.DarkGray)
                Text(i2Name.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = Color.DarkGray)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            fun compareScore(r1: Int, w1: Int, r2: Int, w2: Int): Int {
                if (r1 == 0 && w1 == 0 && r2 == 0 && w2 == 0) return 0
                if (r1 > r2) return 1
                if (r2 > r1) return 2
                if (w1 < w2) return 1
                if (w2 < w1) return 2
                return 0
            }

            CricinfoBreakdownRow("Power Play", "${i1Stats.ppRuns}/${i1Stats.ppWickets}", "${i2Stats.ppRuns}/${i2Stats.ppWickets}", compareScore(i1Stats.ppRuns, i1Stats.ppWickets, i2Stats.ppRuns, i2Stats.ppWickets))
            
            if (i1Stats.hasMid || i2Stats.hasMid) {
                CricinfoBreakdownRow("Middle Overs", if(i1Stats.hasMid) "${i1Stats.midRuns}/${i1Stats.midWickets}" else "-", if(i2Stats.hasMid) "${i2Stats.midRuns}/${i2Stats.midWickets}" else "-", compareScore(i1Stats.midRuns, i1Stats.midWickets, i2Stats.midRuns, i2Stats.midWickets))
            }
            
            if (i1Stats.hasFin || i2Stats.hasFin) {
                CricinfoBreakdownRow("Final Overs", if(i1Stats.hasFin) "${i1Stats.finRuns}/${i1Stats.finWickets}" else "-", if(i2Stats.hasFin) "${i2Stats.finRuns}/${i2Stats.finWickets}" else "-", compareScore(i1Stats.finRuns, i1Stats.finWickets, i2Stats.finRuns, i2Stats.finWickets))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.3f))

            CricinfoBreakdownRow("Sixes", "${i1Stats.sixesRuns/6}", "${i2Stats.sixesRuns/6}", if(i1Stats.sixesRuns > i2Stats.sixesRuns) 1 else if(i2Stats.sixesRuns > i1Stats.sixesRuns) 2 else 0)
            CricinfoBreakdownRow("Fours", "${i1Stats.foursRuns/4}", "${i2Stats.foursRuns/4}", if(i1Stats.foursRuns > i2Stats.foursRuns) 1 else if(i2Stats.foursRuns > i1Stats.foursRuns) 2 else 0)
            CricinfoBreakdownRow("Runs In Boundaries", "${i1Stats.boundaryRuns}", "${i2Stats.boundaryRuns}", if(i1Stats.boundaryRuns > i2Stats.boundaryRuns) 1 else if(i2Stats.boundaryRuns > i1Stats.boundaryRuns) 2 else 0)
            
            CricinfoBreakdownRow("Dot balls", "${i1Stats.dotPercent}%", "${i2Stats.dotPercent}%", if(i1Stats.dotPercent < i2Stats.dotPercent && i1Stats.dotPercent > 0) 1 else if(i2Stats.dotPercent < i1Stats.dotPercent && i2Stats.dotPercent > 0) 2 else 0)
            CricinfoBreakdownRow("Runs In Extras", "${i1Stats.extrasRuns}", "${i2Stats.extrasRuns}", if(i1Stats.extrasRuns > i2Stats.extrasRuns) 1 else if(i2Stats.extrasRuns > i1Stats.extrasRuns) 2 else 0)
        }
    }
}

@Composable
fun CricinfoBreakdownRow(label: String, v1: String, v2: String, highlight: Int = 0) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (v1 != "-") {
                Surface(
                    color = if (highlight == 1) MaterialTheme.colorScheme.primary else Color(0xFFF8F9FA),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = v1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (highlight == 1) FontWeight.Black else FontWeight.Normal,
                        color = if (highlight == 1) Color.White else Color.Black
                    )
                }
            } else {
                Text("-", modifier = Modifier.padding(start = 8.dp), color = Color.LightGray)
            }
        }
        
        Text(
            text = label,
            modifier = Modifier.weight(1.5f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (v2 != "-") {
                Surface(
                    color = if (highlight == 2) MaterialTheme.colorScheme.primary else Color(0xFFF8F9FA),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = v2,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (highlight == 2) FontWeight.Black else FontWeight.Normal,
                        color = if (highlight == 2) Color.White else Color.Black
                    )
                }
            } else {
                Text("-", modifier = Modifier.padding(end = 8.dp), color = Color.LightGray)
            }
        }
    }
}

@Composable
fun BreakdownRow(label: String, v1: String, v2: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(v1, modifier = Modifier.width(50.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        Text(v2, modifier = Modifier.width(50.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BestPerformancesBatters(teamA: Team, teamB: Team) {
    val allBatters = (teamA.players + teamB.players)
        .filter { it.battingStats.balls > 0 }
        .sortedByDescending { it.battingStats.runs }
        .take(5)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("TOP BATTERS", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(12.dp))
            allBatters.forEach { player ->
                val teamName = if (teamA.players.any { it.id == player.id }) teamA.name else teamB.name
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(player.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        Text(teamName, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${player.battingStats.runs}", fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyLarge)
                        Text(" (${player.battingStats.balls})", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Spacer(Modifier.width(12.dp))
                        if (player.battingStats.fours > 0) {
                            Text("4s: ${player.battingStats.fours}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                        }
                        if (player.battingStats.sixes > 0) {
                            Text("6s: ${player.battingStats.sixes}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF1565C0), fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (player != allBatters.last()) {
                    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
                }
            }
        }
    }
}

@Composable
fun BestPerformancesBowlers(teamA: Team, teamB: Team) {
    val allBowlers = (teamA.players + teamB.players)
        .filter { it.bowlingStats.overs > 0 || it.bowlingStats.balls > 0 }
        .sortedWith(compareByDescending<Player> { it.bowlingStats.wickets }.thenBy { it.bowlingStats.runsConceded })
        .take(5)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("TOP BOWLERS", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(12.dp))
            allBowlers.forEach { player ->
                val teamName = if (teamA.players.any { it.id == player.id }) teamA.name else teamB.name
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(player.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        Text(teamName, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${player.bowlingStats.wickets}/${player.bowlingStats.runsConceded}", fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.width(12.dp))
                        Text("${player.bowlingStats.overs}.${player.bowlingStats.balls} ov", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontWeight = FontWeight.Bold)
                        if (player.bowlingStats.dotBalls > 0) {
                            Spacer(Modifier.width(8.dp))
                            Text("● ${player.bowlingStats.dotBalls}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF5D4037))
                        }
                    }
                }
                if (player != allBowlers.last()) {
                    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
                }
            }
        }
    }
}

@Composable
fun BallBox(ball: Ball, onClick: () -> Unit) {
    val bgColor = when {
        ball.wicketType != WicketType.NONE && ball.wicketType != WicketType.RETIRED_HURT -> Color(0xFFFFEBEE)
        ball.isDroppedCatch -> Color(0xFFFFF3E0)
        ball.extrasType != ExtrasType.NONE -> Color(0xFFFFF3E0)
        else -> Color.White
    }
    val textColor = when {
        ball.wicketType != WicketType.NONE && ball.wicketType != WicketType.RETIRED_HURT -> Color.Red
        ball.isDroppedCatch -> Color(0xFFFF9800)
        ball.extrasType != ExtrasType.NONE -> Color(0xFFE65100)
        else -> Color.Black
    }
    val text = when {
        ball.wicketType == WicketType.RETIRED_HURT -> "RH"
        ball.wicketType != WicketType.NONE -> "W"
        ball.isDroppedCatch -> "🤲${ball.runs}"
        ball.extrasType == ExtrasType.WIDE -> "${ball.extraRuns}wd"
        ball.extrasType == ExtrasType.NO_BALL -> "${ball.runs + ball.extraRuns}nb"
        ball.extrasType == ExtrasType.BYE -> "${ball.extraRuns}b"
        ball.extrasType == ExtrasType.LEG_BYE -> "${ball.extraRuns}lb"
        ball.extrasType == ExtrasType.GRANTED -> "${ball.runs}G"
        else -> "${ball.runs}"
    }

    Box(
        modifier = Modifier
            .size(36.dp)
            .background(bgColor, CircleShape)
            .border(1.dp, Color.LightGray, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}


@Composable
fun WicketDialog(match: Match, viewModel: ScoringViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Wicket", fontWeight = FontWeight.Black) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Select Wicket Type", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                
                val allowedTypes = listOf(
                    WicketType.BOWLED, WicketType.CAUGHT, WicketType.LBW, 
                    WicketType.RUN_OUT, WicketType.STUMPED, WicketType.HIT_WICKET
                )
                
                allowedTypes.forEach { type ->
                    OutlinedButton(
                        onClick = { 
                            if (type == WicketType.RUN_OUT) {
                                viewModel.handleWicket(type, null) // Don't pick player here, physics will decide
                                onDismiss()
                            } else {
                                viewModel.handleWicket(type, match.strikerId)
                                onDismiss()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(type.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() })
                    }
                }
            }
        },
        confirmButton = {
            // v2.19 (Engine Hardened & Draw Fix) 🏏🚀⚖️🏅: Removed Confirm button for immediate action
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("CANCEL") }
        }
    )
}

@Composable
fun ExtraRunsDialog(type: ExtrasType, viewModel: ScoringViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${type.name} + Extra Runs", fontWeight = FontWeight.Black) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Any additional runs taken?", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val options = if (type == ExtrasType.NO_BALL) listOf(0, 1, 2, 3, 4, 6) else listOf(0, 1, 2, 3, 4)
                    options.forEach { extra ->
                        OutlinedButton(
                            onClick = {
                                viewModel.handleExtra(type, extra)
                                onDismiss()
                            },
                            shape = CircleShape,
                            modifier = Modifier.size(44.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(if (extra == 6 && type == ExtrasType.NO_BALL) "+6" else "$extra")
                        }
                    }
                    var showCustomPrompt by remember { mutableStateOf(false) }
                    var customValue by remember { mutableStateOf("") }
                    
                    OutlinedButton(
                        onClick = { showCustomPrompt = true },
                        shape = CircleShape,
                        modifier = Modifier.size(44.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("...")
                    }
                    
                    if (showCustomPrompt) {
                        AlertDialog(
                            onDismissRequest = { showCustomPrompt = false },
                            title = { Text("Custom Extra Runs") },
                            text = {
                                OutlinedTextField(
                                    value = customValue,
                                    onValueChange = { if (it.all { c -> c.isDigit() }) customValue = it },
                                    label = { Text("Runs") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            },
                            confirmButton = {
                                Button(onClick = {
                                    val runs = customValue.toIntOrNull() ?: 0
                                    viewModel.handleExtra(type, runs)
                                    showCustomPrompt = false
                                    onDismiss()
                                }) { Text("OK") }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun OtherRunsDialog(viewModel: ScoringViewModel, onDismiss: () -> Unit) {
    var customValue by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Overthrow", fontWeight = FontWeight.Black)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Select bat runs (Strike will rotate if odd)", style = MaterialTheme.typography.bodyMedium)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3, 5, 7).forEach { runs ->
                        OutlinedButton(
                            onClick = {
                                viewModel.handleRuns(runs, true)
                                onDismiss()
                            },
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("$runs")
                        }
                    }
                }

                OutlinedTextField(
                    value = customValue,
                    onValueChange = { if (it.all { char -> char.isDigit() }) customValue = it },
                    label = { Text("Custom Value") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            val runs = customValue.toIntOrNull()
                            if (runs != null) {
                                viewModel.handleRuns(runs, true)
                                onDismiss()
                            }
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Submit")
                        }
                    }
                )
            }
        },
        confirmButton = {}
    )
}

@Composable
fun RetireHurtDialog(match: Match, viewModel: ScoringViewModel, onDismiss: () -> Unit) {
    val battingTeam = if (match.battingTeamId == match.teamA.id) match.teamA else match.teamB
    val striker = battingTeam.players.find { it.id == match.strikerId }
    val nonStriker = battingTeam.players.find { it.id == match.nonStrikerId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Retire Hurt", fontWeight = FontWeight.Black)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = null) }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Who is retiring hurt?")
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    striker?.let {
                        Button(
                            onClick = {
                                viewModel.handleWicket(WicketType.RETIRED_HURT, it.id)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                        ) {
                            Text("Striker: ${it.name}")
                        }
                    }
                    nonStriker?.let {
                        Button(
                            onClick = {
                                viewModel.handleWicket(WicketType.RETIRED_HURT, it.id)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                        ) {
                            Text("Non-Striker: ${it.name}")
                        }
                    }
                    
                    if (striker != null && nonStriker != null) {
                        OutlinedButton(
                            onClick = {
                                // v2.28.1: Unified double retirement call 🏏🚀⚖️🏅
                                viewModel.handleDoubleRetire()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Text("RETIRE BOTH")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { }
    )
}
