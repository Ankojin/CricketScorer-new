package `in`.nrkmart.cricscore

import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.nrkmart.cricscore.ui.CardBranding
import java.util.UUID

private fun handleToggleNearbySync(context: Context, viewModel: ScoringViewModel, enable: Boolean, match: Match? = null) {
    if (enable) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        
        if (!isGpsEnabled && !isNetworkEnabled) {
            Toast.makeText(context, "Please turn on GPS/Location to use Nearby Sync 📍", Toast.LENGTH_LONG).show()
            return
        }
        
        viewModel.toggleSync(true)
        NearbyManager.startSync(context, "CricLeague: " + Build.MODEL)
        if (match != null) {
            val tournament = TournamentRepository.getTournament(match.tournamentId ?: "") ?: Tournament(id = UUID.randomUUID().toString(), name = "Match", matches = listOf(match))
            NearbyManager.broadcastTournament(context, tournament)
            NearbyManager.broadcastMatch(context, match)
            Toast.makeText(context, "Nearby Live Match Sync Active! 📡⚡", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Nearby Sync Active! 📡⚡", Toast.LENGTH_SHORT).show()
        }
    } else {
        viewModel.toggleSync(false)
        NearbyManager.stopAll(context)
        Toast.makeText(context, "Nearby Sync Stopped 🛑", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ScoringViewModel,
    tournamentViewModel: TournamentViewModel,
    onNavigateToDashboard: () -> Unit,
    onNavigateToLiveScoring: (Match) -> Unit,
    onNavigateToMatches: () -> Unit,
    onNavigateToTeams: () -> Unit,
    onNavigateToPlayers: () -> Unit
) {
    val tournaments by TournamentRepository.tournaments.collectAsState()
    val liveMatch = tournaments.flatMap { it.matches }.find { it.status == MatchStatus.LIVE }
    val isSyncEnabled by viewModel.isSyncEnabled.collectAsState()
    val connectedEndpoints by NearbyManager.connectedEndpoints.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showSyncSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Cric League",
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1).sp
                    )
                },
                actions = {
                    Surface(
                        onClick = { showSyncSheet = true },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSyncEnabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(0.5.dp, if (isSyncEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(8.dp),
                                shape = CircleShape,
                                color = if (isSyncEnabled) MaterialTheme.colorScheme.secondary else Color.Gray
                            ) {}
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isSyncEnabled) {
                                    if (connectedEndpoints.isNotEmpty()) "SYNC (${connectedEndpoints.size})" else "SYNC ON"
                                } else "SYNC OFF",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSyncEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = { showAboutDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "About Cric League",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Live Match Section
                if (liveMatch != null) {
                    item {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "LIVE MATCH",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(8.dp))
                            val isSyncEnabled by viewModel.isSyncEnabled.collectAsState()
                            if (isSyncEnabled) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = CircleShape
                                ) {
                                    Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.secondary)
                                        Text(" SYNC ACTIVE", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                            }
                        }
                        
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                                .clickable { onNavigateToLiveScoring(liveMatch) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            "${liveMatch.teamA.name} vs ${liveMatch.teamB.name}",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Black
                                        )
                                        Text(
                                            "Innings ${liveMatch.currentInnings} • Live",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            "${liveMatch.totalRuns}/${liveMatch.totalWickets}",
                                            style = MaterialTheme.typography.headlineLarge,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            "(${liveMatch.totalBalls / 6}.${liveMatch.totalBalls % 6} Ov)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(20.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { onNavigateToLiveScoring(liveMatch) },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("CONTINUE SCORING", fontWeight = FontWeight.Black)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            handleToggleNearbySync(context, viewModel, !isSyncEnabled, liveMatch)
                                        },
                                        modifier = Modifier.height(48.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = if (isSyncEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                        ),
                                        border = BorderStroke(1.dp, if (isSyncEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                modifier = Modifier.size(8.dp),
                                                shape = CircleShape,
                                                color = if (isSyncEnabled) MaterialTheme.colorScheme.secondary else Color.Gray
                                            ) {}
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = if (isSyncEnabled) "BROADCASTING" else "BROADCAST LIVE",
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Highlighted Motto / Brand Banner
                item {
                    Card(
                        modifier = Modifier
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                            .fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "TRACK EVERY BALL.\nOWN EVERY OVER.\nSHARE EVERY MOMENT.",
                                style = MaterialTheme.typography.titleMedium.copy(lineHeight = 22.sp),
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                letterSpacing = 0.5.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "The best cricket scoring app for your matches. Score every ball, track your gully cricket score, relive every moment, and share the glory.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                item {
                    Text(
                        "QUICK START",
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .clickable { onNavigateToDashboard() },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text("Create Series", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Setup teams and manage multiple matches with full leaderboards.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onNavigateToDashboard,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("GET STARTED", fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "EXPLORE",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        if (maxWidth < 360.dp) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    QuickActionChip("SERIES", onNavigateToDashboard, Modifier.weight(1f))
                                    QuickActionChip("PLAYLIST", onNavigateToPlayers, Modifier.weight(1f))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    QuickActionChip("TEAMS", onNavigateToTeams, Modifier.weight(1f))
                                    QuickActionChip("MATCHES", onNavigateToMatches, Modifier.weight(1f))
                                }
                            }
                        } else {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item { QuickActionChip("SERIES", onNavigateToDashboard) }
                                item { QuickActionChip("PLAYLIST", onNavigateToPlayers) }
                                item { QuickActionChip("TEAMS", onNavigateToTeams) }
                                item { QuickActionChip("MATCHES", onNavigateToMatches) }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    CardBranding()
                }
            }

            if (showSettings) {
                val gullyRules by GullyRulesRepository.gullyRules.collectAsState()

                AlertDialog(
                    onDismissRequest = { showSettings = false },
                    title = { Text("Settings", fontWeight = FontWeight.Black) },
                    text = {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Dark Mode", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    Text("Switch to midnight theme", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                                val isDarkMode by viewModel.isDarkMode.collectAsState()
                                Switch(
                                    checked = isDarkMode ?: androidx.compose.foundation.isSystemInDarkTheme(),
                                    onCheckedChange = { viewModel.toggleTheme(it) }
                                )
                            }
                            
                            HorizontalDivider()

                            val isSyncEnabled by viewModel.isSyncEnabled.collectAsState()
                            val connectedEndpoints by NearbyManager.connectedEndpoints.collectAsState()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Local Sync (Beta)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    Text("Share scores with devices nearby", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                                
                                Switch(
                                    checked = isSyncEnabled,
                                    onCheckedChange = { enabled ->
                                        if (enabled) {
                                            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                                            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                                            val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                                            
                                            if (!isGpsEnabled && !isNetworkEnabled) {
                                                Toast.makeText(context, "Please turn on GPS/Location to use Sync.", Toast.LENGTH_LONG).show()
                                                return@Switch
                                            }
                                            
                                            viewModel.toggleSync(true)
                                            NearbyManager.startSync(context, "CricLeague: " + Build.MODEL)
                                        } else {
                                            viewModel.toggleSync(false)
                                            NearbyManager.stopAll(context)
                                        }
                                    }
                                )
                            }

                            if (isSyncEnabled && connectedEndpoints.isEmpty()) {
                                Text(
                                    "Status: Searching for devices...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            HorizontalDivider()

                            GullyRulesSettingsSection(
                                gullyRules = gullyRules,
                                onRuleChange = { updated -> GullyRulesRepository.updateRules(updated) }
                            )

                            HorizontalDivider()

                            Button(
                                onClick = {
                                    tournamentViewModel.setupTestData()
                                    Toast.makeText(context, "E2E Test match scheduled! Check 'MATCHES'", Toast.LENGTH_LONG).show()
                                    showSettings = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                            ) {
                                Text("DEBUG: SETUP 5-OVER E2E TEST", fontWeight = FontWeight.Black)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSettings = false }) {
                            Text("DONE", fontWeight = FontWeight.Black)
                        }
                    }
                )
            }

            if (showAboutDialog) {
                AlertDialog(
                    onDismissRequest = { showAboutDialog = false },
                    confirmButton = {
                        Button(
                            onClick = { showAboutDialog = false },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("DISMISS", fontWeight = FontWeight.Bold)
                        }
                    },
                    title = null,
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Spacer(Modifier.height(4.dp))
                            Surface(
                                modifier = Modifier.size(72.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shadowElevation = 4.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Image(
                                        painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                                        contentDescription = "Cric League Icon",
                                        modifier = Modifier.size(64.dp)
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Cric League",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text(
                                        text = "v${BuildConfig.VERSION_NAME}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Track every ball. Own every over. Share every moment.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }

                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "TRACK EVERY BALL.\nOWN EVERY OVER.\nSHARE EVERY MOMENT.",
                                        style = MaterialTheme.typography.labelLarge.copy(lineHeight = 18.sp),
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        letterSpacing = 0.5.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            Text(
                                text = "The best cricket scoring app for your matches. Score every ball, track your gully cricket score, relive every moment, and share the glory.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp,
                                textAlign = TextAlign.Center
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("⚡ Ball-by-Ball") }
                                )
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("📲 Nearby Sync") }
                                )
                            }
                        }
                    }
                )
            }

            if (showSyncSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showSyncSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Nearby Live Sync 📡", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                                Text("Share scores & matches with devices nearby", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            Surface(
                                color = if (isSyncEnabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape
                            ) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        modifier = Modifier.size(8.dp),
                                        shape = CircleShape,
                                        color = if (isSyncEnabled) MaterialTheme.colorScheme.secondary else Color.Gray
                                    ) {}
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (isSyncEnabled) "ACTIVE" else "OFF",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSyncEnabled) MaterialTheme.colorScheme.secondary else Color.Gray
                                    )
                                }
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Enable Local Sync", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Text("Discover and connect to nearby scorers", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            Switch(
                                checked = isSyncEnabled,
                                onCheckedChange = { enabled ->
                                    handleToggleNearbySync(context, viewModel, enabled, liveMatch)
                                }
                            )
                        }

                        if (isSyncEnabled) {
                            HorizontalDivider()
                            Text("CONNECTED DEVICES", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            
                            if (connectedEndpoints.isEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Text("Searching for nearby devices...", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    connectedEndpoints.forEach { endpointId ->
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = Color(0xFF4CAF50)) {}
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(NearbyManager.getEndpointName(endpointId), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                }
                                                Text("CONNECTED", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            if (liveMatch != null) {
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        NearbyManager.broadcastMatch(context, liveMatch)
                                        Toast.makeText(context, "Broadcasting Live Match to connected devices! 📡⚡", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("BROADCAST LIVE MATCH NOW 📡", fontWeight = FontWeight.Black)
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.LightGray)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun GullyRulesSettingsSection(
    gullyRules: GullyRules,
    onRuleChange: (GullyRules) -> Unit
) {
    var isSquadExpanded by remember { mutableStateOf(false) }
    var isBattingExpanded by remember { mutableStateOf(false) }
    var isScoringExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Gully Rules", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text("Applies to newly created matches", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "${gullyRules.totalActiveCount}/7 active",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Section 1: Squad & Teams
            ExpandableRuleSection(
                title = "SQUAD & TEAMS",
                activeCountText = "${gullyRules.squadCount}/4",
                isExpanded = isSquadExpanded,
                onToggleExpand = { isSquadExpanded = !isSquadExpanded }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GullyRuleRow(
                        title = "Common player",
                        subtitle = "Plays for both sides (gully) · optional",
                        checked = gullyRules.commonPlayer,
                        onCheckedChange = { onRuleChange(gullyRules.copy(commonPlayer = it)) }
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    GullyRuleRow(
                        title = "Start with unequal teams",
                        subtitle = "Teams can have different player counts. Each side is all out on its own squad size.",
                        checked = gullyRules.unequalTeams,
                        onCheckedChange = { onRuleChange(gullyRules.copy(unequalTeams = it)) }
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    GullyRuleRow(
                        title = "Players can join mid-match",
                        subtitle = "Add late arrivals to either team while the match is on.",
                        checked = gullyRules.playersJoinMidMatch,
                        onCheckedChange = { onRuleChange(gullyRules.copy(playersJoinMidMatch = it)) }
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    GullyRuleRow(
                        title = "Players can switch mid-match",
                        subtitle = "Move/swap players who haven't batted or bowled yet.",
                        checked = gullyRules.playersSwitchMidMatch,
                        onCheckedChange = { onRuleChange(gullyRules.copy(playersSwitchMidMatch = it)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Section 2: Batting Format
            ExpandableRuleSection(
                title = "BATTING FORMAT",
                activeCountText = "${gullyRules.battingCount}/2",
                isExpanded = isBattingExpanded,
                onToggleExpand = { isBattingExpanded = !isBattingExpanded }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GullyRuleRow(
                        title = "Last man standing",
                        subtitle = "Last batter bats alone (gully). Off = all out one wicket earlier.",
                        checked = gullyRules.lastManStanding,
                        onCheckedChange = { onRuleChange(gullyRules.copy(lastManStanding = it)) }
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    GullyRuleRow(
                        title = "Single-side batting",
                        subtitle = "One batter at a time — no non-striker, strike never rotates.",
                        checked = gullyRules.singleSideBatting,
                        onCheckedChange = { onRuleChange(gullyRules.copy(singleSideBatting = it)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Section 3: Scoring & Match
            ExpandableRuleSection(
                title = "SCORING & MATCH",
                activeCountText = "${gullyRules.scoringCount}/1",
                isExpanded = isScoringExpanded,
                onToggleExpand = { isScoringExpanded = !isScoringExpanded }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GullyRuleRow(
                        title = "No runs for wides & no-balls",
                        subtitle = "Still re-bowled, but no extra penalty run is added.",
                        checked = gullyRules.noExtraRunsForWidesNoBalls,
                        onCheckedChange = { onRuleChange(gullyRules.copy(noExtraRunsForWidesNoBalls = it)) }
                    )
                }
            }
        }
    }
}

@Composable
fun ExpandableRuleSection(
    title: String,
    activeCountText: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    activeCountText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
            }
            if (isExpanded) {
                Box(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
fun GullyRuleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                lineHeight = 14.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
