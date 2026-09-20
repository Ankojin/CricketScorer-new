package com.example.cricketscorer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
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

@Composable
fun StatsTab(uiState: MatchUiState, graphicsLayer: GraphicsLayer) {
    val match = uiState.match ?: return
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
                    delay(300.milliseconds)
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
                        MatchSummaryCard(uiState)
                        MotmSection(uiState)
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
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(i1Name.take(12).uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = Color.DarkGray)
                Text(i2Name.take(12).uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = Color.DarkGray)
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
            CricinfoBreakdownRow("Singles", "${i1Stats.singlesRuns}", "${i2Stats.singlesRuns}", if(i1Stats.singlesRuns > i2Stats.singlesRuns) 1 else if(i2Stats.singlesRuns > i1Stats.singlesRuns) 2 else 0)
            CricinfoBreakdownRow("Doubles", "${i1Stats.doublesRuns/2}", "${i2Stats.doublesRuns/2}", if(i1Stats.doublesRuns > i2Stats.doublesRuns) 1 else if(i2Stats.doublesRuns > i1Stats.doublesRuns) 2 else 0)
            CricinfoBreakdownRow("Triples", "${i1Stats.triplesRuns/3}", "${i2Stats.triplesRuns/3}", if(i1Stats.triplesRuns > i2Stats.triplesRuns) 1 else if(i2Stats.triplesRuns > i1Stats.triplesRuns) 2 else 0)
            CricinfoBreakdownRow("Runs In Boundaries", "${i1Stats.boundaryRuns}", "${i2Stats.boundaryRuns}", if(i1Stats.boundaryRuns > i2Stats.boundaryRuns) 1 else if(i2Stats.boundaryRuns > i1Stats.boundaryRuns) 2 else 0)
            CricinfoBreakdownRow("Other Bat Runs", "${i1Stats.otherBatRuns}", "${i2Stats.otherBatRuns}", if(i1Stats.otherBatRuns > i2Stats.otherBatRuns) 1 else if(i2Stats.otherBatRuns > i1Stats.otherBatRuns) 2 else 0)
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.3f))

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
