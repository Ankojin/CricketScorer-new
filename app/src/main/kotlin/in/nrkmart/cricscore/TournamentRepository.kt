package `in`.nrkmart.cricscore

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.room.withTransaction
import `in`.nrkmart.cricscore.db.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

object TournamentRepository {
    private val _tournaments = MutableStateFlow<List<Tournament>>(emptyList())
    val tournaments: StateFlow<List<Tournament>> = _tournaments.asStateFlow()

    private lateinit var db: CricketDatabase
    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context
    @Volatile private var activeProfileId: String? = null
    @Volatile private var databaseProfileId: String? = null
    private var tournamentFlowJob: Job? = null
    private val profileLock = Any()
    private val gson = Gson()
    private const val PREFS_NAME = "cricket_scorer_prefs"
    private const val TOURNAMENTS_KEY = "tournaments_data"
    
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val updateMutex = Mutex()

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val guestDb = CricketDatabase.getInstance(appContext)
        db = guestDb
        activeProfileId = null
        databaseProfileId = null
        observeProfileDatabase(guestDb, null)
        
        // Handle migration from legacy SharedPreferences
        migrateFromPrefsIfNecessary(guestDb)
    }

    fun switchProfile(userId: String?) {
        val normalizedUserId = userId?.takeIf { it.isNotBlank() }
        synchronized(profileLock) {
            if (activeProfileId == normalizedUserId && databaseProfileId == normalizedUserId && ::db.isInitialized) return
            activeProfileId = normalizedUserId
            _tournaments.value = emptyList()
            if (!::appContext.isInitialized) return

            tournamentFlowJob?.cancel()
            db = CricketDatabase.getInstance(appContext, normalizedUserId)
            databaseProfileId = normalizedUserId
            observeProfileDatabase(db, normalizedUserId)
        }
    }

    private fun observeProfileDatabase(database: CricketDatabase, profileId: String?) {
        tournamentFlowJob = repositoryScope.launch {
            database.tournamentDao().getAllTournamentsFlow().collect { list ->
                if (activeProfileId == profileId && databaseProfileId == profileId) {
                    _tournaments.value = list.map { it.toDomain() }
                }
            }
        }
    }

    private fun migrateFromPrefsIfNecessary(guestDb: CricketDatabase) {
        val json = prefs.getString(TOURNAMENTS_KEY, null)
        if (json != null) {
            repositoryScope.launch {
                updateMutex.withLock {
                    try {
                        val type = object : TypeToken<List<Tournament>>() {}.type
                        val data: List<Tournament> = gson.fromJson(json, type)
                        val tournaments = data.orEmpty().filterNotNull().map { it.safeCopy() }
                        
                        tournaments.forEach { saveTournamentToDb(guestDb, it) }
                        
                        prefs.edit().remove(TOURNAMENTS_KEY).apply()
                        Log.d("TournamentRepository", "Migration from SharedPreferences completed successfully")
                    } catch (e: Exception) {
                        Log.e("TournamentRepository", "Migration failed", e)
                        // If parsing fails, we clear to avoid infinite loops, but usually safeCopy handles it
                        prefs.edit().remove(TOURNAMENTS_KEY).apply()
                    }
                }
            }
        }
    }

    private suspend fun saveTournamentToDb(tRaw: Tournament) = saveTournamentToDb(db, tRaw)

    private suspend fun saveTournamentToDb(targetDb: CricketDatabase, tRaw: Tournament) {
        val t = tRaw.safeCopy()
        targetDb.withTransaction {
            targetDb.tournamentDao().insertTournament(t.toEntity())
            
            val teams = t.teams.orEmpty().map { it.safeCopy() }
            val teamEntities = teams.map { it.toEntity(t.id) }
            targetDb.teamDao().insertTeams(teamEntities)
            
            val participants = t.participants.orEmpty().map { it.safeCopy() }
            val playerEntities = participants.map { p ->
                val teamId = teams.find { team -> team.players.orEmpty().any { tp -> tp.id == p.id } }?.id
                p.toEntity(t.id, teamId)
            }
            targetDb.playerDao().insertPlayers(playerEntities)
            
            t.matches.orEmpty().forEach { mRaw ->
                val m = mRaw.safeCopy()
                targetDb.matchDao().insertMatch(m.toEntity())
                targetDb.ballDao().deleteBallsByMatch(m.id)
                targetDb.ballDao().insertBalls(m.ballHistory.orEmpty().map { it.toEntity(m.id) })
            }
        }
    }

    private fun updateTournament(tournamentId: String, action: (Tournament) -> Tournament) {
        if (!::db.isInitialized) return
        repositoryScope.launch {
            updateMutex.withLock {
                val profileId = activeProfileId
                if (profileId != databaseProfileId) return@withLock
                val targetDb = db
                val details = targetDb.tournamentDao().getTournamentById(tournamentId) ?: return@withLock
                val tournament = details.toDomain()
                val updated = action(tournament)
                val recalculated = recalculateTournamentStandings(updated)
                if (profileId != activeProfileId || profileId != databaseProfileId) return@withLock
                if (CloudSyncManager.canUploadTournament(recalculated.id)) {
                    CloudSyncManager.enqueueTournamentUpsert(recalculated)
                }
                saveTournamentToDb(targetDb, recalculated)
            }
        }
    }

    fun exportTournament(id: String): String? {
        val tournament = _tournaments.value.find { it.id == id } ?: return null
        val exportPackage = mapOf(
            "version" to "v2.28.0",
            "type" to "UNIFIED_BACKUP",
            "tournament" to tournament,
            "globalPlaylist" to GlobalPlayerRepository.players.value
        )
        return try {
            gson.toJson(exportPackage)
        } catch (e: Exception) {
            null
        }
    }

    fun importTournament(json: String, expectedProfileId: String? = activeProfileId): Boolean {
        if (expectedProfileId != activeProfileId || expectedProfileId != databaseProfileId || !::db.isInitialized) return false
        val targetDb = db
        return try {
            val jsonObject = gson.fromJson(json, JsonObject::class.java) ?: return false
            val allImportedGlobalPlayers = mutableListOf<Player>()
            
            // 1. Collect Global Playlist if present
            if (jsonObject.has("globalPlaylist")) {
                val pJson = jsonObject.get("globalPlaylist")
                if (pJson != null && !pJson.isJsonNull) {
                    try {
                        val playersType = object : TypeToken<List<Player>>() {}.type
                        val importedPlayers: List<Player>? = gson.fromJson(pJson, playersType)
                        allImportedGlobalPlayers.addAll(importedPlayers.orEmpty().filterNotNull())
                    } catch (e: Exception) {
                        Log.w("TournamentRepository", "Skipped playlist import due to error", e)
                    }
                }
            }

            // 2. Resolve Tournament / Series object
            val rawTournament: Tournament? = when {
                jsonObject.has("type") && jsonObject.get("type")?.asString == "UNIFIED_BACKUP" -> {
                    val tJson = jsonObject.get("tournament")
                    if (tJson != null && !tJson.isJsonNull) {
                        gson.fromJson(tJson, Tournament::class.java)
                    } else null
                }
                jsonObject.has("type") && jsonObject.get("type")?.asString == "MATCH_BACKUP" || jsonObject.has("match") -> {
                    val mJson = if (jsonObject.has("match")) jsonObject.get("match") else jsonObject
                    val importedMatch: Match? = gson.fromJson(mJson, Match::class.java)
                    if (importedMatch != null) {
                        val seriesName = importedMatch.tournamentName?.takeIf { it.isNotBlank() } ?: "Imported Matches"
                        val existingSeries = _tournaments.value.find { it.name.equals(seriesName, ignoreCase = true) }
                        val series = existingSeries ?: Tournament(
                            id = importedMatch.tournamentId ?: UUID.randomUUID().toString(),
                            name = seriesName,
                            teams = listOf(importedMatch.teamA, importedMatch.teamB).filterNotNull().distinctBy { it.id },
                            settings = TournamentSettings(overs = importedMatch.oversPerInnings)
                        )
                        val updatedTeams = (series.teams.orEmpty() + listOf(importedMatch.teamA, importedMatch.teamB)).distinctBy { it.id }
                        val updatedMatches = (series.matches.orEmpty().filter { it.id != importedMatch.id } + importedMatch)
                        series.copy(teams = updatedTeams, matches = updatedMatches)
                    } else null
                }
                else -> {
                    try {
                        gson.fromJson(json, Tournament::class.java)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            
            if (rawTournament == null) {
                return false
            }

            val tournament = rawTournament.safeCopy()
            if (tournament.id.isBlank() || tournament.name.isBlank()) {
                return false
            }

            // Collect team & participant players into global playlist as well
            tournament.teams.orEmpty().forEach { team ->
                allImportedGlobalPlayers.addAll(team.players.orEmpty().filterNotNull())
            }
            allImportedGlobalPlayers.addAll(tournament.participants.orEmpty().filterNotNull())

            // Merge with existing series if ID or Name matches to avoid data loss
            val existingSeries = _tournaments.value.find { 
                it.id == tournament.id || it.name.equals(tournament.name, ignoreCase = true) 
            }

            val finalTournamentToSave = if (existingSeries != null) {
                val mergedMatches = (existingSeries.matches + tournament.matches).distinctBy { it.id }
                val mergedTeams = (existingSeries.teams + tournament.teams).distinctBy { it.id }
                val mergedParticipants = (existingSeries.participants + tournament.participants).distinctBy { it.id }
                existingSeries.copy(
                    teams = mergedTeams,
                    matches = mergedMatches,
                    participants = mergedParticipants
                )
            } else {
                tournament
            }

            repositoryScope.launch {
                updateMutex.withLock {
                    if (expectedProfileId != activeProfileId || expectedProfileId != databaseProfileId) return@withLock
                    if (allImportedGlobalPlayers.isNotEmpty()) {
                        GlobalPlayerRepository.importPlayers(allImportedGlobalPlayers)
                    }
                    val recalculated = recalculateTournamentStandings(finalTournamentToSave)
                    saveTournamentToDb(targetDb, recalculated)
                }
            }
            true
        } catch (e: Exception) {
            Log.e("TournamentRepository", "Failed to import tournament/match JSON", e)
            false
        }
    }

    fun importCloudTournament(tournament: Tournament, expectedProfileId: String? = activeProfileId): Boolean {
        if (expectedProfileId != activeProfileId || expectedProfileId != databaseProfileId) return false
        val localMatches = _tournaments.value
            .firstOrNull { it.id == tournament.id }
            ?.matches
            .orEmpty()
        val merged = tournament.safeCopy(matches = localMatches)
        val wrapper = JsonObject().apply {
            addProperty("type", "UNIFIED_BACKUP")
            add("tournament", gson.toJsonTree(merged))
        }
        return importTournament(gson.toJson(wrapper), expectedProfileId)
    }

    fun createTournament(name: String, overs: Int, maxOvers: Int? = null, quotaCount: Int? = null, quotaLimit: Int? = null) {
        repositoryScope.launch {
            updateMutex.withLock {
                val profileId = activeProfileId
                if (profileId != databaseProfileId) return@withLock
                val targetDb = db
                val tournament = Tournament(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    settings = TournamentSettings(
                        overs = overs, 
                        maxOversPerBowler = maxOvers,
                        quotaBowlersCount = quotaCount,
                        quotaMaxOvers = quotaLimit
                    )
                )
                CloudSyncManager.claimTournamentForCurrentUser(tournament.id)
                if (profileId != activeProfileId || profileId != databaseProfileId) return@withLock
                saveTournamentToDb(targetDb, tournament)
                CloudSyncManager.enqueueTournamentUpsert(tournament)
            }
        }
    }

    fun deleteTournament(id: String) {
        CloudSyncManager.enqueueTournamentDelete(id)
        repositoryScope.launch {
            updateMutex.withLock {
                val profileId = activeProfileId
                if (profileId != databaseProfileId) return@withLock
                val targetDb = db
                targetDb.tournamentDao().deleteTournamentById(id)
            }
        }
    }

    fun addTeamToTournament(tournamentId: String, teamName: String, colorHex: String? = null) {
        updateTournament(tournamentId) { t ->
            val otherTeamColors = t.teams.mapNotNull { it.colorHex }
            val finalColor = if (colorHex != null && otherTeamColors.any { it.equals(colorHex, ignoreCase = true) }) {
                null
            } else {
                colorHex
            }
            val newTeam = Team(id = UUID.randomUUID().toString(), name = teamName, colorHex = finalColor)
            t.safeCopy(teams = t.teams.orEmpty() + newTeam)
        }
    }

    fun updateTeamDetails(tournamentId: String, teamId: String, newName: String, newColorHex: String?): Boolean {
        var success = true
        updateTournament(tournamentId) { t ->
            val otherTeamColors = t.teams.filter { it.id != teamId }.mapNotNull { it.colorHex }
            if (newColorHex != null && otherTeamColors.any { it.equals(newColorHex, ignoreCase = true) }) {
                success = false
                return@updateTournament t
            }
            
            val updatedTeams = t.teams.map { team ->
                if (team.id == teamId) {
                    team.copy(name = newName.trim(), colorHex = newColorHex)
                } else team
            }
            t.safeCopy(teams = updatedTeams)
        }
        return success
    }

    fun deleteTeam(tournamentId: String, teamId: String) {
        updateTournament(tournamentId) { t ->
            t.safeCopy(teams = t.teams.orEmpty().filter { it.id != teamId })
        }
    }

    fun addPlayerToTeam(tournamentId: String, teamId: String, playerName: String, bStyle: BattingStyle = BattingStyle.RHB, isCaptain: Boolean = false, isViceCaptain: Boolean = false): Boolean {
        val tournament = _tournaments.value.find { it.id == tournamentId } ?: return false
        val trimmedName = playerName.trim()
        
        // v2.33.17: Rotation Logic - Find existing player by name in tournament or global list 🏏🚀⚖️🏅
        val existingInTournament = tournament.teams.flatMap { it.players }.find { it.name.trim().equals(trimmedName, ignoreCase = true) }
        val globalMaster = GlobalPlayerRepository.players.value.find { it.name.trim().equals(trimmedName, ignoreCase = true) }
        
        val playerToAdd = when {
            existingInTournament != null -> existingInTournament.copy(battingStyle = bStyle, isCaptain = isCaptain, isViceCaptain = isViceCaptain)
            globalMaster != null -> globalMaster.copy(battingStyle = bStyle, isCaptain = isCaptain, isViceCaptain = isViceCaptain)
            else -> Player(
                id = UUID.randomUUID().toString(), 
                name = trimmedName,
                battingStyle = bStyle,
                isCaptain = isCaptain,
                isViceCaptain = isViceCaptain
            )
        }
        
        return addPlayersToTeam(tournamentId, teamId, listOf(playerToAdd))
    }

    fun addPlayersToTeam(
        tournamentId: String,
        teamId: String,
        players: List<Player>,
        allowCommonPlayer: Boolean = false
    ): Boolean {
        val tournament = _tournaments.value.find { it.id == tournamentId } ?: return false
        
        // v2.33.17: Filter out players already in the target team 🏏🚀⚖️🏅
        val targetTeam = tournament.teams.find { it.id == teamId } ?: return false
        val playersToProcess = players.filter { p -> targetTeam.players.none { it.id == p.id } }
        
        if (playersToProcess.isEmpty()) return false

        updateTournament(tournamentId) { t ->
            val playerIdsToMove = playersToProcess.map { it.id }.toSet()

            // A common player remains in both squads. Otherwise, adding them moves them.
            val teamsWithRemovals = t.teams.map { team ->
                if (!allowCommonPlayer && team.id != teamId) {
                    team.copy(players = team.players.filter { it.id !in playerIdsToMove })
                } else team
            }
            
            // 2. Add players to target team
            var captainSeen = false
            var viceCaptainSeen = false
            val sanitizedPlayersToProcess = playersToProcess.map { p ->
                val pCaptain = p.isCaptain && !captainSeen
                if (pCaptain) captainSeen = true
                val pViceCaptain = p.isViceCaptain && !pCaptain && !viceCaptainSeen
                if (pViceCaptain) viceCaptainSeen = true
                p.copy(isCaptain = pCaptain, isViceCaptain = pViceCaptain)
            }

            val hasNewCaptain = sanitizedPlayersToProcess.any { it.isCaptain }
            val hasNewViceCaptain = sanitizedPlayersToProcess.any { it.isViceCaptain }

            val updatedTeams = teamsWithRemovals.map { team ->
                if (team.id == teamId) {
                    val adjustedExisting = if (hasNewCaptain || hasNewViceCaptain) {
                        team.players.map { p ->
                            p.copy(
                                isCaptain = if (hasNewCaptain) false else p.isCaptain,
                                isViceCaptain = if (hasNewViceCaptain) false else p.isViceCaptain
                            )
                        }
                    } else team.players
                    team.copy(players = adjustedExisting + sanitizedPlayersToProcess)
                } else team
            }
            
            val updatedParticipants = (t.participants.orEmpty() + sanitizedPlayersToProcess).distinctBy { it.id }

            // 3. Update matches with the moved rosters
            val updatedMatches = t.matches.orEmpty().map { match ->
                if (match.status == MatchStatus.LIVE || match.status == MatchStatus.UPCOMING) {
                    var newTeamA = match.teamA
                    var newTeamB = match.teamB

                    val masterA = updatedTeams.find { it.id == match.teamA.id }
                    val masterB = updatedTeams.find { it.id == match.teamB.id }
                    
                    if (masterA != null) newTeamA = match.teamA.copy(players = masterA.players)
                    if (masterB != null) newTeamB = match.teamB.copy(players = masterB.players)

                    val resolvedTeamACaptainId = newTeamA.players.firstOrNull { it.isCaptain }?.id
                    val resolvedTeamBCaptainId = newTeamB.players.firstOrNull { it.isCaptain }?.id

                    match.copy(
                        teamA = newTeamA, 
                        teamB = newTeamB,
                        teamACaptainId = resolvedTeamACaptainId,
                        teamBCaptainId = resolvedTeamBCaptainId
                    )
                } else match
            }

            t.safeCopy(teams = updatedTeams, matches = updatedMatches, participants = updatedParticipants)
        }
        return true
    }

    fun deletePlayer(tournamentId: String, teamId: String, playerId: String) {
        updateTournament(tournamentId) { t ->
            val updatedTeams = t.teams.map { team ->
                if (team.id == teamId) {
                    team.copy(players = team.players.filter { it.id != playerId })
                } else team
            }
            
            val updatedMatches = t.matches.map { match ->
                if (match.status == MatchStatus.LIVE || match.status == MatchStatus.UPCOMING) {
                    match.copy(
                        teamA = if (match.teamA.id == teamId) match.teamA.copy(players = match.teamA.players.filter { it.id != playerId }) else match.teamA,
                        teamB = if (match.teamB.id == teamId) match.teamB.copy(players = match.teamB.players.filter { it.id != playerId }) else match.teamB
                    )
                } else match
            }
            
            t.safeCopy(teams = updatedTeams, matches = updatedMatches)
        }
    }

    fun updatePlayerDetails(tournamentId: String, teamId: String, playerId: String, newName: String, bStyle: BattingStyle, isCaptain: Boolean, isViceCaptain: Boolean) {
        updateTournament(tournamentId) { t ->
            fun updateRosterIfContains(players: List<Player>): List<Player> {
                if (players.none { it.id == playerId }) return players
                return players.map { player ->
                    if (player.id == playerId) {
                        player.copy(name = newName, battingStyle = bStyle, isCaptain = isCaptain, isViceCaptain = isViceCaptain)
                    } else {
                        player.copy(
                            isCaptain = if (isCaptain) false else player.isCaptain,
                            isViceCaptain = if (isViceCaptain) false else player.isViceCaptain
                        )
                    }
                }
            }

            val updatedTeams = t.teams.map { team ->
                if (team.id == teamId) {
                    val updatedPlayers = updateRosterIfContains(team.players)
                    team.copy(players = updatedPlayers)
                } else team
            }
            
            val updatedMatches = t.matches.map { match ->
                if (match.status == MatchStatus.LIVE || match.status == MatchStatus.UPCOMING) {
                    val updatedTeamAPlayers = updateRosterIfContains(match.teamA.players)
                    val updatedTeamA = if (updatedTeamAPlayers !== match.teamA.players) {
                        match.teamA.copy(players = updatedTeamAPlayers)
                    } else match.teamA
                    
                    val updatedTeamBPlayers = updateRosterIfContains(match.teamB.players)
                    val updatedTeamB = if (updatedTeamBPlayers !== match.teamB.players) {
                        match.teamB.copy(players = updatedTeamBPlayers)
                    } else match.teamB

                    val resolvedTeamACaptainId = updatedTeamA.players.firstOrNull { it.isCaptain }?.id
                    val resolvedTeamBCaptainId = updatedTeamB.players.firstOrNull { it.isCaptain }?.id
                    
                    match.copy(
                        teamA = updatedTeamA,
                        teamB = updatedTeamB,
                        teamACaptainId = resolvedTeamACaptainId,
                        teamBCaptainId = resolvedTeamBCaptainId
                    )
                } else match
            }
            
            t.safeCopy(teams = updatedTeams, matches = updatedMatches)
        }
    }

    fun togglePlayerJokerStatus(tournamentId: String, teamId: String, playerId: String) {
        updateTournament(tournamentId) { t ->
            val updatedTeams = t.teams.map { team ->
                if (team.id == teamId) {
                    val updatedPlayers = team.players.map { player ->
                        if (player.id == playerId) player.copy(isJoker = !player.isJoker) else player
                    }
                    team.copy(players = updatedPlayers)
                } else team
            }

            val jokerPlayer = updatedTeams.flatMap { it.players }.find { it.id == playerId } ?: return@updateTournament t

            val updatedMatches = t.matches.map { match ->
                if (match.status == MatchStatus.LIVE || match.status == MatchStatus.UPCOMING) {
                    var newTeamA = match.teamA
                    var newTeamB = match.teamB

                    if (jokerPlayer.isJoker) {
                        if (newTeamA.players.none { it.id == playerId }) {
                            newTeamA = newTeamA.copy(players = newTeamA.players + jokerPlayer)
                        } else {
                            newTeamA = newTeamA.copy(players = newTeamA.players.map { if (it.id == playerId) jokerPlayer else it })
                        }
                        if (newTeamB.players.none { it.id == playerId }) {
                            newTeamB = newTeamB.copy(players = newTeamB.players + jokerPlayer)
                        } else {
                            newTeamB = newTeamB.copy(players = newTeamB.players.map { if (it.id == playerId) jokerPlayer else it })
                        }
                    } else {
                        val belongsInA = updatedTeams.find { it.id == newTeamA.id }?.players?.any { it.id == playerId } == true
                        val belongsInB = updatedTeams.find { it.id == newTeamB.id }?.players?.any { it.id == playerId } == true

                        newTeamA = if (belongsInA) {
                            newTeamA.copy(players = newTeamA.players.map { if (it.id == playerId) jokerPlayer else it })
                        } else {
                            newTeamA.copy(players = newTeamA.players.filter { it.id != playerId })
                        }

                        newTeamB = if (belongsInB) {
                            newTeamB.copy(players = newTeamB.players.map { if (it.id == playerId) jokerPlayer else it })
                        } else {
                            newTeamB.copy(players = newTeamB.players.filter { it.id != playerId })
                        }
                    }
                    match.copy(teamA = newTeamA, teamB = newTeamB)
                } else match
            }

            t.safeCopy(teams = updatedTeams, matches = updatedMatches)
        }
    }

    fun scheduleMatch(
        tournamentId: String, 
        teamAId: String, 
        teamBId: String, 
        scheduledDate: Long? = null
    ) {
        updateTournament(tournamentId) { t ->
            val teamA = t.teams.find { it.id == teamAId } ?: return@updateTournament t
            val teamB = t.teams.find { it.id == teamBId } ?: return@updateTournament t
            val teamACaptainId = teamA.players.firstOrNull { it.isCaptain }?.id
            val teamBCaptainId = teamB.players.firstOrNull { it.isCaptain }?.id
            
            val match = Match(
                id = UUID.randomUUID().toString(),
                tournamentId = tournamentId,
                tournamentName = t.name,
                teamA = resetTeamStats(teamA),
                teamB = resetTeamStats(teamB),
                teamACaptainId = teamACaptainId,
                teamBCaptainId = teamBCaptainId,
                battingTeamId = teamA.id,
                bowlingTeamId = teamB.id,
                oversPerInnings = t.settings.overs,
                maxOversPerBowler = t.settings.maxOversPerBowler,
                dateMillis = scheduledDate ?: System.currentTimeMillis(),
                gullyRules = GullyRulesRepository.gullyRules.value
            )
            if (CloudSyncManager.canUploadTournament(tournamentId)) {
                CloudSyncManager.claimMatchForCurrentUser(match.id)
                CloudSyncManager.enqueueMatchUpsert(match)
            }
            t.safeCopy(matches = t.matches.orEmpty() + match)
        }
    }

    fun deleteMatch(tournamentId: String, matchId: String) {
        CloudSyncManager.enqueueMatchDelete(matchId)
        updateTournament(tournamentId) { t ->
            val filteredMatches = t.matches.orEmpty().filter { it.id != matchId }
            t.safeCopy(matches = filteredMatches)
        }
    }

    fun updateMatch(tournamentId: String, updatedMatch: Match) {
        CloudSyncManager.enqueueMatchUpsert(updatedMatch)
        updateTournament(tournamentId) { t ->
            val updatedMatches = t.matches.orEmpty().map { if (it.id == updatedMatch.id) updatedMatch else it }
            t.safeCopy(matches = updatedMatches)
        }
    }

    fun updateMatchSyncMetadata(matchId: String, revision: Int?, updatedAt: String?) {
        val match = _tournaments.value.asSequence()
            .flatMap { it.matches.orEmpty().asSequence() }
            .firstOrNull { it.id == matchId } ?: return
        val tournamentId = match.tournamentId ?: return
        updateTournament(tournamentId) { tournament ->
            val updatedMatches = tournament.matches.orEmpty().map {
                if (it.id == matchId) it.copy(revision = revision, updatedAt = updatedAt) else it
            }
            tournament.safeCopy(matches = updatedMatches)
        }
    }

    private fun resetTeamStats(team: Team): Team {
        return team.copy(
            matchesPlayed = 0,
            wins = 0,
            losses = 0,
            points = 0,
            nrr = 0.0,
            players = team.players.map { player ->
                player.copy(
                    battingStats = BattingStats(),
                    bowlingStats = BowlingStats(),
                    fieldingStats = FieldingStats()
                )
            }
        )
    }

    private fun recalculateTournamentStandings(tournament: Tournament): Tournament {
        val safeTeams = tournament.teams.orEmpty().filterNotNull()
        val resetTeams = safeTeams.map { resetTeamStats(it) }
        
        var currentParticipants = tournament.participants.orEmpty().map { it.copy(battingStats = BattingStats(), bowlingStats = BowlingStats(), fieldingStats = FieldingStats()) }
        var currentTeams = resetTeams
        
        // v2.33.16: Run all matches through the ScoringEngine before aggregating 🏏🚀⚖️🏅
        // This ensures standings are based on calculated match data, not raw DB snapshots.
        tournament.matches.orEmpty().filterNotNull().forEach { match ->
            val calculatedMatch = ScoringEngine.recalculateMatchFromHistory(match)
            
            if (calculatedMatch.status == MatchStatus.COMPLETED) {
                currentTeams = updateTeamStandings(currentTeams, calculatedMatch)
            }
            currentParticipants = aggregateParticipantStats(currentParticipants, calculatedMatch)
        }
        
        val finalizedTeams = currentTeams.map { team ->
            team.copy(players = team.players.orEmpty().filterNotNull().map { tp ->
                currentParticipants.find { it.id == tp.id } ?: tp
            })
        }
        
        return tournament.copy(
            teams = finalizedTeams,
            participants = currentParticipants
        )
    }

    private fun aggregateParticipantStats(participants: List<Player>, match: Match): List<Player> {
        val teamAPlayers = match.teamA?.players.orEmpty()
        val teamBPlayers = match.teamB?.players.orEmpty()
        val allMatchPlayers = (teamAPlayers + teamBPlayers)
        val updatedParticipants = participants.toMutableList()

        allMatchPlayers.forEach { mp ->
            if (mp == null) return@forEach
            val index = updatedParticipants.indexOfFirst { it.id == mp.id }
            if (index != -1) {
                val tp = updatedParticipants[index]
                updatedParticipants[index] = tp.copy(
                    battingStats = tp.battingStats.copy(
                        runs = tp.battingStats.runs + mp.battingStats.runs,
                        balls = tp.battingStats.balls + mp.battingStats.balls,
                        fours = tp.battingStats.fours + mp.battingStats.fours,
                        sixes = tp.battingStats.sixes + mp.battingStats.sixes,
                        isOut = tp.battingStats.isOut || mp.battingStats.isOut
                    ),
                    bowlingStats = tp.bowlingStats.copy(
                        wickets = tp.bowlingStats.wickets + mp.bowlingStats.wickets,
                        runsConceded = tp.bowlingStats.runsConceded + mp.bowlingStats.runsConceded,
                        balls = tp.bowlingStats.balls + mp.bowlingStats.balls,
                        overs = tp.bowlingStats.overs + mp.bowlingStats.overs,
                        dotBalls = tp.bowlingStats.dotBalls + mp.bowlingStats.dotBalls,
                        wides = tp.bowlingStats.wides + mp.bowlingStats.wides,
                        noBalls = tp.bowlingStats.noBalls + mp.bowlingStats.noBalls
                    ),
                    fieldingStats = tp.fieldingStats.copy(
                        catches = tp.fieldingStats.catches + mp.fieldingStats.catches,
                        runOuts = tp.fieldingStats.runOuts + mp.fieldingStats.runOuts,
                        stumpings = tp.fieldingStats.stumpings + mp.fieldingStats.stumpings
                    )
                )
            } else {
                updatedParticipants.add(mp)
            }
        }
        return updatedParticipants
    }

    private fun updateTeamStandings(teams: List<Team>, match: Match): List<Team> {
        return teams.map { team ->
            if (match.teamA == null || match.teamB == null) return@map team
            
            if (team.id == match.teamA.id || team.id == match.teamB.id) {
                val won = match.winnerId == team.id
                val lost = match.winnerId != null && match.winnerId != team.id
                val draw = match.status == MatchStatus.COMPLETED && match.winnerId == null
                
                team.copy(
                    matchesPlayed = team.matchesPlayed + 1,
                    wins = team.wins + if (won) 1 else 0,
                    losses = team.losses + if (lost) 1 else 0,
                    points = team.points + (if (won) 2 else if (draw) 1 else 0),
                    nrr = team.nrr + (if (won) 0.5 else if (lost) -0.5 else 0.0) 
                )
            } else team
        }
    }

    fun getTournament(id: String): Tournament? {
        return _tournaments.value.find { it.id == id }
    }

    fun updateTournamentSettings(tournamentId: String, overs: Int, maxOvers: Int?, quotaCount: Int?, quotaLimit: Int?) {
        updateTournament(tournamentId) { t ->
            t.safeCopy(settings = t.settings.copy(
                overs = overs, 
                maxOversPerBowler = maxOvers,
                quotaBowlersCount = quotaCount,
                quotaMaxOvers = quotaLimit
            ))
        }
    }

    fun setupE2ETestData() {
        repositoryScope.launch {
            val tournamentId = "e2e-test-tournament"
            val teamAId = "e2e-team-india"
            val teamBId = "e2e-team-australia"

            val playersA = listOf("Virat", "Rohit", "Rahul", "Hardik", "Bumrah").map { name ->
                Player(id = "player-in-$name".lowercase(), name = name)
            }
            val playersB = listOf("Warner", "Smith", "Maxwell", "Cummins", "Starc").map { name ->
                Player(id = "player-au-$name".lowercase(), name = name)
            }

            val teamA = Team(id = teamAId, name = "India", players = playersA)
            val teamB = Team(id = teamBId, name = "Australia", players = playersB)

            val tournament = Tournament(
                id = tournamentId,
                name = "E2E Test Series",
                teams = listOf(teamA, teamB),
                settings = TournamentSettings(overs = 5, maxOversPerBowler = 2),
                participants = playersA + playersB
            )

            saveTournamentToDb(tournament)
            
            // Give time for Room to emit and _tournaments to update before scheduling
            delay(800)
            scheduleMatch(tournamentId, teamAId, teamBId)
        }
    }
}
