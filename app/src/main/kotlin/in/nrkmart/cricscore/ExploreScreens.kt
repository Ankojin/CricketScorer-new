package `in`.nrkmart.cricscore

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `in`.nrkmart.cricscore.ui.CardBranding
import `in`.nrkmart.cricscore.ui.TeamColorPicker
import `in`.nrkmart.cricscore.ui.colorOrDefault
import `in`.nrkmart.cricscore.ui.findTeamNameForPlayer
import `in`.nrkmart.cricscore.ui.parseTeamColor
import `in`.nrkmart.cricscore.ui.TEAM_PALETTE

private fun PlayerRole.displayName(): String = when (this) {
    PlayerRole.BATTER -> "Batter"
    PlayerRole.BOWLER -> "Bowler"
    PlayerRole.ALL_ROUNDER -> "All-rounder"
    PlayerRole.WICKET_KEEPER -> "Wicket-keeper"
}

private fun BowlingStyle?.displayName(): String = when (this ?: BowlingStyle.NONE) {
    BowlingStyle.RIGHT_ARM -> "Right arm"
    BowlingStyle.LEFT_ARM -> "Left arm"
    BowlingStyle.NONE -> "None"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllTeamsScreen(
    onBack: () -> Unit,
    onTeamClick: (String, String?) -> Unit // teamId, tournamentId
) {
    val tournaments by TournamentRepository.tournaments.collectAsState()
    val allTeams = tournaments.flatMap { it.teams }.distinctBy { it.id }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Teams", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFFF5F7FA)), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (allTeams.isEmpty()) {
                item { Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { Text("No teams found. Create a series first.", color = Color.Gray) } }
            } else {
                items(allTeams) { team ->
                    val tournamentId = tournaments.find { t -> t.teams.any { it.id == team.id } }?.id
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onTeamClick(team.id, tournamentId) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = parseTeamColor(team.colorHex, MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    val teamColor = parseTeamColor(team.colorHex, Color.Transparent)
                                    val textColor = if (team.colorHex != null) {
                                        if (teamColor.luminance() > 0.5f) Color.Black else Color.White
                                    } else MaterialTheme.colorScheme.onPrimaryContainer
                                    Text(team.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = textColor)
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(team.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("${team.players.size} Players", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                    }
                }
                item { CardBranding() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllPlayersScreen(onBack: () -> Unit) {
    val globalPlayers by GlobalPlayerRepository.players.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var playerName by remember { mutableStateOf("") }
    var battingStyle by remember { mutableStateOf(BattingStyle.RHB) }
    var bowlingStyle by remember { mutableStateOf(BowlingStyle.NONE) }
    var playerRole by remember { mutableStateOf(PlayerRole.BATTER) }

    val filteredPlayers = remember(globalPlayers, searchQuery) {
        if (searchQuery.isBlank()) globalPlayers
        else {
            val q = searchQuery.trim().lowercase()
            globalPlayers.filter { p ->
                p.name.lowercase().contains(q) ||
                p.role.displayName().lowercase().contains(q) ||
                (p.battingStyle?.name ?: "").lowercase().contains(q) ||
                p.bowlingStyle.displayName().lowercase().contains(q)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Global Playlist", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Add Global Player", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }, containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFFF5F7FA)), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Saved players that can be reused across any series or team. 🌎🏏", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(bottom = 4.dp))
            }
            if (globalPlayers.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search player by name, style, or role...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    )
                }
            }
            if (globalPlayers.isEmpty()) {
                item { Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { Text("Your global playlist is empty. Add players to reuse them!", color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp)) } }
            } else if (filteredPlayers.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("No players found matching \"$searchQuery\"", color = Color.Gray, textAlign = TextAlign.Center) } }
            } else {
                items(filteredPlayers) { player ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(modifier = Modifier.size(32.dp), shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                                Box(contentAlignment = Alignment.Center) { Text(player.name.take(1).uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black) }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(player.name, fontWeight = FontWeight.Bold)
                                Text("${player.role.displayName()} · Bat: ${player.battingStyle ?: "RHB"} · Bowl: ${player.bowlingStyle.displayName()}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                            Row {
                                var showEditPlayerDialog by remember { mutableStateOf<Player?>(null) }
                                IconButton(onClick = { showEditPlayerDialog = player }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray, modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { GlobalPlayerRepository.removePlayer(player.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                                }

                                if (showEditPlayerDialog != null) {
                                    val p = showEditPlayerDialog!!
                                    var editedName by remember(p.id) { mutableStateOf(p.name) }
                                    var editedStyle by remember(p.id) { mutableStateOf(p.battingStyle ?: BattingStyle.RHB) }
                                    var editedBowlingStyle by remember(p.id) { mutableStateOf(p.bowlingStyle ?: BowlingStyle.NONE) }
                                    var editedRole by remember(p.id) { mutableStateOf(p.role) }

                                    val isDuplicateName = remember(editedName) {
                                        val trimmed = editedName.trim()
                                        globalPlayers.any { it.id != p.id && it.name.equals(trimmed, ignoreCase = true) }
                                    }

                                    AlertDialog(
                                        onDismissRequest = { showEditPlayerDialog = null },
                                        title = { Text("Edit Player") },
                                        text = {
                                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                                OutlinedTextField(
                                                    value = editedName,
                                                    onValueChange = { editedName = it },
                                                    label = { Text("Name") },
                                                    isError = isDuplicateName,
                                                    supportingText = {
                                                        if (isDuplicateName) {
                                                            Text("A player with this name already exists in playlist.", color = MaterialTheme.colorScheme.error)
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                Text("Batting Style", fontWeight = FontWeight.Bold)
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    BattingStyle.entries.forEach { style ->
                                                        FilterChip(
                                                            selected = editedStyle == style,
                                                            onClick = { editedStyle = style },
                                                            label = { Text(style.name) },
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                }
                                                Text("Role", fontWeight = FontWeight.Bold)
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        FilterChip(
                                                            selected = editedRole == PlayerRole.BATTER,
                                                            onClick = {
                                                                editedRole = PlayerRole.BATTER
                                                                editedBowlingStyle = BowlingStyle.NONE
                                                            },
                                                            label = { Text(PlayerRole.BATTER.displayName()) },
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        FilterChip(
                                                            selected = editedRole == PlayerRole.BOWLER,
                                                            onClick = {
                                                                editedRole = PlayerRole.BOWLER
                                                                if (editedBowlingStyle == BowlingStyle.NONE) editedBowlingStyle = BowlingStyle.RIGHT_ARM
                                                            },
                                                            label = { Text(PlayerRole.BOWLER.displayName()) },
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        FilterChip(
                                                            selected = editedRole == PlayerRole.ALL_ROUNDER,
                                                            onClick = {
                                                                editedRole = PlayerRole.ALL_ROUNDER
                                                                if (editedBowlingStyle == BowlingStyle.NONE) editedBowlingStyle = BowlingStyle.RIGHT_ARM
                                                            },
                                                            label = { Text(PlayerRole.ALL_ROUNDER.displayName()) },
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        FilterChip(
                                                            selected = editedRole == PlayerRole.WICKET_KEEPER,
                                                            onClick = {
                                                                editedRole = PlayerRole.WICKET_KEEPER
                                                                editedBowlingStyle = BowlingStyle.NONE
                                                            },
                                                            label = { Text(PlayerRole.WICKET_KEEPER.displayName()) },
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                }
                                                Text("Bowling Style", fontWeight = FontWeight.Bold)
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    BowlingStyle.entries.forEach { style ->
                                                        FilterChip(
                                                            selected = editedBowlingStyle == style,
                                                            onClick = { editedBowlingStyle = style },
                                                            label = { Text(style.displayName()) },
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        confirmButton = {
                                            Button(
                                                onClick = {
                                                    if (editedName.isNotBlank() && !isDuplicateName) {
                                                        GlobalPlayerRepository.updatePlayer(p.id, editedName, editedStyle, editedBowlingStyle, editedRole)
                                                        showEditPlayerDialog = null
                                                    }
                                                },
                                                enabled = editedName.isNotBlank() && !isDuplicateName
                                            ) { Text("UPDATE") }
                                        },
                                        dismissButton = { TextButton(onClick = { showEditPlayerDialog = null }) { Text("CANCEL") } }
                                    )
                                }
                            }
                        }
                    }
                }
                item { CardBranding() }
            }
        }

        if (showAddDialog) {
            val isDuplicateName = remember(playerName) {
                val trimmed = playerName.trim()
                globalPlayers.any { it.name.equals(trimmed, ignoreCase = true) }
            }

            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Add Global Player") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = playerName,
                            onValueChange = { playerName = it },
                            label = { Text("Player Name") },
                            supportingText = {
                                if (isDuplicateName) {
                                    Text("Player already in playlist. Saving will update their details.", color = MaterialTheme.colorScheme.primary)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Batting Style", fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BattingStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = battingStyle == style,
                                    onClick = { battingStyle = style },
                                    label = { Text(style.name) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Text("Role", fontWeight = FontWeight.Bold)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = playerRole == PlayerRole.BATTER,
                                    onClick = {
                                        playerRole = PlayerRole.BATTER
                                        bowlingStyle = BowlingStyle.NONE
                                    },
                                    label = { Text(PlayerRole.BATTER.displayName()) },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = playerRole == PlayerRole.BOWLER,
                                    onClick = {
                                        playerRole = PlayerRole.BOWLER
                                        if (bowlingStyle == BowlingStyle.NONE) bowlingStyle = BowlingStyle.RIGHT_ARM
                                    },
                                    label = { Text(PlayerRole.BOWLER.displayName()) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = playerRole == PlayerRole.ALL_ROUNDER,
                                    onClick = {
                                        playerRole = PlayerRole.ALL_ROUNDER
                                        if (bowlingStyle == BowlingStyle.NONE) bowlingStyle = BowlingStyle.RIGHT_ARM
                                    },
                                    label = { Text(PlayerRole.ALL_ROUNDER.displayName()) },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = playerRole == PlayerRole.WICKET_KEEPER,
                                    onClick = {
                                        playerRole = PlayerRole.WICKET_KEEPER
                                        bowlingStyle = BowlingStyle.NONE
                                    },
                                    label = { Text(PlayerRole.WICKET_KEEPER.displayName()) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Text("Bowling Style", fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BowlingStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = bowlingStyle == style,
                                    onClick = { bowlingStyle = style },
                                    label = { Text(style.displayName()) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (playerName.isNotBlank()) {
                            GlobalPlayerRepository.addPlayer(playerName, battingStyle, bowlingStyle, playerRole)
                            playerName = ""
                            battingStyle = BattingStyle.RHB
                            bowlingStyle = BowlingStyle.NONE
                            playerRole = PlayerRole.BATTER
                            showAddDialog = false
                        }
                    }, enabled = playerName.isNotBlank()) { Text(if (isDuplicateName) "UPDATE DETAILS" else "SAVE TO PLAYLIST") }
                },
                dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("CANCEL") } }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllMatchesScreen(
    onBack: () -> Unit,
    onMatchClick: (Match) -> Unit
) {
    val tournaments by TournamentRepository.tournaments.collectAsState()
    val allMatches = tournaments.flatMap { it.matches }.sortedByDescending { it.dateMillis }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Matches", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFFF5F7FA)), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (allMatches.isEmpty()) {
                item { Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { Text("No matches scheduled.", color = Color.Gray) } }
            } else {
                items(allMatches) { match ->
                    val tournament = tournaments.find { it.id == match.tournamentId }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onMatchClick(match) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("${tournament?.name ?: "Series"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${match.teamA.name} vs ${match.teamB.name}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Score: ${match.totalRuns}/${match.totalWickets} (${match.totalBalls/6}.${match.totalBalls%6} Ov)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item { CardBranding() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamDetailScreen(
    teamId: String,
    tournamentId: String?,
    viewModel: TournamentViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val tournaments by viewModel.tournaments.collectAsState()
    
    // Resolve tournament and team
    val tournament = if (tournamentId != null) {
        tournaments.find { it.id == tournamentId }
    } else {
        tournaments.find { t -> t.teams.any { it.id == teamId } }
    }
    
    val team = tournament?.teams?.find { it.id == teamId }
    val globalPlayers by GlobalPlayerRepository.players.collectAsState()
    
    var showEditTeamDialog by remember { mutableStateOf(false) }
    var editedTeamName by remember(team?.id) { mutableStateOf(team?.name ?: "") }
    var editedTeamColor by remember(team?.id) { mutableStateOf(team?.colorHex) }

    var showAddDialog by remember { mutableStateOf(false) }
    var newPlayerName by remember { mutableStateOf("") }
    var selectedStyle by remember { mutableStateOf(BattingStyle.RHB) }
    var showGlobalPlaylist by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    val teamColor = team?.colorOrDefault(MaterialTheme.colorScheme.primary) ?: MaterialTheme.colorScheme.primary
                    val contentColor = if (team?.colorHex != null) {
                        if (teamColor.luminance() > 0.5f) Color.Black else Color.White
                    } else Color.White
                    
                    Text(team?.name ?: "Team Details", fontWeight = FontWeight.Black, color = contentColor) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (team != null) {
                        IconButton(onClick = { showEditTeamDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Team")
                        }
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Add Player")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = team?.colorOrDefault(MaterialTheme.colorScheme.primary) ?: MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White, 
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        if (team == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Team not found.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFFF5F7FA)),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text("SQUAD LIST", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                }
                
                if (team.players.isEmpty()) {
                    item { Text("No players in this team yet.", color = Color.Gray, style = MaterialTheme.typography.bodyMedium) }
                } else {
                    items(team.players) { player ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(modifier = Modifier.size(32.dp), shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                                    Box(contentAlignment = Alignment.Center) { Text(player.name.take(1).uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black) }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(player.name, fontWeight = FontWeight.Bold)
                                    Text("Batting: ${player.battingStyle ?: "RHB"}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                                IconButton(onClick = { 
                                    if (tournament != null) viewModel.deletePlayer(tournament.id, team.id, player.id) 
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
                item { CardBranding() }
            }
        }

        if (showEditTeamDialog && tournament != null && team != null) {
            AlertDialog(
                onDismissRequest = { showEditTeamDialog = false },
                title = { Text("Edit Team", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = editedTeamName,
                            onValueChange = { editedTeamName = it },
                            label = { Text("Team Name") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        
                        val otherTeamColors = tournament.teams.filter { it.id != team.id }.mapNotNull { it.colorHex }
                        TeamColorPicker(
                            selectedColorHex = editedTeamColor,
                            otherTeamColorsHex = otherTeamColors,
                            onColorSelected = { editedTeamColor = it }
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (editedTeamName.isNotBlank()) {
                                val success = viewModel.updateTeamDetails(tournament.id, team.id, editedTeamName, editedTeamColor)
                                if (!success) {
                                    Toast.makeText(context, "Color already used by the other team", Toast.LENGTH_SHORT).show()
                                } else {
                                    showEditTeamDialog = false
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditTeamDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showAddDialog && tournament != null && team != null) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text(if (showGlobalPlaylist) "Pick from Playlist" else "Add Player") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (!showGlobalPlaylist) {
                            OutlinedTextField(
                                value = newPlayerName, 
                                onValueChange = { newPlayerName = it }, 
                                label = { Text("Name") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("Batting Style", fontWeight = FontWeight.Bold)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BattingStyle.entries.forEach { style ->
                                    FilterChip(
                                        selected = selectedStyle == style,
                                        onClick = { selectedStyle = style },
                                        label = { Text(style.name) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            
                            if (globalPlayers.isNotEmpty()) {
                                TextButton(onClick = { showGlobalPlaylist = true }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.PersonSearch, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("PICK FROM GLOBAL PLAYLIST")
                                }
                            }
                        } else {
                            Text("Select players to add:", fontWeight = FontWeight.Bold)
                            val currentTeamPlayerNames = team.players.map { it.name.lowercase() }
                            val filteredGlobal = globalPlayers.filter { gp -> gp.name.lowercase() !in currentTeamPlayerNames }
                            val selectedPlayers = remember { mutableStateListOf<Player>() }

                            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                                if (filteredGlobal.isEmpty()) {
                                    item { Text("No new players to add.", color = Color.Gray, modifier = Modifier.padding(16.dp)) }
                                } else {
                                    items(filteredGlobal) { gp ->
                                        val isSelected = selectedPlayers.contains(gp)
                                        val existingTeamName = findTeamNameForPlayer(tournament, gp.name)
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                if (isSelected) selectedPlayers.remove(gp) else selectedPlayers.add(gp)
                                            }.padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(checked = isSelected, onCheckedChange = {
                                                if (it) selectedPlayers.add(gp) else selectedPlayers.remove(gp)
                                            })
                                            Column {
                                                Text(gp.name + " (${gp.battingStyle})")
                                                if (existingTeamName != null) {
                                                    Text(
                                                        text = if (existingTeamName.equals(team.name, true)) "Already in this team" else "Also in $existingTeamName",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (existingTeamName.equals(team.name, true)) MaterialTheme.colorScheme.error else Color.Gray,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                        HorizontalDivider(thickness = 0.5.dp)
                                    }
                                }
                            }
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (selectedPlayers.isNotEmpty()) {
                                            viewModel.addGlobalPlayers(tournament.id, team.id, selectedPlayers.toList())
                                            showAddDialog = false
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = selectedPlayers.isNotEmpty()
                                ) {
                                    Text("ADD (${selectedPlayers.size})")
                                }
                                TextButton(onClick = { showGlobalPlaylist = false }) { Text("BACK") }
                            }
                        }
                    }
                },
                confirmButton = {
                    if (!showGlobalPlaylist) {
                        Button(onClick = {
                            if (newPlayerName.isNotBlank()) {
                                viewModel.addPlayer(context, tournament.id, team.id, newPlayerName, selectedStyle)
                                newPlayerName = ""
                                showAddDialog = false
                            }
                        }) { Text("ADD") }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) { Text("CANCEL") }
                }
            )
        }
    }
}
