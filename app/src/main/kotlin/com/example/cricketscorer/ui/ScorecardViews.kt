package com.example.cricketscorer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import com.example.cricketscorer.*
import com.example.cricketscorer.ui.CaptureArea
import com.example.cricketscorer.ui.CardBranding
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun ScorecardTab(
    uiState: MatchUiState,
    viewedInnings: Int,
    graphicsLayer: GraphicsLayer,
    onInningsChange: (Int) -> Unit
) {
    val match = uiState.match ?: return
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
                    delay(300.milliseconds)
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
                                    match.startTimeMillis?.let { ((System.currentTimeMillis() - it) / 60000).toInt() } ?: 0
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
                    text = "$totalOvers Ov (RR: ${String.format(Locale.US, "%.2f", crr)}, $durationMinutes Mins)",
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
                    Text(String.format(Locale.US, "%.1f", player.battingStats.strikeRate), modifier = Modifier.width(45.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End, fontSize = 13.sp)
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
                    Text(String.format(Locale.US, "%.2f", player.bowlingStats.economy), modifier = Modifier.width(45.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End, fontSize = 13.sp)
                }
                HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFF0F0F0))
            }
        }
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
