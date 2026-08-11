package com.example.cricketscorer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import android.content.Intent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: TournamentViewModel,
    onNavigateToTournament: (String) -> Unit
) {
    val tournaments by viewModel.tournaments.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var tournamentName by remember { mutableStateOf("") }
    var oversCount by remember { mutableStateOf("20") }
    var maxOversPerBowler by remember { mutableStateOf("") }
    var quotaBowlersCount by remember { mutableStateOf("") }
    var quotaMaxOvers by remember { mutableStateOf("") }
    var importJson by remember { mutableStateOf("") }

    val context = LocalContext.current
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { inputStream ->
                    val json = inputStream.bufferedReader().use { it.readText() }
                    if (json.isNotBlank()) {
                        val success = viewModel.importTournament(json)
                        if (success) {
                            Toast.makeText(context, "Tournament imported successfully", Toast.LENGTH_SHORT).show()
                            showImportDialog = false
                        } else {
                            Toast.makeText(context, "Import failed: Invalid data", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Series & Tournaments", fontWeight = FontWeight.Black) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                ),
                actions = {
                    TextButton(onClick = { showImportDialog = true }) {
                        Text("IMPORT", color = Color.White, fontWeight = FontWeight.Black)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Series") },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = Color.White
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF5F7FA))
        ) {
            if (tournaments.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(60.dp),
                            tint = Color.LightGray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No series active",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { viewModel.createTournament("Sample League", 20) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Create Sample League")
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                        Text("Prepared by Ankoji | v1.59", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(tournaments) { tournament ->
                        TournamentCard(
                            tournament = tournament,
                            viewModel = viewModel,
                            onNavigateToTournament = onNavigateToTournament,
                            onDelete = { viewModel.deleteTournament(tournament.id) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("Prepared by Ankoji | v1.59", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("Setup New Tournament", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = tournamentName,
                            onValueChange = { tournamentName = it },
                            label = { Text("Tournament Name") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = oversCount,
                            onValueChange = { oversCount = it },
                            label = { Text("Overs per Match") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = maxOversPerBowler,
                            onValueChange = { maxOversPerBowler = it },
                            label = { Text("Base Max Overs (e.g. 2)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = quotaBowlersCount,
                                onValueChange = { quotaBowlersCount = it },
                                label = { Text("Number of Bowlers") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                placeholder = { Text("e.g. 3") }
                            )
                            OutlinedTextField(
                                value = quotaMaxOvers,
                                onValueChange = { quotaMaxOvers = it },
                                label = { Text("Per Bowler Limit") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                placeholder = { Text("e.g. 3") }
                            )
                        }
                        Text(
                            "Example: 3 bowlers can bowl 3 overs, others bowl 2.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (tournamentName.isNotBlank()) {
                                viewModel.createTournament(
                                    tournamentName, 
                                    oversCount.toIntOrNull() ?: 20,
                                    maxOversPerBowler.toIntOrNull(),
                                    quotaBowlersCount.toIntOrNull(),
                                    quotaMaxOvers.toIntOrNull()
                                )
                                tournamentName = ""
                                maxOversPerBowler = ""
                                quotaBowlersCount = ""
                                quotaMaxOvers = ""
                                showCreateDialog = false
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showImportDialog) {
            AlertDialog(
                onDismissRequest = { showImportDialog = false },
                title = { Text("Import Series Data", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Button(
                            onClick = { filePickerLauncher.launch("*/*") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("PICK .CSD FILE")
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Paste Text (Backup)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = importJson,
                            onValueChange = { importJson = it },
                            label = { Text("CSD Data String") },
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (importJson.isNotBlank()) {
                                val success = viewModel.importTournament(importJson)
                                if (success) {
                                    importJson = ""
                                    showImportDialog = false
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Import Text")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showImportDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun TournamentCard(
    tournament: Tournament,
    viewModel: TournamentViewModel,
    onNavigateToTournament: (String) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onNavigateToTournament(tournament.id) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tournament.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        val json = viewModel.exportTournament(tournament.id)
                        if (json != null) {
                            try {
                                val fileName = "${tournament.name.replace(" ", "_")}.csd"
                                val tempFile = File(context.cacheDir, fileName)
                                tempFile.writeText(json)
                                
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    tempFile
                                )
                                
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    type = "application/octet-stream"
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Export Series Data")
                                context.startActivity(shareIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Export", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    InfoItem("TEAMS", "${tournament.teams.size}")
                    InfoItem("MATCHES", "${tournament.matches.size}")
                }
                
                TextButton(onClick = { onNavigateToTournament(tournament.id) }) {
                    Text("VIEW DETAILS", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun InfoItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontWeight = FontWeight.Bold)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
