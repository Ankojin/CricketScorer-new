package com.example.cricketscorer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cricketscorer.*
import java.util.Locale
import androidx.compose.foundation.basicMarquee

@Composable
fun MatchTicker(uiState: MatchUiState, selectedTabIndex: Int, viewedInnings: Int) {
    val match = uiState.match ?: return
    val indicator = when {
        match.status == MatchStatus.COMPLETED -> "MATCH COMPLETED"
        match.pendingAction == PendingAction.START_SECOND_INNINGS -> "1ST INNINGS COMPLETED"
        match.pendingAction != PendingAction.NONE && match.pendingAction != PendingAction.START_SECOND_INNINGS && match.pendingAction != PendingAction.TOSS_REQUIRED -> "ACTION REQUIRED: ${match.pendingAction.toString().replace("_", " ")}"
        selectedTabIndex == 1 && viewedInnings == 1 && match.currentInnings == 2 -> "1ST INNINGS COMPLETED"
        else -> "LIVE"
    }
    val tickerText = if (selectedTabIndex == 1) {
        val team = if (viewedInnings == 1) {
            if (match.initialBattingTeamId == match.teamA.id) match.teamA else match.teamB
        } else {
            if (match.initialBattingTeamId == match.teamA.id) match.teamB else match.teamA
        }
        val runs = if (viewedInnings == 1) (match.innings1Data?.runs ?: if (match.currentInnings == 1) match.totalRuns else 0) else match.totalRuns
        val wkts = if (viewedInnings == 1) (match.innings1Data?.wickets ?: if (match.currentInnings == 1) match.totalWickets else 0) else match.totalWickets
        val balls = if (viewedInnings == 1) (match.innings1Data?.balls ?: if (match.currentInnings == 1) match.totalBalls else 0) else match.totalBalls
        val crr = if (balls > 0) (runs.toDouble() / (balls / 6.0 + (balls % 6) / 6.0)) else 0.0

        "[$indicator] ${team.name} $runs/$wkts (${balls / 6}.${balls % 6}) • CRR: ${String.format(Locale.US, "%.2f", crr)}"
    } else {
        val battingTeamName = if (match.battingTeamId == match.teamA.id) match.teamA.name else match.teamB.name
        val crr = if (match.totalBalls > 0) (match.totalRuns.toDouble() / (match.totalBalls / 6.0 + (match.totalBalls % 6) / 6.0)) else 0.0

        buildString {
            append("[$indicator] $battingTeamName ${match.totalRuns}/${match.totalWickets} (${match.totalBalls / 6}.${match.totalBalls % 6})")
            append(" • CRR: ${String.format(Locale.US, "%.2f", crr)}")
            if (match.currentInnings == 2 && match.status != MatchStatus.COMPLETED) {
                val runsNeeded = (match.target ?: 0) - match.totalRuns
                val ballsRemaining = (match.oversPerInnings * 6) - match.totalBalls
                append(" • Target: ${match.target} • Need $runsNeeded off $ballsRemaining")
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

@Composable
fun NoActiveMatchState(onNavigateToMatches: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
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
        
        item { CardBranding() }
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
                    Text(text = "CRR: ${String.format(Locale.US, "%.2f", crr)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
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
                Text(String.format(Locale.US, "%.2f", it.bowlingStats.economy), modifier = Modifier.width(40.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall, fontSize = 13.sp)
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
        Text(String.format(Locale.US, "%.1f", sr), modifier = Modifier.width(40.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall)
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
                enabled = !isCompleted
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
