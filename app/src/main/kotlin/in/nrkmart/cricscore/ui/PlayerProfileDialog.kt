package `in`.nrkmart.cricscore.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import `in`.nrkmart.cricscore.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerProfileDialog(
    playerId: String,
    tournaments: List<Tournament>,
    globalPlayer: Player? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()
    var shareTrigger by remember { mutableIntStateOf(0) }

    val stats = remember(playerId, tournaments, globalPlayer) {
        PlayerStatsCalculator.calculatePlayerStats(playerId, tournaments, globalPlayer)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // Top Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "PLAYER CAREER PROFILE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Share Button
            Button(
                onClick = {
                    scope.launch {
                        shareTrigger++
                        delay(300.milliseconds)
                        shareComposableScreenshot(
                            context,
                            graphicsLayer,
                            "${stats.playerName}_profile",
                            subject = "${stats.playerName} - Career Stats",
                            shareMessage = "Check out ${stats.playerName}'s career profile from Cricket League app! 🏏"
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("SHARE PLAYER PROFILE", fontWeight = FontWeight.Bold)
            }

            // Captured Content Canvas
            Box(
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
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header Profile Card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(54.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                stats.playerName.take(1).uppercase(),
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                stats.playerName,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (stats.isJoker) {
                                                Spacer(Modifier.width(6.dp))
                                                Text("🃏", fontSize = 16.sp)
                                            }
                                        }
                                        val roleLabel = stats.role.name.lowercase().replaceFirstChar { it.titlecase() }
                                        val styleLabel = "Bat: ${stats.battingStyle.name}" + if (stats.bowlingStyle != BowlingStyle.NONE) " · Bowl: ${stats.bowlingStyle.name}" else ""
                                        Text(
                                            "$roleLabel · $styleLabel",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (stats.currentTeamName != null) {
                                            Text(
                                                "Team: ${stats.currentTeamName}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Quick Highlight Stat Bar
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                QuickStatBox("MATCHES", "${stats.matchesPlayed}", Modifier.weight(1f))
                                QuickStatBox("RUNS", "${stats.totalRuns}", Modifier.weight(1f))
                                QuickStatBox("AVG", if (stats.battingAverage > 0) String.format(Locale.US, "%.1f", stats.battingAverage) else "-", Modifier.weight(1f))
                                QuickStatBox("WKTS", "${stats.wicketsTaken}", Modifier.weight(1f))
                                QuickStatBox("ECO", if (stats.bowlingEconomy > 0) String.format(Locale.US, "%.1f", stats.bowlingEconomy) else "-", Modifier.weight(1f))
                            }
                        }

                        // Batting Breakdown Card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("BATTING STATS 🏏", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(12.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        StatColumn("Innings", "${stats.inningsBatted}")
                                        StatColumn("Runs", "${stats.totalRuns}")
                                        StatColumn("High Score", "${stats.highestScore}${if (stats.isHighestScoreNotOut) "*" else ""}")
                                        StatColumn("Not Outs", "${stats.notOuts}")
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                    Spacer(Modifier.height(12.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        StatColumn("Average", String.format(Locale.US, "%.2f", stats.battingAverage))
                                        StatColumn("Strike Rate", String.format(Locale.US, "%.1f", stats.battingStrikeRate))
                                        StatColumn("50s / 100s", "${stats.fifties} / ${stats.hundreds}")
                                        StatColumn("4s / 6s", "${stats.fours} / ${stats.sixes}")
                                    }
                                }
                            }
                        }

                        // Bowling Breakdown Card
                        if (stats.totalBallsBowled > 0 || stats.wicketsTaken > 0) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("BOWLING STATS 🎯", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.height(12.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            StatColumn("Overs", String.format(Locale.US, "%.1f", stats.totalOversBowled))
                                            StatColumn("Wickets", "${stats.wicketsTaken}")
                                            StatColumn("Best Bowling", "${stats.bestBowlingWickets}/${stats.bestBowlingRuns}")
                                            StatColumn("Runs Conceded", "${stats.runsConceded}")
                                        }
                                        Spacer(Modifier.height(12.dp))
                                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                        Spacer(Modifier.height(12.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            StatColumn("Economy", String.format(Locale.US, "%.2f", stats.bowlingEconomy))
                                            StatColumn("Average", if (stats.bowlingAverage > 0) String.format(Locale.US, "%.2f", stats.bowlingAverage) else "-")
                                            StatColumn("Dot Balls", "${stats.dotBalls}")
                                            StatColumn("Maidens", "${stats.maidens}")
                                        }
                                    }
                                }
                            }
                        }

                        // Fielding Stats Card
                        if (stats.catches > 0 || stats.stumpings > 0 || stats.runOuts > 0) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("FIELDING 🧤", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.height(12.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            StatColumn("Catches", "${stats.catches}")
                                            StatColumn("Stumpings", "${stats.stumpings}")
                                            StatColumn("Run-Out Assists", "${stats.runOuts}")
                                        }
                                    }
                                }
                            }
                        }

                        // Recent Match History Log Header
                        if (stats.matchLogs.isNotEmpty()) {
                            item {
                                Text("RECENT MATCH LOGS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            items(stats.matchLogs.take(5)) { log ->
                                MatchLogCard(log)
                            }
                        }

                        item {
                            CardBranding()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickStatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun MatchLogCard(log: PlayerMatchLog) {
    val dateStr = remember(log.dateMillis) {
        try {
            SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(log.dateMillis))
        } catch (e: Exception) { "" }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("vs ${log.opponentName}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                if (dateStr.isNotBlank()) {
                    Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!log.isDidNotBat) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${log.runs}${if (!log.isOut) "*" else ""} (${log.balls}b)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        Text("${log.fours}x4 · ${log.sixes}x6", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text("DNB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (log.oversBowled > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${log.wicketsTaken}/${log.runsConceded}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary)
                        Text("${log.oversBowled} ov", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
