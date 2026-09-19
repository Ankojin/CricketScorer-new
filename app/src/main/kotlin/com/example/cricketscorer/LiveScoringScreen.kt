package com.example.cricketscorer

import com.example.cricketscorer.ui.*
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cricketscorer.ui.MatchSettingsDialog
import com.example.cricketscorer.ui.CoinFlipDialog
import com.example.cricketscorer.ui.MatchCelebrationDialog
import com.example.cricketscorer.ui.WicketDialog
import com.example.cricketscorer.ui.ExtraRunsDialog
import com.example.cricketscorer.ui.OtherRunsDialog
import com.example.cricketscorer.ui.RetireHurtDialog
import com.example.cricketscorer.ui.InningsOverOverlay
import com.example.cricketscorer.ui.DroppedCatchRunsOverlay
import com.example.cricketscorer.ui.RunOutRunsOverlay
import com.example.cricketscorer.ui.PlayerSelectionOverlay
import com.example.cricketscorer.ui.ManageSquadsOverlay
import java.util.Locale

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
                            if (match != null) {
                                MatchTicker(uiState, selectedTabIndex, viewedInnings)
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
        match?.let { 
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

                ScoringOverlaysContainer(
                    uiState = uiState,
                    viewModel = viewModel,
                    showManageSquads = showManageSquads,
                    showWicketDialog = showWicketDialog,
                    showRetireHurtDialog = showRetireHurtDialog,
                    showExtraRunsDialog = showExtraRunsDialog,
                    showOtherRunsDialog = showOtherRunsDialog,
                    showOversDialog = showOversDialog,
                    showMatchFinishedDialog = showMatchFinishedDialog,
                    onDismissManageSquads = { showManageSquads = false },
                    onDismissWicket = { showWicketDialog = false },
                    onDismissRetireHurt = { showRetireHurtDialog = false },
                    onDismissExtraRuns = { showExtraRunsDialog = null },
                    onDismissOtherRuns = { showOtherRunsDialog = false },
                    onDismissOvers = { showOversDialog = false },
                    onDismissMatchFinished = { showMatchFinishedDialog = false },
                    onNavigateToDashboard = onNavigateToDashboard
                )
            }
        } ?: NoActiveMatchState(onNavigateToMatches)
    }
}
