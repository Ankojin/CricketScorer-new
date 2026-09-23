package com.example.cricketscorer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cricketscorer.*
import com.example.cricketscorer.ui.colorOrDefault
import com.example.cricketscorer.ui.parseTeamColor
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
fun CompactLiveScoreHeader(uiState: MatchUiState) {
    val match = uiState.match ?: return
    val battingTeam = if (match.battingTeamId == match.teamA.id) match.teamA else match.teamB

    val colorA = match.teamA.colorOrDefault(MaterialTheme.colorScheme.primary)
    val colorB = match.teamB.colorOrDefault(MaterialTheme.colorScheme.secondary)
    val battingColor = battingTeam.colorOrDefault(MaterialTheme.colorScheme.primary)

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Line 1: TeamA vs TeamB
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = match.teamA.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = colorA,
                    maxLines = 1
                )
                Text(
                    text = " vs ",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    fontSize = 10.sp
                )
                Text(
                    text = match.teamB.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = colorB,
                    maxLines = 1
                )
                if (match.tossWinnerId != null) {
                    val tossWinnerName = if (match.tossWinnerId == match.teamA.id) match.teamA.name else match.teamB.name
                    Text(
                        text = " • $tossWinnerName won toss",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
            }

            // Line 2: Batting Team + Score "R/W" + Overs "(o.b Ov)" + CRR
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = battingTeam.name.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = battingColor,
                    maxLines = 1
                )
                Text(
                    text = "${match.totalRuns}/${match.totalWickets}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "(${match.totalBalls / 6}.${match.totalBalls % 6} Ov)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
                val crr = if (match.totalBalls > 0) (match.totalRuns.toDouble() / match.totalBalls) * 6 else 0.0
                Text(
                    text = "CRR: ${String.format(Locale.US, "%.1f", crr)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
            }

            // Line 3: Target info if 2nd innings
            if (match.currentInnings == 2 && match.target != null && match.status != MatchStatus.COMPLETED) {
                val needed = match.target - match.totalRuns
                val ballsLeft = (match.oversPerInnings * 6) - match.totalBalls
                Text(
                    text = "Target ${match.target} • Need $needed off $ballsLeft b",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                    maxLines = 1
                )
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // TOP — compact score (not scrollable)
        CompactLiveScoreHeader(uiState)

        // MIDDLE — scroll only player stats
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlayerStatsSection(uiState, viewModel)

            if (match.status == MatchStatus.COMPLETED) {
                val winnerTeam = if (match.winnerId == match.teamA.id) match.teamA else if (match.winnerId == match.teamB.id) match.teamB else null
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("MATCH SUMMARY", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (winnerTeam != null) "${winnerTeam.name} 🏅" else "MATCH DRAWN 🤝",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                MatchForecasterSection(uiState)
            }

            CardBranding()
        }

        // BOTTOM — controls always visible
        if (match.status != MatchStatus.COMPLETED) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                tonalElevation = 2.dp
            ) {
                Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
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
        }
    }
}

@Composable
fun ScoreCard(uiState: MatchUiState) {
    val match = uiState.match ?: return
    val battingTeam = if (match.battingTeamId == match.teamA.id) match.teamA else match.teamB
    val bowlingTeam = if (match.battingTeamId == match.teamA.id) match.teamB else match.teamA
    
    val teamColor = battingTeam.colorOrDefault(MaterialTheme.colorScheme.primary)
    val isLightColor = teamColor.luminance() > 0.5f
    val contentColor = if (isLightColor) Color.Black else Color.White

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = teamColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                battingTeam.name.uppercase(),
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor.copy(alpha = 0.8f)
            )
            
            val totalRuns = match.totalRuns
            val totalWickets = match.totalWickets
            val totalBalls = match.totalBalls
            val target = match.target
            val status = match.status
            val oversPerInnings = match.oversPerInnings
            val innings1Data = match.innings1Data

            val scoreText by remember(totalRuns, totalWickets) {
                derivedStateOf { "$totalRuns/$totalWickets" }
            }
            val oversString by remember(totalBalls) {
                derivedStateOf { "(${totalBalls / 6}.${totalBalls % 6})" }
            }
            val crrText by remember(totalRuns, totalBalls) {
                derivedStateOf {
                    val crr = if (totalBalls > 0) (totalRuns.toDouble() / totalBalls) * 6 else 0.0
                    "CRR: ${String.format(Locale.US, "%.2f", crr)}"
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Text(text = scoreText, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = oversString, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = contentColor.copy(alpha = 0.8f))
                    Text(text = crrText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }

            if (match.currentInnings == 2) {
                val neededText by remember(target, totalRuns, totalBalls, oversPerInnings, status) {
                    derivedStateOf {
                        val needed = (target ?: 0) - totalRuns
                        val ballsLeft = (oversPerInnings * 6) - totalBalls
                        if (needed > 0 && status != MatchStatus.COMPLETED) {
                            "Need $needed off $ballsLeft balls"
                        } else ""
                    }
                }
                if (neededText.isNotEmpty()) {
                    Text(
                        text = neededText,
                        fontWeight = FontWeight.Black,
                        color = if (isLightColor) Color(0xFFD32F2F) else Color(0xFFFFCDD2),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                
                // Show Innings 1 Score for context
                innings1Data?.let { i1 ->
                    Text(
                        text = "Target: $target (${bowlingTeam.name}: ${i1.runs}/${i1.wickets})",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Medium
                    )
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

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Row 1: DOT, 1, 1G, 2, 3, 4, 6
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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

        // Row 2: WIDE, NO-BALL, BYE, L-BYE, DROP, OVERTHROW
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExtraButton("WIDE", ExtrasType.WIDE, viewModel, onShowExtraRuns, modifier = Modifier.weight(1f), enabled = !isCompleted)
            ExtraButton("NO-BALL", ExtrasType.NO_BALL, viewModel, onShowExtraRuns, modifier = Modifier.weight(1.2f), enabled = !isCompleted)
            ExtraButton("BYE", ExtrasType.BYE, viewModel, onShowExtraRuns, modifier = Modifier.weight(1f), enabled = !isCompleted)
            ExtraButton("L-BYE", ExtrasType.LEG_BYE, viewModel, onShowExtraRuns, modifier = Modifier.weight(1f), enabled = !isCompleted)
            Button(
                onClick = { viewModel.handleDroppedCatch() },
                enabled = !isCompleted,
                modifier = Modifier.weight(1.1f).height(42.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
            ) {
                Text("🤲 DROP", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 11.sp)
            }
            Button(
                onClick = onShowOtherRuns,
                enabled = !isCompleted,
                modifier = Modifier.weight(1.4f).height(42.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
            ) {
                Text("⚾ OVERTHROW", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 11.sp)
            }
        }

        // Row 3: WICKET (Red), RETIRE HURT (Grey), UNDO
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = onShowWicket,
                modifier = Modifier.weight(1.4f).height(44.dp),
                enabled = !isCompleted,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F), contentColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text("🏏 ", fontSize = 16.sp)
                    Text("WICKET", fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }
            Button(
                onClick = onShowRetireHurt,
                modifier = Modifier.weight(1.1f).height(44.dp),
                enabled = !isCompleted,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("🤕 RETIRE", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1)
            }
            Button(
                onClick = { viewModel.undo(androidContext) },
                modifier = Modifier.weight(1.1f).height(44.dp),
                enabled = !isCompleted,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("⏪ UNDO", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}
