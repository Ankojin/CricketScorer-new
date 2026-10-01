package `in`.nrkmart.cricscore

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.nrkmart.cricscore.ui.*
import kotlinx.coroutines.launch

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
    var showRevokeShareConfirm by remember { mutableStateOf(false) }
    var showShareTtlDialog by remember { mutableStateOf(false) }
    var showSyncRetryDialog by remember { mutableStateOf(false) }
    var selectedShareTtl by remember { mutableIntStateOf(360) }
    var retryShareTtl by remember { mutableIntStateOf(360) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
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
        if (isSyncEnabled && match != null && connectedDevicesCount > 0 && !viewModel.isReceivingRemoteUpdate) {
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
                                Text("ACTIVE MATCH", fontWeight = FontWeight.Black)
                            }
                            if (match != null) {
                                MatchTicker(uiState, selectedTabIndex, viewedInnings)
                            }
                        }
                    },
                    actions = {
                        val canUseLiveShare = match?.status == MatchStatus.LIVE && !uiState.isSpectatorMode && CloudSyncManager.isSignedIn()
                        val shareIsActive = uiState.liveShareState.isActive

                        TopBarActionIcon(
                            icon = Icons.Default.Share,
                            contentDescription = if (shareIsActive) "Share live link (active)" else "Share live link",
                            enabled = canUseLiveShare,
                            tint = if (shareIsActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            onClick = {
                                if (!canUseLiveShare) return@TopBarActionIcon
                                val existingLink = uiState.liveShareState.shareUrl
                                if (shareIsActive && !existingLink.isNullOrBlank()) {
                                    shareLiveScoreLink(context, match, existingLink)
                                } else {
                                    showShareTtlDialog = true
                                }
                            }
                        )

                        TopBarActionIcon(
                            icon = Icons.Default.LinkOff,
                            contentDescription = "Revoke live link",
                            enabled = canUseLiveShare && shareIsActive,
                            tint = if (shareIsActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            onClick = { showRevokeShareConfirm = true }
                        )

                        IconButton(
                            onClick = { if (!uiState.isSpectatorMode) showManageSquads = true },
                            enabled = !uiState.isSpectatorMode
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Manage Squads")
                        }
                        IconButton(
                            onClick = { if (!uiState.isSpectatorMode) showOversDialog = true },
                            enabled = !uiState.isSpectatorMode
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }) {
                        Text("Summary", modifier = Modifier.padding(vertical = 10.dp), fontWeight = FontWeight.Bold)
                    }
                    Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }) {
                        Text("Scorecard", modifier = Modifier.padding(vertical = 10.dp), fontWeight = FontWeight.Bold)
                    }
                    Tab(selected = selectedTabIndex == 2, onClick = { selectedTabIndex = 2 }) {
                        Text("Overs", modifier = Modifier.padding(vertical = 10.dp), fontWeight = FontWeight.Bold)
                    }
                    Tab(selected = selectedTabIndex == 3, onClick = { selectedTabIndex = 3 }) {
                        Text("Stats", modifier = Modifier.padding(vertical = 10.dp), fontWeight = FontWeight.Bold)
                    }
                }

                if (!uiState.isSpectatorMode && CloudSyncManager.isSignedIn()) {
                    val shareLabel = if (uiState.liveShareState.isActive) {
                        val ttl = uiState.liveShareState.expiresInSeconds?.let { secs ->
                            val mins = (secs + 59) / 60
                            " • TTL ${mins}m"
                        }.orEmpty()
                        "LIVE SHARE: Active$ttl"
                    } else {
                        "LIVE SHARE: Revoked / Not active"
                    }
                    Surface(
                        color = if (uiState.liveShareState.isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = shareLabel,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (uiState.liveShareState.isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                    onDismissOverSummary = { viewModel.dismissOverSummary() },
                    onNavigateToDashboard = onNavigateToDashboard
                )

                if (showRevokeShareConfirm) {
                    AlertDialog(
                        onDismissRequest = { showRevokeShareConfirm = false },
                        title = { Text("Revoke live share") },
                        text = { Text("Revoke all spectator links for this match?") },
                        confirmButton = {
                            TextButton(onClick = {
                                showRevokeShareConfirm = false
                                coroutineScope.launch {
                                    runCatching {
                                        viewModel.revokeLiveShare()
                                    }.onSuccess {
                                        Toast.makeText(context, "Live share revoked", Toast.LENGTH_SHORT).show()
                                    }.onFailure { err ->
                                        Toast.makeText(context, err.message ?: "Unable to revoke live share", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }) {
                                Text("Revoke")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRevokeShareConfirm = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                if (showShareTtlDialog) {
                    val ttlOptions = listOf(15, 60, 360)
                    AlertDialog(
                        onDismissRequest = { showShareTtlDialog = false },
                        title = { Text("Live share validity") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Choose spectator link validity")
                                ttlOptions.forEach { ttl ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        RadioButton(
                                            selected = selectedShareTtl == ttl,
                                            onClick = { selectedShareTtl = ttl }
                                        )
                                        Text("$ttl minutes")
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showShareTtlDialog = false
                                coroutineScope.launch {
                                    runCatching {
                                        viewModel.createLiveShareLink(selectedShareTtl)
                                    }.onSuccess { link ->
                                        shareLiveScoreLink(context, match, link)
                                        Toast.makeText(context, "Live share link created", Toast.LENGTH_SHORT).show()
                                    }.onFailure { err ->
                                        if (isLiveShareSyncRequiredError(err)) {
                                            retryShareTtl = selectedShareTtl
                                            showSyncRetryDialog = true
                                        } else {
                                            Toast.makeText(context, err.message ?: "Unable to create live share link", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }) {
                                Text("Create")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showShareTtlDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                if (showSyncRetryDialog) {
                    AlertDialog(
                        onDismissRequest = { showSyncRetryDialog = false },
                        title = { Text("Cloud sync required") },
                        text = {
                            Text("This match is not available in cloud yet. Sync match data now and retry creating live share link?")
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showSyncRetryDialog = false
                                coroutineScope.launch {
                                    runCatching {
                                        viewModel.syncMatchAndCreateLiveShareLink(retryShareTtl)
                                    }.onSuccess { link ->
                                        shareLiveScoreLink(context, match, link)
                                        Toast.makeText(context, "Match synced and live share link created", Toast.LENGTH_SHORT).show()
                                    }.onFailure { err ->
                                        Toast.makeText(context, err.message ?: "Sync and retry failed", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }) {
                                Text("Sync and Retry")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSyncRetryDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        } ?: NoActiveMatchState(onNavigateToMatches)
    }
}

@Composable
private fun TopBarActionIcon(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    tint: Color,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = contentDescription, tint = if (enabled) tint else tint.copy(alpha = 0.35f))
    }
}

private fun shareLiveScoreLink(context: Context, match: Match?, link: String) {
    val shareText = buildString {
        append("Live Cricket Score (Read-Only)\n")
        append("${match?.teamA?.name ?: "Team A"} vs ${match?.teamB?.name ?: "Team B"}\n")
        append("Open live score: $link")
    }

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(sendIntent, "Share live score"))
}

private fun isLiveShareSyncRequiredError(err: Throwable): Boolean {
    val msg = err.message.orEmpty().lowercase()
    return msg.contains("sync and retry") || msg.contains("http 403") || msg.contains("http 404")
}
