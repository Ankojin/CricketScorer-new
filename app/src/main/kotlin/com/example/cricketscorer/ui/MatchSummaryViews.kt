package com.example.cricketscorer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cricketscorer.*
import com.example.cricketscorer.ui.colorOrDefault
import java.util.Locale

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

    val colorA = teamA.colorOrDefault(MaterialTheme.colorScheme.primary)
    val colorB = teamB.colorOrDefault(MaterialTheme.colorScheme.secondary)

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = colorA) {}
                    Spacer(Modifier.width(8.dp))
                    Text(teamA?.name ?: "Team A", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                }
                Text(getScoreString(teamA?.id), fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = colorB) {}
                    Spacer(Modifier.width(8.dp))
                    Text(teamB?.name ?: "Team B", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                }
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
                        Text(String.format(Locale.US, "%.0f", mvpEntry?.value ?: 0.0), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black, color = Color(0xFFFFD700))
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
                Text(teamA.name.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = teamA.colorOrDefault(MaterialTheme.colorScheme.primary))
                Text(teamB.name.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = teamB.colorOrDefault(MaterialTheme.colorScheme.secondary))
            }
            Spacer(Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth().height(10.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxHeight().weight(teamAWinProb.toFloat()).background(teamA.colorOrDefault(MaterialTheme.colorScheme.primary), RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp)))
                    Box(modifier = Modifier.fillMaxHeight().weight(teamBWinProb.toFloat()).background(teamB.colorOrDefault(MaterialTheme.colorScheme.secondary), RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp)))
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${teamAWinProb.toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = teamA.colorOrDefault(MaterialTheme.colorScheme.primary))
                Text("${teamBWinProb.toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = teamB.colorOrDefault(MaterialTheme.colorScheme.secondary))
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), thickness = 0.5.dp)
            
            // Projected Scores
            val label = if (match.currentInnings == 2) "PAR SCORE: ${match.target}" else "PROJECTED SCORE"
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Gray)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ForecastItem("At ${String.format(Locale.US, "%.1f", crr)} RPO", projectedAtCurrent.toInt().toString(), Modifier.weight(1f))
                ForecastItem("At 10.0 RPO", projectedAt10.toInt().toString(), Modifier.weight(1f))
                if (match.currentInnings == 2) {
                    val rrr = if (remainingBalls > 0) ( (match.target!! - match.totalRuns).toDouble() / remainingBalls ) * 6 else 0.0
                    ForecastItem("RRR", String.format(Locale.US, "%.2f", rrr), Modifier.weight(1f), isHighlight = true)
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
