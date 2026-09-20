package com.example.cricketscorer.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.cricketscorer.*
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun ScoringOverlaysContainer(
    uiState: MatchUiState,
    viewModel: ScoringViewModel,
    showManageSquads: Boolean,
    showWicketDialog: Boolean,
    showRetireHurtDialog: Boolean,
    showExtraRunsDialog: ExtrasType?,
    showOtherRunsDialog: Boolean,
    showOversDialog: Boolean,
    showMatchFinishedDialog: Boolean,
    onDismissManageSquads: () -> Unit,
    onDismissWicket: () -> Unit,
    onDismissRetireHurt: () -> Unit,
    onDismissExtraRuns: () -> Unit,
    onDismissOtherRuns: () -> Unit,
    onDismissOvers: () -> Unit,
    onDismissMatchFinished: () -> Unit,
    onDismissOverSummary: () -> Unit,
    onNavigateToDashboard: () -> Unit
) {
    val match = uiState.match ?: return

    if (match.pendingAction == PendingAction.TOSS_REQUIRED || match.pendingAction == PendingAction.SELECT_MATCH_SETTINGS) {
        MatchSettingsDialog(uiState, viewModel, onDismiss = onNavigateToDashboard)
    } else if (match.pendingAction == PendingAction.START_SECOND_INNINGS) {
        InningsOverOverlay(uiState, viewModel)
    } else if (uiState.finishedOverSummary != null) {
        // v2.33.4: Priority - Show Over Summary only if no major match transitions are pending 🏏🚀⚖️🏅
        OverCompletedOverlay(uiState.finishedOverSummary, onDismiss = onDismissOverSummary)
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
            onDismiss = onDismissMatchFinished
        )
    }

    if (showManageSquads) {
        ManageSquadsOverlay(uiState, viewModel, onDismiss = onDismissManageSquads)
    }

    if (showWicketDialog) {
        WicketDialog(uiState, viewModel, onDismiss = onDismissWicket)
    }

    if (showRetireHurtDialog) {
        RetireHurtDialog(uiState, viewModel, onDismiss = onDismissRetireHurt)
    }

    showExtraRunsDialog?.let { type: ExtrasType ->
        ExtraRunsDialog(type, viewModel, onDismiss = onDismissExtraRuns)
    }

    if (showOtherRunsDialog) {
        OtherRunsDialog(viewModel, onDismiss = onDismissOtherRuns)
    }

    if (showOversDialog) {
        MatchSettingsDialog(uiState, viewModel, onDismiss = onDismissOvers)
    }
}


