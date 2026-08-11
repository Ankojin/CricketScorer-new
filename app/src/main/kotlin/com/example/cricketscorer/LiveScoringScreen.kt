package com.example.cricketscorer

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
import kotlin.time.Duration.Companion.milliseconds
import com.example.cricketscorer.ui.CaptureArea
import androidx.compose.ui.graphics.layer.GraphicsLayer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScoringScreen(
    viewModel: ScoringViewModel,
    onNavigateToDashboard: () -> Unit,
    onNavigateToMatches: () -> Unit
) {
    val match by viewModel.matchState.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var viewedInnings by remember(match?.currentInnings) { mutableIntStateOf(match?.currentInnings ?: 1) }
    var showMatchFinishedDialog by remember { mutableStateOf(true) }
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
                                if (isSyncEnabled) {
                                    Spacer(Modifier.width(8.dp))
                                    val syncColor = if (connectedDevices.isNotEmpty()) Color(0xFF4CAF50) else Color(0xFFFFC107)
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Sync Status",
                                        modifier = Modifier.size(14.dp),
                                        tint = syncColor
                                    )
                                    if (connectedDevices.isNotEmpty()) {
                                        Text(
                                            " ${connectedDevices.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = syncColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            match?.let { m ->
                                val indicator = when {
                                    m.status == MatchStatus.COMPLETED -> "MATCH COMPLETED"
                                    m.pendingAction == PendingAction.START_SECOND_INNINGS -> "1ST INNINGS COMPLETED"
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
                                    
                                    "[$indicator] ${team.name} $runs/$wkts (${balls / 6}.${balls % 6}) • CRR: ${String.format(Locale.getDefault(), "%.2f", crr)}"
                                } else {
                                    val battingTeamName = if (m.battingTeamId == m.teamA.id) m.teamA.name else m.teamB.name
                                    val crr = if (m.totalBalls > 0) (m.totalRuns.toDouble() / (m.totalBalls / 6.0 + (m.totalBalls % 6) / 6.0)) else 0.0
                                    buildString {
                                        append("[$indicator] $battingTeamName ${m.totalRuns}/${m.totalWickets} (${m.totalBalls / 6}.${m.totalBalls % 6})")
                                        append(" • CRR: ${String.format(Locale.getDefault(), "%.2f", crr)}")
                                        if (m.currentInnings == 2 && m.status != MatchStatus.COMPLETED) {
                                            val runsNeeded = (m.target ?: 0) - m.totalRuns
                                            val ballsRemaining = (m.oversPerInnings * 6) - m.totalBalls
                                            append(" • Target: ${m.target} • Need $runsNeeded off $ballsRemaining")
                                        }
                                    }
                                }
                                Text(
                                    text = tickerText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.basicMarquee(),
                                    maxLines = 1
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateToDashboard) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showOversDialog = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
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
                        if (type == ExtrasType.WIDE) {
                            viewModel.handleExtra(ExtrasType.WIDE, 1) // v1.48: Record 1+1 = 2 runs for Wide directly
                        } else {
                            showExtraRunsDialog = type
                        }
                    },
                        onShowOtherRuns = { showOtherRunsDialog = true },
                        onShowRetireHurt = { showRetireHurtDialog = true }
                    )
                    1 -> ScorecardTab(m, viewedInnings, scorecardGraphicsLayer) { viewedInnings = it }
                    2 -> OversTab(m, viewModel, oversGraphicsLayer)
                    3 -> StatsTab(m, statsGraphicsLayer)
                }

                if (m.pendingAction == PendingAction.TOSS_REQUIRED) {
                    TossOverlay(m, viewModel)
                } else if (m.pendingAction == PendingAction.START_SECOND_INNINGS && m.status != MatchStatus.COMPLETED) {
                    InningsOverOverlay(m, viewModel)
                } else if (m.pendingAction == PendingAction.SELECT_RUNS_DROPPED_CATCH) {
                    DroppedCatchRunsOverlay(viewModel)
                } else if (m.pendingAction != PendingAction.NONE && m.status != MatchStatus.COMPLETED) {
                    PlayerSelectionOverlay(m, viewModel)
                }
                
                if (m.status == MatchStatus.COMPLETED && showMatchFinishedDialog) {
                    MatchResultOverlay(m, onDismiss = { showMatchFinishedDialog = false }, onNavigateToDashboard)
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
                    val currentOvers = m.oversPerInnings
                    val currentMaxOvers = m.maxOversPerBowler
                    val currentQuotaCount = m.quotaBowlersCount
                    val currentQuotaLimit = m.quotaMaxOvers

                    var oversText by remember { mutableStateOf(currentOvers.toString()) }
                    var maxOversText by remember { mutableStateOf(currentMaxOvers?.toString() ?: "") }
                    var quotaCountText by remember { mutableStateOf(currentQuotaCount?.toString() ?: "") }
                    var quotaLimitText by remember { mutableStateOf(currentQuotaLimit?.toString() ?: "") }

                    AlertDialog(
                        onDismissRequest = { showOversDialog = false },
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
                                Spacer(modifier = Modifier.height(16.dp))
                                TextButton(
                                    onClick = {
                                        showManageSquads = true
                                        showOversDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.List,
                                        contentDescription = null
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("MANAGE SQUADS", fontWeight = FontWeight.Bold)
                                }
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                val newOvers = oversText.toIntOrNull() ?: currentOvers
                                val newMaxOvers = maxOversText.toIntOrNull()
                                val newQuotaCount = quotaCountText.toIntOrNull()
                                val newQuotaLimit = quotaLimitText.toIntOrNull()
                                if (newOvers > 0) {
                                    viewModel.updateMatchSettings(newOvers, newMaxOvers, newQuotaCount, newQuotaLimit)
                                }
                                showOversDialog = false
                            }) {
                                Text("UPDATE")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showOversDialog = false }) {
                                Text("CANCEL")
                            }
                        }
                    )
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
                    "Go to the Matches tab to select a game or resume scoring from the Home screen.",
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
                                durationMinutes = match.innings1Data?.durationMinutes ?: 0,
                                battingOrder = match.innings1Data?.battingOrder ?: (if (match.currentInnings == 1) match.battingOrder else emptyList())
                            )
                        } else {
                            val i2Team = if (match.initialBattingTeamId == teamA.id) teamB else teamA
                            val i2Duration = if (match.status == MatchStatus.COMPLETED) {
                                match.startTimeMillis?.let { start ->
                                    match.endTimeMillis?.let { end -> ((end - start) / 60000).toInt() }
                                } ?: 0
                            } else {
                                match.startTimeMillis?.let { ((System.currentTimeMillis() - it) / 60000).toInt() } ?: 0
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
                                battingOrder = if (match.currentInnings == 2) match.battingOrder else emptyList()
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
    wicketHistory: List<WicketRecord>,
    wideCount: Int,
    noBallCount: Int,
    byeCount: Int,
    legByeCount: Int,
    totalScore: String?,
    totalOvers: String?,
    bowlingTeamPlayers: List<Player>,
    maxBalls: Int,
    numericRuns: Int,
    numericBalls: Int,
    durationMinutes: Int,
    battingOrder: List<String>
) {
    Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
        Box(
            modifier = Modifier.fillMaxWidth().background(Color(0xFFE3F2FD)).padding(16.dp)
        ) {
            Column {
                Text(text = "🏏 ${team.name} 🏆", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color(0xFF003366))
                Text(text = "($maxBalls balls maximum)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
        
        BattingTable(match, team.players, strikerId, nonStrikerId, bowlingTeamPlayers, battingOrder)
        
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
                    text = "$totalOvers Ov (RR: ${String.format(Locale.getDefault(), "%.2f", crr)}, $durationMinutes Mins)",
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
                Text("DID NOT BAT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.Gray)
                Text(text = didNotBat.joinToString { 
                    val style = (it.battingStyle ?: BattingStyle.RHB).name.take(1)
                    (if (it.isJoker) "${it.name} 🃏" else it.name) + " ($style)"
                }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp), fontWeight = FontWeight.Medium)
            }
            HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
        }

        if (wicketHistory.isNotEmpty()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("☝️ FALL OF WICKETS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.Gray)
                Text(text = wicketHistory.joinToString { "☝️ ${it.wicketNumber}-${it.totalRuns} (${it.batterName}, ${it.over} ov) 🚩" }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp), lineHeight = 20.sp)
            }
            HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
        }

        Spacer(modifier = Modifier.height(16.dp))
        BowlingTable(match, bowlingTeamPlayers)
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun BattingTable(match: Match, players: List<Player>, strikerId: String?, nonStrikerId: String?, bowlers: List<Player>, battingOrder: List<String>) {
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
        
        val sortedPlayers = players.sortedBy { player ->
            val idx = battingOrder.indexOf(player.id)
            if (idx == -1) Int.MAX_VALUE else idx
        }
        
        sortedPlayers.forEach { player ->
            val isCurrentBatter = player.id == strikerId || player.id == nonStrikerId
            if (player.battingStats.balls > 0 || player.battingStats.isOut || isCurrentBatter) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(4f)) {
                        val isCaptain = player.id == match.teamACaptainId || player.id == match.teamBCaptainId
                        val isWK = player.id == match.teamAWicketKeeperId || player.id == match.teamBWicketKeeperId
                        val isRH = player.battingStats.isRetiredHurt
                        val isJoker = player.isJoker
                        val bStyle = (player.battingStyle ?: BattingStyle.RHB).name.take(1)
                        Text(
                            text = "🏏 " + player.name + " ($bStyle)" + (if (isJoker) " 🃏" else "") + (if (isCaptain) " (c)" else "") + (if (isWK) " 🧤" else "") + (if (isCurrentBatter && !player.battingStats.isOut) "*" else "") + (if (isRH) " (Retired Hurt) 🤕" else ""),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (player.battingStats.isOut) Color.Gray else Color.Black
                        )
                        val dismissalBowlerName = bowlers.find { it.id == player.battingStats.dismissalBowlerId }?.name
                        val dismissalFielderName = bowlers.find { it.id == player.battingStats.dismissalFielderId }?.name ?: players.find { it.id == player.battingStats.dismissalFielderId }?.name
                        
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
fun BowlingTable(match: Match, players: List<Player>) {
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
            Text("NB", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = Color.Gray)
            Text("WD", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = Color.Gray)
            Text("R", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = Color.Gray)
            Text("W", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = Color.Gray)
            Text("ER", modifier = Modifier.width(45.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = Color.Gray)
        }
        players.forEach { player ->
            if (player.bowlingStats.overs > 0 || player.bowlingStats.balls > 0 || player.id == match.currentBowlerId) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(3f), verticalAlignment = Alignment.CenterVertically) {
                        val isCaptain = player.id == match.teamACaptainId || player.id == match.teamBCaptainId
                        val bStyle = (player.battingStyle ?: BattingStyle.RHB).name.take(1)
                        val bowlStyle = if ((player.bowlingStyle ?: BowlingStyle.RightArm) == BowlingStyle.RightArm) "RA" else "LA"
                        Text("⚾ " + player.name + " ($bStyle, $bowlStyle)" + (if (player.isJoker) " 🃏" else "") + (if (isCaptain) " (c)" else ""), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        val isWK = player.id == match.teamAWicketKeeperId || player.id == match.teamBWicketKeeperId
                        if (isWK) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("🧤", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(player.bowlingStats.formattedOvers, modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                    Text("${player.bowlingStats.maidens}", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                    Text("${player.bowlingStats.noBalls}", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                    Text("${player.bowlingStats.wides}", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                    Text("${player.bowlingStats.runsConceded}", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                    Text("${player.bowlingStats.wickets}", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.primary)
                    Text(String.format(Locale.getDefault(), "%.2f", player.bowlingStats.economy), modifier = Modifier.width(45.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
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

        val splitIdx = match.innings1Data?.recordedBallsCount ?: match.ballHistory.size
        val inningsBallsWithIndices = if (selectedInnings == 1) {
            match.ballHistory.take(splitIdx).mapIndexed { index, ball -> index to ball }
        } else {
            match.ballHistory.drop(splitIdx).mapIndexed { index, ball -> (index + splitIdx) to ball }
        }

        val battingTeam = if (selectedInnings == 1) (if (match.initialBattingTeamId == teamA.id) teamA else teamB) else (if (match.initialBattingTeamId == teamA.id) teamB else teamA)
        val bowlingTeam = if (battingTeam.id == teamA.id) teamB else teamA
        val bowlingTeamPlayers = bowlingTeam.players

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
                            if (ball.isLegalBall && currentOver.count { it.second.isLegalBall } == 6) {
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
                                    val bowlerId = overBalls.firstOrNull()?.second?.bowlerId
                                    val bowlerName = bowlingTeamPlayers.find { it.id == bowlerId }?.name ?: "Unknown"

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column {
                                            Text("Over $overNum", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                            val bowlersInOver = overBalls.groupBy { it.second.bowlerId }.map { (id, balls) ->
                                                val name = bowlingTeamPlayers.find { it.id == id }?.name ?: "Unknown"
                                                "$name (${balls.count { it.second.isLegalBall }})"
                                            }.joinToString(", ")
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CricketBallIcon(modifier = Modifier.size(10.dp).padding(end = 4.dp))
                                                Text("Bowlers: $bowlersInOver", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        val overRuns = overBalls.sumOf { it.second.runs + it.second.extraRuns }
                                        val overWickets = overBalls.count { it.second.wicketType != WicketType.NONE && it.second.wicketType != WicketType.RETIRED_HURT }
                                        Text("$overRuns Runs" + (if (overWickets > 0) ", $overWickets Wkts" else ""), fontWeight = FontWeight.Bold, color = Color.DarkGray)
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        overBalls.forEach { (idx, ball) ->
                                            BallBox(ball, onClick = { editingBallIndex = idx })
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

        AlertDialog(
            onDismissRequest = { editingBallIndex = null },
            title = { Text("Edit Ball ${editingBallIndex!! + 1}", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                }
            },
            confirmButton = {
                Button(onClick = {
                    val updatedBall = ball.copy(
                        runs = runs,
                        extrasType = extraType,
                        extraRuns = if (extraType == ExtrasType.NONE) 0 else extraRuns,
                        wicketType = wicketType,
                        isLegalBall = extraType != ExtrasType.WIDE && extraType != ExtrasType.NO_BALL
                    )
                    viewModel.editBall(editingBallIndex!!, updatedBall)
                    editingBallIndex = null
                }) {
                    Text("SAVE CHANGES")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingBallIndex = null }) {
                    Text("CANCEL")
                }
            }
        )
    }
}

@Composable
fun CricketBallIcon(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(12.dp),
        shape = CircleShape,
        color = Color(0xFFC62828), // Cricket Red
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
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
                        ScoringBreakdownCard(i1Stats, i2Stats)
                        BestPerformancesBatters(i1Team, i2Team)
                        BestPerformancesBowlers(i1Team, i2Team)
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
        item { PlayerStatsSection(match, viewModel) }
        item {
            if (match.status == MatchStatus.COMPLETED) {
                val winnerTeam = if (match.winnerId == match.teamA.id) match.teamA else if (match.winnerId == match.teamB.id) match.teamB else null
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("MATCH SUMMARY", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(12.dp))
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
                    viewModel = viewModel,
                    onShowWicket = onShowWicket,
                    onShowExtraRuns = onShowExtraRuns,
                    onShowOtherRuns = onShowOtherRuns,
                    onShowRetireHurt = onShowRetireHurt
                )
            }
        }
    }
}

@Composable
fun TossOverlay(match: Match, viewModel: ScoringViewModel) {
    var winnerId by remember { mutableStateOf<String?>(null) }
    var decision by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { },
        title = { Text("Match Toss", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Who won the toss?", fontWeight = FontWeight.Bold)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = winnerId == match.teamA.id, onClick = { winnerId = match.teamA.id })
                        Text(match.teamA.name, modifier = Modifier.clickable { winnerId = match.teamA.id })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = winnerId == match.teamB.id, onClick = { winnerId = match.teamB.id })
                        Text(match.teamB.name, modifier = Modifier.clickable { winnerId = match.teamB.id })
                    }
                }
                
                Text("Decision?", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = decision == "BAT", onClick = { decision = "BAT" })
                        Text("BAT", modifier = Modifier.clickable { decision = "BAT" })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = decision == "BOWL", onClick = { decision = "BOWL" })
                        Text("BOWL", modifier = Modifier.clickable { decision = "BOWL" })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    winnerId?.let { w -> 
                        decision?.let { d -> 
                            viewModel.handleToss(w, d) 
                        } 
                    } 
                },
                enabled = winnerId != null && decision != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("START MATCH")
            }
        }
    )
}

@Composable
fun InningsOverOverlay(match: Match, viewModel: ScoringViewModel) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Innings Completed", fontWeight = FontWeight.Black) },
        text = {
            val teamName = if (match.initialBattingTeamId == match.teamA.id) match.teamA.name else match.teamB.name
            val runs = match.innings1Data?.runs ?: 0
            val wickets = match.innings1Data?.wickets ?: 0
            val balls = match.innings1Data?.balls ?: 0
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
        onDismissRequest = { },
        title = { Text("Runs on Dropped Catch", fontWeight = FontWeight.Black) },
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
fun PlayerSelectionOverlay(match: Match, viewModel: ScoringViewModel) {
    val title = when (match.pendingAction ?: PendingAction.NONE) {
        PendingAction.SELECT_STRIKER -> "Select Striker"
        PendingAction.SELECT_NON_STRIKER -> "Select Non-Striker"
        PendingAction.SELECT_BOWLER -> "Select Bowler"
        PendingAction.REPLACE_STRIKER -> "Replace Striker"
        PendingAction.REPLACE_NON_STRIKER -> "Replace Non-Striker"
        PendingAction.REPLACE_BOWLER -> "Replace Bowler"
        PendingAction.SELECT_FIELDER -> "Select Fielder"
        PendingAction.SELECT_FIELDER_DROPPED_CATCH -> "Who dropped the catch?"
        PendingAction.SELECT_CAPTAIN_A -> "Select Captain (${match.teamA.name})"
        PendingAction.SELECT_CAPTAIN_B -> "Select Captain (${match.teamB.name})"
        PendingAction.SELECT_WK_A -> "Select Wicket Keeper (${match.teamA.name})"
        PendingAction.SELECT_WK_B -> "Select Wicket Keeper (${match.teamB.name})"
        else -> "Select Player"
    }
    
    val team = when (match.pendingAction ?: PendingAction.NONE) {
        PendingAction.SELECT_CAPTAIN_A, PendingAction.SELECT_WK_A -> match.teamA
        PendingAction.SELECT_CAPTAIN_B, PendingAction.SELECT_WK_B -> match.teamB
        PendingAction.SELECT_BOWLER, PendingAction.REPLACE_BOWLER, PendingAction.SELECT_FIELDER, PendingAction.SELECT_FIELDER_DROPPED_CATCH -> {
            if (match.battingTeamId == match.teamA.id) match.teamB else match.teamA
        }
        else -> {
            if (match.battingTeamId == match.teamA.id) match.teamA else match.teamB
        }
    }

    AlertDialog(
        onDismissRequest = { 
            if (match.pendingAction?.name?.startsWith("REPLACE_") == true) {
                viewModel.startSecondInnings() // Using this as a generic "clear pending action" or similar
                // Actually startSecondInnings just sets pendingAction to NONE.
            }
        },
        title = { Text(title, fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                items(team.players) { player ->
                    val isAvailable = when (match.pendingAction ?: PendingAction.NONE) {
                        PendingAction.SELECT_STRIKER, PendingAction.SELECT_NON_STRIKER,
                        PendingAction.REPLACE_STRIKER, PendingAction.REPLACE_NON_STRIKER -> 
                            !player.battingStats.isOut && player.id != match.strikerId && player.id != match.nonStrikerId
                        PendingAction.SELECT_BOWLER, PendingAction.REPLACE_BOWLER -> 
                            player.id != match.lastBowlerId && player.id != (if (team.id == match.teamA.id) match.teamAWicketKeeperId else match.teamBWicketKeeperId)
                        else -> true
                    }
                    if (isAvailable) {
                        ListItem(
                            headlineContent = { 
                                val isCaptain = player.id == match.teamACaptainId || player.id == match.teamBCaptainId
                                val isWK = player.id == match.teamAWicketKeeperId || player.id == match.teamBWicketKeeperId
                                val isRH = player.battingStats.isRetiredHurt
                                val isSpellCompleted = viewModel.isSpellCompleted(player, match)
                                val currentAction = match.pendingAction ?: PendingAction.NONE
                                val showSpellCompleted = isSpellCompleted && (currentAction == PendingAction.SELECT_BOWLER || currentAction == PendingAction.REPLACE_BOWLER)
                                
                                val bStyle = (player.battingStyle ?: BattingStyle.RHB).name.take(1)
                                val bowlStyle = if ((player.bowlingStyle ?: BowlingStyle.RightArm) == BowlingStyle.RightArm) "RA" else "LA"
                                val isBowlerAction = currentAction == PendingAction.SELECT_BOWLER || currentAction == PendingAction.REPLACE_BOWLER
                                
                                Text(
                                    player.name + 
                                    (if (isBowlerAction) " ($bStyle, $bowlStyle)" else " ($bStyle)") +
                                    (if (player.isJoker) " 🃏" else "") + 
                                    (if (isCaptain) " (c)" else "") + 
                                    (if (isWK) " 🧤" else "") +
                                    (if (isRH) " (Retired Hurt) 🤕" else "") +
                                    (if (showSpellCompleted) " (Spell Completed) 🛑" else "")
                                ) 
                            },
                            modifier = Modifier.clickable { 
                                if (match.pendingAction == PendingAction.SELECT_FIELDER || match.pendingAction == PendingAction.SELECT_FIELDER_DROPPED_CATCH) {
                                    viewModel.selectFielder(player.id)
                                } else {
                                    viewModel.selectNewPlayer(player.id)
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (match.pendingAction == PendingAction.SELECT_BOWLER || match.pendingAction == PendingAction.REPLACE_BOWLER) {
                TextButton(onClick = { viewModel.forceChangeBowler() }) { Text("SAME BOWLER") }
            }
            if (match.pendingAction?.name?.startsWith("REPLACE_") == true) {
                TextButton(onClick = { viewModel.startSecondInnings() }) { Text("CANCEL") }
            }
        }
    )
}

@Composable
fun MatchResultOverlay(match: Match, onDismiss: () -> Unit, onNavigateToDashboard: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Match Finished", fontWeight = FontWeight.Bold) },
        text = {
            val winner = if (match.winnerId == match.teamA.id) match.teamA.name else if (match.winnerId == match.teamB.id) match.teamB.name else "Match Drawn"
            Text(if (match.winnerId != null) "$winner won the match! 🏆" else "The match ended in a draw. 🤝")
        },
        confirmButton = {
            Button(onClick = onNavigateToDashboard) {
                Text("EXIT")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("VIEW SCORECARD")
            }
        }
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
    var newPlayerName by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, contentDescription = null) }
        }
        LazyColumn(modifier = Modifier.height(150.dp)) {
            items(team.players) { player ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    val isCaptain = player.id == match.teamACaptainId || player.id == match.teamBCaptainId
                    val isWK = player.id == match.teamAWicketKeeperId || player.id == match.teamBWicketKeeperId
                    val bStyle = (player.battingStyle ?: BattingStyle.RHB).name.take(1)
                    val bowlStyle = if ((player.bowlingStyle ?: BowlingStyle.RightArm) == BowlingStyle.RightArm) "RA" else "LA"
                    Text(
                        text = player.name + " ($bStyle, $bowlStyle)" + (if (player.isJoker) " 🃏" else "") + (if (isCaptain) " (c)" else "") + (if (isWK) " 🧤" else ""),
                        style = MaterialTheme.typography.bodySmall
                    )
                    IconButton(onClick = { viewModel.deletePlayerFromMatch(player.id) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        if (showAddDialog) {
            var selectedStyle by remember { mutableStateOf(BattingStyle.RHB) }
            var selectedBowlStyle by remember { mutableStateOf(BowlingStyle.RightArm) }
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Add Player") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = newPlayerName, 
                            onValueChange = { newPlayerName = it }, 
                            label = { Text("Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
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
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Bowling Style", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BowlingStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = selectedBowlStyle == style,
                                    onClick = { selectedBowlStyle = style },
                                    label = { Text(if (style == BowlingStyle.RightArm) "Right Arm" else "Left Arm") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (newPlayerName.isNotBlank()) {
                            viewModel.addNewPlayerToMatch(context, newPlayerName, selectedStyle, selectedBowlStyle)
                            newPlayerName = ""
                            showAddDialog = false
                        }
                    }) { Text("ADD") }
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
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val battingTeam = if (match.battingTeamId == match.teamA.id) match.teamA else match.teamB
            Text(battingTeam.name.uppercase(), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelLarge)
            
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = "${match.totalRuns}/${match.totalWickets}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "(${match.totalBalls / 6}.${match.totalBalls % 6})", style = MaterialTheme.typography.titleMedium, color = Color.Gray, modifier = Modifier.padding(bottom = 4.dp))
            }
            
            if (match.currentInnings == 2) {
                val needed = (match.target ?: 0) - match.totalRuns
                val ballsLeft = (match.oversPerInnings * 6) - match.totalBalls
                Text("Need $needed off $ballsLeft balls", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
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
            val isCaptain = it.id == match.teamACaptainId || it.id == match.teamBCaptainId
            val isWK = it.id == match.teamAWicketKeeperId || it.id == match.teamBWicketKeeperId
            val nameWithExtras = it.name + " (${(it.battingStyle ?: BattingStyle.RHB).name.take(1)})" + (if (it.isJoker) " 🃏" else "") + (if (isCaptain) " (c)" else "") + (if (isWK) " 🧤" else "")
            PlayerRow(nameWithExtras, it.battingStats.runs, it.battingStats.balls, it.battingStats.fours, it.battingStats.sixes, it.battingStats.strikeRate, true, onNameClick = { viewModel.replaceStriker() }) 
        }
        nonStriker?.let { 
            val isCaptain = it.id == match.teamACaptainId || it.id == match.teamBCaptainId
            val isWK = it.id == match.teamAWicketKeeperId || it.id == match.teamBWicketKeeperId
            val nameWithExtras = it.name + " (${(it.battingStyle ?: BattingStyle.RHB).name.take(1)})" + (if (it.isJoker) " 🃏" else "") + (if (isCaptain) " (c)" else "") + (if (isWK) " 🧤" else "")
            PlayerRow(nameWithExtras, it.battingStats.runs, it.battingStats.balls, it.battingStats.fours, it.battingStats.sixes, it.battingStats.strikeRate, false, onNameClick = { viewModel.replaceNonStriker() }) 
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
                val isCaptain = it.id == match.teamACaptainId || it.id == match.teamBCaptainId
                val isWK = it.id == match.teamAWicketKeeperId || it.id == match.teamBWicketKeeperId
                val bStyle = (it.battingStyle ?: BattingStyle.RHB).name.take(1)
                val bowlStyle = if ((it.bowlingStyle ?: BowlingStyle.RightArm) == BowlingStyle.RightArm) "RA" else "LA"
                val nameWithExtras = it.name + " ($bStyle, $bowlStyle)" + (if (it.isJoker) " 🃏" else "") + (if (isCaptain) " (c)" else "") + (if (isWK) " 🧤" else "")
                
                Row(modifier = Modifier.weight(3f).clickable { viewModel.replaceBowler() }, verticalAlignment = Alignment.CenterVertically) {
                    Text(nameWithExtras, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(12.dp), tint = Color.Gray)
                }
                
                Text(it.bowlingStats.formattedOvers, modifier = Modifier.width(30.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall)
                Text("${it.bowlingStats.maidens}", modifier = Modifier.width(30.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall)
                Text("${it.bowlingStats.noBalls}", modifier = Modifier.width(30.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall)
                Text("${it.bowlingStats.wides}", modifier = Modifier.width(30.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall)
                Text("${it.bowlingStats.runsConceded}", modifier = Modifier.width(30.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall)
                Text("${it.bowlingStats.wickets}", modifier = Modifier.width(30.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Text(String.format(Locale.getDefault(), "%.2f", it.bowlingStats.economy), modifier = Modifier.width(40.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall)
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
        Text(String.format(Locale.getDefault(), "%.1f", sr), modifier = Modifier.width(40.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ControlsSection(
    viewModel: ScoringViewModel,
    onShowWicket: () -> Unit,
    onShowExtraRuns: (ExtrasType) -> Unit,
    onShowOtherRuns: () -> Unit,
    onShowRetireHurt: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Row 1: DOT, 1, 1D, 2, 3, 4, 6
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RunButton(runs = 0, modifier = Modifier.weight(1f), label = "DOT") { viewModel.handleRuns(0, true) }
            RunButton(runs = 1, modifier = Modifier.weight(1f)) { viewModel.handleRuns(1, true) }
            RunButton(
                runs = 1, 
                modifier = Modifier.weight(1f), 
                label = "1D",
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) { viewModel.handleRuns(1, rotateStrike = false) }
            RunButton(runs = 2, modifier = Modifier.weight(1f)) { viewModel.handleRuns(2, true) }
            RunButton(runs = 3, modifier = Modifier.weight(1f)) { viewModel.handleRuns(3, true) }
            RunButton(runs = 4, modifier = Modifier.weight(1f), label = "4 💥") { viewModel.handleRuns(4, true) }
            RunButton(runs = 6, modifier = Modifier.weight(1f), label = "6 💥") { viewModel.handleRuns(6, true) }
        }
        // Row 2: WIDE, NO-BALL, BYE, L-BYE, OVERTHROW
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExtraButton("WIDE", ExtrasType.WIDE, onShowExtraRuns, modifier = Modifier.weight(1f))
            ExtraButton("NO-BALL", ExtrasType.NO_BALL, onShowExtraRuns, modifier = Modifier.weight(1.3f))
            ExtraButton("BYE", ExtrasType.BYE, onShowExtraRuns, modifier = Modifier.weight(1f))
            ExtraButton("L-BYE", ExtrasType.LEG_BYE, onShowExtraRuns, modifier = Modifier.weight(1.1f))
            Button(
                onClick = onShowOtherRuns,
                modifier = Modifier.weight(1.8f).height(48.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
            ) {
                Text("⚾ OVERTHROW", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        // Row 3: WICKET (Red), RETIRE HURT (Grey), DROPPED (Orange), SWAP STRIKE
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onShowWicket,
                modifier = Modifier.weight(1.5f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("🏏 WICKET", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
            Button(
                onClick = onShowRetireHurt,
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("🤕 RETIRE", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            }
            Button(
                onClick = { viewModel.handleDroppedCatch() },
                modifier = Modifier.weight(1.1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("🤲 DROP", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            }
            Button(
                onClick = { viewModel.swapStrike() },
                modifier = Modifier.weight(1.1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("🔄 SWAP", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
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
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        contentPadding = PaddingValues(0.dp),
        shape = CircleShape,
        colors = if (containerColor != null) ButtonDefaults.filledTonalButtonColors(containerColor = containerColor) else ButtonDefaults.filledTonalButtonColors()
    ) {
        Text(label ?: "$runs", fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ExtraButton(label: String, type: ExtrasType, onShowExtraRuns: (ExtrasType) -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = { onShowExtraRuns(type) },
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
    val singles: Int = 0,
    val doubles: Int = 0,
    val triples: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    val dots: Int = 0,
    val extras: Int = 0,
    val wickets: Int = 0
)

fun calculateInningsStats(balls: List<Ball>): InningsStats {
    var singles = 0; var doubles = 0; var triples = 0; var fours = 0; var sixes = 0; var dots = 0; var extras = 0; var wickets = 0
    balls.forEach { ball ->
        if (ball.wicketType != WicketType.NONE && ball.wicketType != WicketType.RETIRED_HURT) wickets++
        if (ball.extrasType != ExtrasType.NONE) extras += ball.extraRuns
        
        val runs = ball.runs
        when (runs) {
            0 -> if (ball.extrasType == ExtrasType.NONE) dots++
            1 -> singles++
            2 -> doubles++
            3 -> triples++
            4 -> fours++
            6 -> sixes++
        }
    }
    return InningsStats(singles, doubles, triples, fours, sixes, dots, extras, wickets)
}

fun calculatePartnerships(balls: List<Ball>, teamPlayers: List<Player>): List<Partnership> {
    val partnerships = mutableListOf<Partnership>()
    if (balls.isEmpty()) return partnerships
    
    var currentB1Id: String? = null
    var currentB2Id: String? = null
    var runs1 = 0; var balls1 = 0
    var runs2 = 0; var balls2 = 0
    
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
        
        if (ball.wicketType != WicketType.NONE && ball.wicketType != WicketType.RETIRED_HURT) {
            val b1Name = teamPlayers.find { it.id == currentB1Id }?.name ?: "Unknown"
            val b2Name = teamPlayers.find { it.id == currentB2Id }?.name ?: "Unknown"
            partnerships.add(Partnership(
                currentB1Id, b1Name, runs1, balls1,
                currentB2Id!!, b2Name, runs2, balls2,
                runs1 + runs2 + ball.extraRuns, balls1 + balls2
            ))
            currentB1Id = null; currentB2Id = null
            runs1 = 0; balls1 = 0; runs2 = 0; balls2 = 0
        }
    }
    
    if (currentB1Id != null) {
        val b1Name = teamPlayers.find { it.id == currentB1Id }?.name ?: "Unknown"
        val b2Name = teamPlayers.find { it.id == currentB2Id }?.name ?: "Unknown"
        partnerships.add(Partnership(
            currentB1Id, b1Name, runs1, balls1,
            currentB2Id!!, b2Name, runs2, balls2,
            runs1 + runs2, balls1 + balls2
        ))
    }
    
    return partnerships
}

@Composable
fun PartnershipsSection(match: Match, team1: Team, team2: Team) {
    val battingTeam = if (match.battingTeamId == team1.id) team1 else team2
    val splitIdx = match.innings1Data?.recordedBallsCount ?: match.ballHistory.size
    val currentInningsBalls = if (match.currentInnings == 1) match.ballHistory.take(splitIdx) else match.ballHistory.drop(splitIdx)
    val partnerships = calculatePartnerships(currentInningsBalls, battingTeam.players)

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("PARTNERSHIPS", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            partnerships.forEach { p ->
                PartnershipRow(p, match)
                HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
            }
        }
    }
}

@Composable
fun PartnershipRow(p: Partnership, match: Match) {
    val battingTeam = if (match.battingTeamId == match.teamA.id) match.teamA else match.teamB
    val b1 = battingTeam.players.find { it.id == p.batter1Id }
    val b2 = battingTeam.players.find { it.id == p.batter2Id }
    val b1Style = b1?.battingStyle?.name?.take(1) ?: "R"
    val b2Style = b2?.battingStyle?.name?.take(1) ?: "R"

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(p.batter1Name + " ($b1Style)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            Text("${p.batter1Runs} (${p.batter1Balls})", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${p.totalRuns}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text("${p.totalBalls} balls", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(p.batter2Name + " ($b2Style)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            Text("${p.batter2Runs} (${p.batter2Balls})", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
fun ScoringBreakdownCard(i1Stats: InningsStats, i2Stats: InningsStats) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("SCORING BREAKDOWN", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            BreakdownRow("Dots", "${i1Stats.dots}", "${i2Stats.dots}")
            BreakdownRow("1s", "${i1Stats.singles}", "${i2Stats.singles}")
            BreakdownRow("4s", "${i1Stats.fours}", "${i2Stats.fours}")
            BreakdownRow("6s", "${i1Stats.sixes}", "${i2Stats.sixes}")
            BreakdownRow("Extras", "${i1Stats.extras}", "${i2Stats.extras}")
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
    val allBatters = (teamA.players + teamB.players).filter { it.battingStats.balls > 0 }.sortedByDescending { it.battingStats.runs }.take(3)
    Column {
        Text("TOP BATTERS", fontWeight = FontWeight.Black)
        Spacer(modifier = Modifier.height(8.dp))
        allBatters.forEach { player ->
            Text("${player.name} (${(player.battingStyle ?: BattingStyle.RHB).name.take(1)}): ${player.battingStats.runs} (${player.battingStats.balls})", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun BestPerformancesBowlers(teamA: Team, teamB: Team) {
    val allBowlers = (teamA.players + teamB.players).filter { it.bowlingStats.overs > 0 || it.bowlingStats.balls > 0 }.sortedByDescending { it.bowlingStats.wickets }.take(3)
    Column {
        Text("TOP BOWLERS", fontWeight = FontWeight.Black)
        Spacer(modifier = Modifier.height(8.dp))
        allBowlers.forEach { player ->
            val bStyle = (player.battingStyle ?: BattingStyle.RHB).name.take(1)
            val bowlStyle = if ((player.bowlingStyle ?: BowlingStyle.RightArm) == BowlingStyle.RightArm) "RA" else "LA"
            Text("${player.name} ($bStyle, $bowlStyle): ${player.bowlingStats.wickets}/${player.bowlingStats.runsConceded}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun BallBox(ball: Ball, onClick: () -> Unit) {
    val bgColor = when {
        ball.wicketType != WicketType.NONE && ball.wicketType != WicketType.RETIRED_HURT -> Color(0xFFFFEBEE)
        ball.extrasType != ExtrasType.NONE -> Color(0xFFFFF3E0)
        else -> Color.White
    }
    val textColor = when {
        ball.wicketType != WicketType.NONE && ball.wicketType != WicketType.RETIRED_HURT -> Color.Red
        ball.extrasType != ExtrasType.NONE -> Color(0xFFE65100)
        else -> Color.Black
    }
    val text = when {
        ball.wicketType == WicketType.RETIRED_HURT -> "RH"
        ball.wicketType != WicketType.NONE -> "W"
        ball.extrasType == ExtrasType.WIDE -> "${ball.runs + ball.extraRuns}wd"
        ball.extrasType == ExtrasType.NO_BALL -> "${ball.runs + ball.extraRuns}nb"
        ball.extrasType == ExtrasType.BYE -> "${ball.runs}b"
        ball.extrasType == ExtrasType.LEG_BYE -> "${ball.runs}lb"
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
private fun CardBranding() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text("Prepared by Ankoji | v1.59", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun WicketDialog(match: Match, viewModel: ScoringViewModel, onDismiss: () -> Unit) {
    var selectedType by remember(match.id) { mutableStateOf(WicketType.BOWLED) }
    var selectedOutPlayerId by remember(match.id) { mutableStateOf(match.strikerId) }

    val battingTeam = if (match.battingTeamId == match.teamA.id) match.teamA else match.teamB
    val striker = battingTeam.players.find { it.id == match.strikerId }
    val nonStriker = battingTeam.players.find { it.id == match.nonStrikerId }

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
                            if (type != WicketType.RUN_OUT) {
                                viewModel.handleWicket(type, match.strikerId)
                                onDismiss()
                            } else {
                                selectedType = type
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(type.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() })
                    }
                }
                
                if (selectedType == WicketType.RUN_OUT) {
                    Spacer(Modifier.height(8.dp))
                    Text("Who is out?", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        striker?.let {
                            Button(
                                onClick = { 
                                    viewModel.handleWicket(WicketType.RUN_OUT, it.id)
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Striker: ${it.name}")
                            }
                        }
                        nonStriker?.let {
                            Button(
                                onClick = { 
                                    viewModel.handleWicket(WicketType.RUN_OUT, it.id)
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Non-Striker: ${it.name}")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            // v1.49: Removed Confirm button for immediate action
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
        title = { Text("Special Runs / Overthrow", fontWeight = FontWeight.Black) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Select runs (Strike will rotate if odd)", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Spacer(Modifier.height(24.dp))
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
        title = { Text("Retire Hurt", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Who is retiring hurt?")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("CANCEL") }
        }
    )
}
