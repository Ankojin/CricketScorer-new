package com.example.cricketscorer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cricketscorer.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

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
                    delay(300.milliseconds)
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

        val overs = remember(inningsBallsWithIndices) {
            val list = mutableListOf<List<Pair<Int, Ball>>>()
            var currentOver = mutableListOf<Pair<Int, Ball>>()
            inningsBallsWithIndices.forEach { (idx, ball) ->
                currentOver.add(idx to ball)
                if (ball.isPhysicalBall && currentOver.count { it.second.isPhysicalBall } == 6) {
                    list.add(currentOver.toList())
                    currentOver = mutableListOf()
                }
            }
            if (currentOver.isNotEmpty()) list.add(currentOver)
            list
        }

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
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
                item {
                    CaptureArea {
                        Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                            val reversedOvers = overs.reversed()
                            reversedOvers.forEachIndexed { index, overBalls ->
                                val overNum = overs.size - index
                                key(overNum) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
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
                                                    key(idx) {
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
                                }
                            }
                            CardBranding()
                            Spacer(modifier = Modifier.height(32.dp))
                        }
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