@Composable
fun InningsOverOverlay(uiState: MatchUiState, viewModel: ScoringViewModel) {
    val match = uiState.match ?: return
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
fun OverCompletedOverlay(summary: OverSummary, onDismiss: () -> Unit) {
    LaunchedEffect(summary) {
        delay(2000)
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { },
        text = {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D47A1)), // Deep Blue 🏏🚀⚖️🏅
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "END OF OVER ${summary.overNumber}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.6f),
                        letterSpacing = 1.sp
                    )
                    
                    // v2.33.4: Highly Prominent Team Score 🏆🏏🚀⚖️🏅
                    if (summary.battingTeamName.isNotBlank()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = summary.battingTeamName.uppercase(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Text(
                                text = "${summary.teamTotalRuns}/${summary.teamTotalWickets}",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(modifier = Modifier.width(100.dp), thickness = 1.dp, color = Color.White.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        "OVER STATS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.5f)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${summary.runs}",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Text(
                                "RUNS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val wicketColor = if (summary.wickets > 0) Color(0xFFFF5252) else Color.White
                            Text(
                                "${summary.wickets}",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Black,
                                color = wicketColor
                            )
                            Text(
                                "WICKETS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }

                    // v2.33.2: Ball sequence display for Over Summary 🏏🚀⚖️🏅
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        summary.ballLabels.forEach { label ->
                            val color = when {
                                label.contains("W") -> Color(0xFFD32F2F)
                                label == "4" -> Color(0xFF1976D2)
                                label == "6" -> Color(0xFF7B1FA2)
                                label.contains("wd") || label.contains("nb") -> Color(0xFFF57C00)
                                else -> Color.White.copy(alpha = 0.1f)
                            }
                            val textColor = Color.White
                            
                            Surface(
                                modifier = Modifier.size(32.dp).padding(horizontal = 2.dp),
                                shape = CircleShape,
                                color = color,
                                border = if (color == Color.White.copy(alpha = 0.1f)) BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)) else null
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (label == "0") "•" else label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = textColor,
                                        fontSize = if (label.length > 2) 8.sp else 10.sp
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0D47A1)),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("TAP TO CONTINUE", fontWeight = FontWeight.Black)
                    }
                }
            }
        },
        confirmButton = { },
        properties = DialogProperties(usePlatformDefaultWidth = false)
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
    var step by remember { mutableIntStateOf(1) }

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
fun PlayerSelectionOverlay(uiState: MatchUiState, viewModel: ScoringViewModel) {
    val match = uiState.match ?: return
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
            if (match.battingTeamId == match.teamA.id) match.teamA 
            else if (match.battingTeamId == match.teamB.id) match.teamB
            else match.teamA
    }

    // v2.33.3: Bottom-aligned selection overlay to keep scoreboard visible 🏏🚀⚖️🏅
    Dialog(
        onDismissRequest = { viewModel.cancelPendingAction() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 12.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        IconButton(onClick = { viewModel.cancelPendingAction() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    val isBowlerAction = match.pendingAction == PendingAction.SELECT_BOWLER || match.pendingAction == PendingAction.REPLACE_BOWLER
                    
                    val displayedPlayers = if (isBowlerAction) {
                        // Show ALL bowlers but handle restrictions in the UI 🏏🚀⚖️🏅
                        team.players
                    } else {
                        team.players.filter { player ->
                            when (match.pendingAction ?: PendingAction.NONE) {
                                PendingAction.SELECT_STRIKER, PendingAction.SELECT_NON_STRIKER,
                                PendingAction.REPLACE_STRIKER, PendingAction.REPLACE_NON_STRIKER ->
                                    !player.battingStats.isOut && 
                                    player.id != match.strikerId && player.id != match.nonStrikerId
                                else -> true
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp) // Reduced height v2.33.3
                    ) {
                        if (displayedPlayers.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    Text("No players found in this team.", textAlign = TextAlign.Center, color = Color.Gray)
                                }
                            }
                        } else {
                            items(displayedPlayers) { player ->
                                val isLastBowler = isBowlerAction && player.id == match.lastBowlerId
                                val isMaxedOut = isBowlerAction && viewModel.isSpellCompleted(player, match)
                                val isDisabled = isLastBowler || isMaxedOut
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer { alpha = if (isDisabled && isBowlerAction) 0.6f else 1f }
                                        .clickable(enabled = !isDisabled || !isBowlerAction) {
                                            if (match.pendingAction == PendingAction.SELECT_FIELDER || match.pendingAction == PendingAction.SELECT_FIELDER_DROPPED_CATCH) {
                                                viewModel.selectFielder(player.id)
                                            } else {
                                                viewModel.assignPlayerToAction(player.id)
                                            }
                                            Toast.makeText(context, "${player.name} selected! ✅", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(if (isBowlerAction) 44.dp else 40.dp),
                                        shape = CircleShape,
                                        color = if (isLastBowler) Color.LightGray else MaterialTheme.colorScheme.primaryContainer,
                                        border = if (isLastBowler) BorderStroke(1.dp, Color.Gray) else null
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(player.name.take(1).uppercase(), fontWeight = FontWeight.Black)
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        val isWK = player.id == match.teamAWicketKeeperId || player.id == match.teamBWicketKeeperId
                                        val roleSuffix = if (player.isCaptain) " (c)" else if (player.isViceCaptain) " (vc)" else ""
                                        Text(
                                            text = player.name + roleSuffix + (if (isWK) " 🧤" else ""), 
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        
                                        if (isBowlerAction) {
                                            val stats = player.bowlingStats
                                            val maxOvers = match.maxOversPerBowler
                                            val oversLabel = if (maxOvers != null) "${stats.formattedOvers} / $maxOvers ov" else "${stats.formattedOvers} ov"
                                            
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "$oversLabel • ${stats.wickets}W • ER: ${String.format(
                                                        Locale.US, "%.2f", stats.economy)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isMaxedOut) Color.Red else Color.Gray,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        } else {
                                            val action = match.pendingAction ?: PendingAction.NONE
                                            val isFielderAction = action == PendingAction.SELECT_FIELDER || action == PendingAction.SELECT_FIELDER_DROPPED_CATCH
                                            val bStyle = (player.battingStyle ?: BattingStyle.RHB).name
                                            Text(
                                                text = if (isFielderAction) "Fielder" else "Batting: $bStyle", 
                                                style = MaterialTheme.typography.bodyMedium, 
                                                fontWeight = FontWeight.Bold,
                                                color = if (isFielderAction) MaterialTheme.colorScheme.primary else Color.Gray
                                            )
                                        }
                                    }
                                    
                                    if (isBowlerAction) {
                                        when {
                                            isLastBowler -> Badge(containerColor = Color.Gray, contentColor = Color.White) { Text("LAST OVER") }
                                            isMaxedOut -> Badge(containerColor = Color.Red, contentColor = Color.White) { Text("MAXED") }
                                        }
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
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }

                    TextButton(
                        onClick = { viewModel.cancelPendingAction() },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("CANCEL", fontWeight = FontWeight.Black, color = Color.Red)
                    }
                }
            }
        }
    }
}

@Composable
fun ManageSquadsOverlay(uiState: MatchUiState, viewModel: ScoringViewModel, onDismiss: () -> Unit) {
    val match = uiState.match ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Squads", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SquadList(uiState, match.teamA, "Team A: ${match.teamA.name}", viewModel)
                SquadList(uiState, match.teamB, "Team B: ${match.teamB.name}", viewModel)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("CLOSE") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquadList(uiState: MatchUiState, team: Team, title: String, viewModel: ScoringViewModel) {
    val match = uiState.match ?: return
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
                            IconButton(onClick = { viewModel.deletePlayerFromMatch(context, player.id) }, modifier = Modifier.size(24.dp)) {
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
                            val currentTeamPlayerNames = team.players.map { it.name.lowercase() }
                            val filteredGlobal = globalPlayers.filter { gp -> gp.name.lowercase() !in currentTeamPlayerNames }
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
                                            viewModel.addGlobalPlayersToMatch(context, selectedPlayers.toList(), team.id)
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
                                viewModel.addNewPlayerToMatch(context, newPlayerName, selectedStyle, team.id)
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
