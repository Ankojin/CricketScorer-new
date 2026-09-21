package com.example.cricketscorer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cricketscorer.*
import com.example.cricketscorer.R
import kotlinx.coroutines.launch
import java.util.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchSettingsDialog(uiState: MatchUiState, viewModel: ScoringViewModel, onDismiss: (() -> Unit)? = null) {
    val match = uiState.match ?: return
    val currentOvers = match.oversPerInnings
    val currentMaxOvers = match.maxOversPerBowler
    val currentQuotaCount = match.quotaBowlersCount
    val currentQuotaLimit = match.quotaMaxOvers

    var oversText by remember { mutableStateOf(currentOvers.toString()) }
    var maxOversText by remember { mutableStateOf(currentMaxOvers?.toString() ?: "") }
    var quotaCountText by remember { mutableStateOf(currentQuotaCount?.toString() ?: "") }
    var quotaLimitText by remember { mutableStateOf(currentQuotaLimit?.toString() ?: "") }
    
    var tempTossWinnerId by remember(match.id) { mutableStateOf(match.tossWinnerId) }
    var tempTossDecision by remember(match.id) { mutableStateOf(match.tossDecision) }
    var showFlipDialog by remember { mutableStateOf(false) }

    val onActionDismiss = {
        if (onDismiss != null) onDismiss()
        else viewModel.cancelPendingAction()
    }

    AlertDialog(
        onDismissRequest = onActionDismiss,
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
                HorizontalDivider(thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Toss", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Button(onClick = { showFlipDialog = true }, shape = RoundedCornerShape(8.dp)) {
                        Text("FLIP COIN 🪙")
                    }
                }
                
                if (showFlipDialog) {
                    CoinFlipDialog(
                        uiState = uiState,
                        onResult = { winnerId, decision ->
                            tempTossWinnerId = winnerId
                            tempTossDecision = decision
                            showFlipDialog = false
                        },
                        onDismiss = { showFlipDialog = false }
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text("Winner", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = tempTossWinnerId == match.teamA.id, onClick = { tempTossWinnerId = match.teamA.id }, label = { Text(match.teamA.name.uppercase(), fontWeight = FontWeight.Black) })
                    FilterChip(selected = tempTossWinnerId == match.teamB.id, onClick = { tempTossWinnerId = match.teamB.id }, label = { Text(match.teamB.name.uppercase(), fontWeight = FontWeight.Black) })
                }

                Spacer(Modifier.height(8.dp))
                Text("Decision", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = tempTossDecision == "BAT", onClick = { tempTossDecision = "BAT" }, label = { Text("Bat First") })
                    FilterChip(selected = tempTossDecision == "BOWL", onClick = { tempTossDecision = "BOWL" }, label = { Text("Bowl First") })
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val o = oversText.toIntOrNull() ?: currentOvers
                    val mO = maxOversText.toIntOrNull()
                    val qC = quotaCountText.toIntOrNull()
                    val qL = quotaLimitText.toIntOrNull()
                    
                    if (tempTossWinnerId != null && tempTossDecision != null) {
                        viewModel.handleToss(tempTossWinnerId!!, tempTossDecision!!)
                    }
                    
                    viewModel.updateMatchSettings(o, mO, qC, qL)
                    
                    // v2.33.11: Close dialog. 
                    // If it was an automatic pending action, the engine just set it to NONE.
                    // If it was a manual toggle (showOversDialog), we must call the dismiss lambda.
                    if (match.pendingAction == PendingAction.NONE) {
                        onDismiss?.invoke()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("SAVE & START SCORING", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss?.invoke() ?: viewModel.cancelPendingAction() }) {
                Text("CANCEL")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinFlipDialog(uiState: MatchUiState, onResult: (String, String) -> Unit, onDismiss: () -> Unit) {
    val match = uiState.match ?: return
    var winnerId by remember { mutableStateOf<String?>(null) }
    var decision by remember { mutableStateOf<String?>(null) }
    
    var isFlipping by remember { mutableStateOf(false) }
    var coinResult by remember { mutableStateOf<String?>(null) }
    var callerChoice by remember { mutableStateOf<String?>(null) }
    var tossCallerId by remember { mutableStateOf(match.teamA.id) }
    
    val rotation = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Match Toss", fontWeight = FontWeight.Black) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. CALLER SELECTION (Who is calling?)
                Column {
                    Text("Who is calling?", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = tossCallerId == match.teamA.id,
                            onClick = { if (!isFlipping && coinResult == null) tossCallerId = match.teamA.id },
                            label = { Text(match.teamA.name.uppercase(), fontWeight = FontWeight.Bold) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = tossCallerId == match.teamB.id,
                            onClick = { if (!isFlipping && coinResult == null) tossCallerId = match.teamB.id },
                            label = { Text(match.teamB.name.uppercase(), fontWeight = FontWeight.Bold) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 2. SIDE CHOICE (Heads or Tails?)
                Column {
                    val callerName = if (tossCallerId == match.teamA.id) match.teamA.name else match.teamB.name
                    Text("$callerName calls:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf("HEADS", "TAILS").forEach { choice ->
                            FilterChip(
                                selected = callerChoice == choice,
                                onClick = { if (!isFlipping) callerChoice = choice },
                                label = { Text(choice, fontWeight = FontWeight.Black) },
                                enabled = !isFlipping && coinResult == null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 3. THE ANIMATED COIN
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .graphicsLayer {
                                rotationY = rotation.value
                                scaleX = scale.value
                                scaleY = scale.value
                                cameraDistance = 12f * density
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // v2.33.17: Precise Image Mapping. 0/360deg = HEADS, 180/540deg = TAILS 🏏🚀⚖️🏅
                        val rotationVal = rotation.value.toInt()
                        val isHeadsVisible = (rotationVal / 180) % 2 == 0
                        Image(
                            painter = painterResource(id = if (isHeadsVisible) R.drawable.coin_heads else R.drawable.coin_tails),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Button(
                    onClick = {
                        scope.launch {
                            isFlipping = true
                            coinResult = null
                            winnerId = null
                            
                            launch { scale.animateTo(1.5f, tween(500, easing = FastOutSlowInEasing)) }
                            
                            // 12 full rotations + random end side (0 or 180)
                            val finalResultIsHeads = Random().nextBoolean()
                            val targetRotation = (180f * 24) + (if (finalResultIsHeads) 0f else 180f)
                            
                            rotation.animateTo(
                                targetValue = targetRotation,
                                animationSpec = tween(durationMillis = 1800, easing = FastOutSlowInEasing)
                            )
                            
                            scale.animateTo(1f, tween(300))
                            
                            val resultText = if (finalResultIsHeads) "HEADS" else "TAILS"
                            coinResult = resultText
                            isFlipping = false
                            
                            if (callerChoice != null) {
                                val otherTeamId = if (tossCallerId == match.teamA.id) match.teamB.id else match.teamA.id
                                winnerId = if (callerChoice == resultText) tossCallerId else otherTeamId
                            }
                        }
                    },
                    enabled = !isFlipping && callerChoice != null && coinResult == null,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isFlipping) "FLIPPING..." else "FLIP COIN")
                }
                
                if (coinResult != null) {
                    val callerName = if (tossCallerId == match.teamA.id) match.teamA.name else match.teamB.name
                    val winnerName = if (winnerId == match.teamA.id) match.teamA.name else match.teamB.name
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$callerName called $callerChoice · Coin: $coinResult · $winnerName wins!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF1B5E20),
                                textAlign = TextAlign.Center
                            )
                            TextButton(
                                onClick = { 
                                    coinResult = null
                                    callerChoice = null
                                    winnerId = null
                                    scope.launch { rotation.snapTo(0f) }
                                },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text("RE-FLIP", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.5f))

                // MANUAL OVERRIDE SECTION
                Text("Manual Winner Override:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = winnerId == match.teamA.id,
                        onClick = { if (!isFlipping) winnerId = match.teamA.id },
                        label = { Text(match.teamA.name.uppercase(), fontWeight = FontWeight.Black) },
                        enabled = !isFlipping
                    )
                    FilterChip(
                        selected = winnerId == match.teamB.id,
                        onClick = { if (!isFlipping) winnerId = match.teamB.id },
                        label = { Text(match.teamB.name.uppercase(), fontWeight = FontWeight.Black) },
                        enabled = !isFlipping
                    )
                }
                
                if (winnerId != null) {
                    Text("Decision", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { decision = "BAT" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (decision == "BAT") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, contentColor = if (decision == "BAT") Color.White else Color.Black),
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)
                        ) { Text("BAT FIRST") }
                        Button(
                            onClick = { decision = "BOWL" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (decision == "BOWL") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, contentColor = if (decision == "BOWL") Color.White else Color.Black),
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)
                        ) { Text("BOWL FIRST") }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    winnerId?.let { w -> 
                        decision?.let { d -> 
                            onResult(w, d)
                        } 
                    } 
                },
                enabled = winnerId != null && decision != null && !isFlipping,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("CONFIRM TOSS", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL") }
        }
    )
}

@Composable
fun MatchCelebrationDialog(uiState: MatchUiState, onNavigateToDashboard: () -> Unit, onDismiss: () -> Unit) {
    val match = uiState.match ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("🎊 CHAMPIONS! 🏆", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                val winner = if (match.winnerId == match.teamA.id) match.teamA.name else if (match.winnerId == match.teamB.id) match.teamB.name else "Match Drawn"
                Text(
                    text = if (match.winnerId != null) "$winner won the match!" else "The match ended in a draw.",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text("Great game played by both teams! 🏏🔥", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("FINAL SCORE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("${match.totalRuns}/${match.totalWickets} (${match.totalBalls/6}.${match.totalBalls%6} Ov)", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onNavigateToDashboard, modifier = Modifier.fillMaxWidth()) {
                Text("EXIT")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("VIEW SCORECARD")
            }
        }
    )
}

@Composable
fun WicketDialog(uiState: MatchUiState, viewModel: ScoringViewModel, onDismiss: () -> Unit) {
    val match = uiState.match ?: return
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
                            if (type == WicketType.RUN_OUT) {
                                viewModel.handleWicket(type, null)
                                onDismiss()
                            } else {
                                viewModel.handleWicket(type, match.strikerId)
                                onDismiss()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(type.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() })
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
                    var showCustomPrompt by remember { mutableStateOf(false) }
                    var customValue by remember { mutableStateOf("") }
                    
                    OutlinedButton(
                        onClick = { showCustomPrompt = true },
                        shape = CircleShape,
                        modifier = Modifier.size(44.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("...")
                    }
                    
                    if (showCustomPrompt) {
                        AlertDialog(
                            onDismissRequest = { showCustomPrompt = false },
                            title = { Text("Custom Extra Runs") },
                            text = {
                                OutlinedTextField(
                                    value = customValue,
                                    onValueChange = { if (it.all { c -> c.isDigit() }) customValue = it },
                                    label = { Text("Runs") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            },
                            confirmButton = {
                                Button(onClick = {
                                    val runs = customValue.toIntOrNull() ?: 0
                                    viewModel.handleExtra(type, runs)
                                    showCustomPrompt = false
                                    onDismiss()
                                }) { Text("OK") }
                            }
                        )
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
        title = { 
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Overthrow", fontWeight = FontWeight.Black)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Select bat runs (Strike will rotate if odd)", style = MaterialTheme.typography.bodyMedium)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
fun RetireHurtDialog(uiState: MatchUiState, viewModel: ScoringViewModel, onDismiss: () -> Unit) {
    val match = uiState.match ?: return
    val battingTeam = if (match.battingTeamId == match.teamA.id) match.teamA else match.teamB
    val striker = battingTeam.players.find { it.id == match.strikerId }
    val nonStriker = battingTeam.players.find { it.id == match.nonStrikerId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Retire Hurt", fontWeight = FontWeight.Black)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = null) }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Who is retiring hurt?")
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    
                    if (striker != null && nonStriker != null) {
                        OutlinedButton(
                            onClick = {
                                viewModel.handleDoubleRetire()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Text("RETIRE BOTH")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { }
    )
}
