package com.example.cricketscorer

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.room.withTransaction
import com.example.cricketscorer.db.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    private val gson = Gson()
    private const val PREFS_NAME = "cricket_scorer_prefs"
    private const val TOURNAMENTS_KEY = "tournaments_data"
    
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val updateMutex = Mutex()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        db = CricketDatabase.getInstance(context)
        
        // Collect data from Room and update StateFlow
        repositoryScope.launch {
            db.tournamentDao().getAllTournamentsFlow().collect { list ->
                _tournaments.value = list.map { it.toDomain() }
            }
        }
        
        // Handle migration from legacy SharedPreferences
        migrateFromPrefsIfNecessary()
    }

    private fun migrateFromPrefsIfNecessary() {
        val json = prefs.getString(TOURNAMENTS_KEY, null)
        if (json != null) {
            repositoryScope.launch {
                updateMutex.withLock {
                    try {
                        val type = object : TypeToken<List<Tournament>>() {}.type
                        val data: List<Tournament> = gson.fromJson(json, type)
                        val tournaments = data.orEmpty().filterNotNull().map { it.safeCopy() }
                        
                        tournaments.forEach { saveTournamentToDb(it) }
                        
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

    private suspend fun saveTournamentToDb(t: Tournament) {
        db.withTransaction {
            db.tournamentDao().insertTournament(t.toEntity())
            
            val teamEntities = t.teams.map { it.toEntity(t.id) }
            db.teamDao().insertTeams(teamEntities)
            
            val playerEntities = t.participants.map { p ->
                val teamId = t.teams.find { team -> team.players.any { tp -> tp.id == p.id } }?.id
                p.toEntity(t.id, teamId)
            }
            db.playerDao().insertPlayers(playerEntities)
            
            t.matches.forEach { m ->
                db.matchDao().insertMatch(m.toEntity())
                db.ballDao().deleteBallsByMatch(m.id)
                db.ballDao().insertBalls(m.ballHistory.map { it.toEntity(m.id) })
            }
        }
    }

    private fun updateTournament(tournamentId: String, action: (Tournament) -> Tournament) {
        repositoryScope.launch {
            updateMutex.withLock {
                val details = db.tournamentDao().getTournamentById(tournamentId) ?: return@withLock
                val tournament = details.toDomain()
                val updated = action(tournament)
                val recalculated = recalculateTournamentStandings(updated)
                saveTournamentToDb(recalculated)
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

    fun importTournament(json: String): Boolean {
        return try {
            val jsonObject = gson.fromJson(json, JsonObject::class.java)
            
            val tournament: Tournament = if (jsonObject.has("type") && jsonObject.get("type").asString == "UNIFIED_BACKUP") {
                val tJson = jsonObject.get("tournament")
                val pJson = jsonObject.get("globalPlaylist")
                
                val playersType = object : TypeToken<List<Player>>() {}.type
                val importedPlayers: List<Player> = gson.fromJson(pJson, playersType)
                importedPlayers.forEach { GlobalPlayerRepository.addPlayer(it.name, it.battingStyle ?: BattingStyle.RHB) }
                
                gson.fromJson(tJson, Tournament::class.java)
            } else {
                gson.fromJson(json, Tournament::class.java)
            }
            
            if (tournament == null || tournament.id.isNullOrBlank() || tournament.name.isNullOrBlank()) {
                return false
            }

            repositoryScope.launch {
                updateMutex.withLock {
                    val recalculated = recalculateTournamentStandings(tournament)
                    saveTournamentToDb(recalculated)
                }
            }
            true
        } catch (e: Exception) {
            Log.e("TournamentRepository", "Failed to import tournament JSON", e)
            false
        }
    }

    fun createTournament(name: String, overs: Int, maxOvers: Int? = null, quotaCount: Int? = null, quotaLimit: Int? = null) {
        repositoryScope.launch {
            updateMutex.withLock {
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
                saveTournamentToDb(tournament)
            }
        }
    }

    fun deleteTournament(id: String) {
        repositoryScope.launch {
            updateMutex.withLock {
                db.tournamentDao().deleteTournamentById(id)
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

    fun addPlayersToTeam(tournamentId: String, teamId: String, players: List<Player>): Boolean {
        val tournament = _tournaments.value.find { it.id == tournamentId } ?: return false
        
        // v2.33.17: Filter out players already in the target team 🏏🚀⚖️🏅
        val targetTeam = tournament.teams.find { it.id == teamId } ?: return false
        val playersToProcess = players.filter { p -> targetTeam.players.none { it.id == p.id } }
        
        if (playersToProcess.isEmpty()) return false

        updateTournament(tournamentId) { t ->
            val playerIdsToMove = playersToProcess.map { it.id }.toSet()

            // 1. Remove moving players from any other teams in this tournament
            val teamsWithRemovals = t.teams.map { team ->
                if (team.id != teamId) {
                    team.copy(players = team.players.filter { it.id !in playerIdsToMove })
                } else team
            }
            
            // 2. Add players to target team
            val updatedTeams = teamsWithRemovals.map { team ->
                if (team.id == teamId) {
                    team.copy(players = team.players + playersToProcess)
                } else team
            }
            
            val updatedParticipants = (t.participants.orEmpty() + playersToProcess).distinctBy { it.id }

            // 3. Update matches with the moved rosters
            val updatedMatches = t.matches.orEmpty().map { match ->
                if (match.status == MatchStatus.LIVE || match.status == MatchStatus.UPCOMING) {
                    var newTeamA = match.teamA
                    var newTeamB = match.teamB

                    val masterA = updatedTeams.find { it.id == match.teamA.id }
                    val masterB = updatedTeams.find { it.id == match.teamB.id }
                    
                    if (masterA != null) newTeamA = match.teamA.copy(players = masterA.players)
                    if (masterB != null) newTeamB = match.teamB.copy(players = masterB.players)

                    match.copy(teamA = newTeamA, teamB = newTeamB)
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
                        teamA = match.teamA.copy(players = match.teamA.players.filter { it.id != playerId }),
                        teamB = match.teamB.copy(players = match.teamB.players.filter { it.id != playerId })
                    )
                } else match
            }
            
            t.safeCopy(teams = updatedTeams, matches = updatedMatches)
        }
    }

    fun updatePlayerDetails(tournamentId: String, teamId: String, playerId: String, newName: String, bStyle: BattingStyle, isCaptain: Boolean, isViceCaptain: Boolean) {
        updateTournament(tournamentId) { t ->
            val updatedTeams = t.teams.map { team ->
                if (team.id == teamId) {
                    val updatedPlayers = team.players.map { player ->
                        if (player.id == playerId) player.copy(name = newName, battingStyle = bStyle, isCaptain = isCaptain, isViceCaptain = isViceCaptain) else player
                    }
                    team.copy(players = updatedPlayers)
                } else team
            }
            
            val updatedMatches = t.matches.map { match ->
                if (match.status == MatchStatus.LIVE || match.status == MatchStatus.UPCOMING) {
                    val updatedTeamA = if (match.teamA.id == teamId || match.teamA.players.any { it.id == playerId }) {
                        match.teamA.copy(players = match.teamA.players.map { p ->
                            if (p.id == playerId) p.copy(name = newName, battingStyle = bStyle, isCaptain = isCaptain, isViceCaptain = isViceCaptain) else p
                        })
                    } else match.teamA
                    
                    val updatedTeamB = if (match.teamB.id == teamId || match.teamB.players.any { it.id == playerId }) {
                        match.teamB.copy(players = match.teamB.players.map { p ->
                            if (p.id == playerId) p.copy(name = newName, battingStyle = bStyle, isCaptain = isCaptain, isViceCaptain = isViceCaptain) else p
                        })
                    } else match.teamB
                    
                    match.copy(teamA = updatedTeamA, teamB = updatedTeamB)
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
            
            val match = Match(
                id = UUID.randomUUID().toString(),
                tournamentId = tournamentId,
                tournamentName = t.name,
                teamA = resetTeamStats(teamA),
                teamB = resetTeamStats(teamB),
                battingTeamId = teamA.id,
                bowlingTeamId = teamB.id,
                oversPerInnings = t.settings.overs,
                maxOversPerBowler = t.settings.maxOversPerBowler,
                dateMillis = scheduledDate ?: System.currentTimeMillis()
            )
            t.safeCopy(matches = t.matches.orEmpty() + match)
        }
    }

    fun deleteMatch(tournamentId: String, matchId: String) {
        updateTournament(tournamentId) { t ->
            val filteredMatches = t.matches.orEmpty().filter { it.id != matchId }
            t.safeCopy(matches = filteredMatches)
        }
    }

    fun updateMatch(tournamentId: String, updatedMatch: Match) {
        updateTournament(tournamentId) { t ->
            val updatedMatches = t.matches.orEmpty().map { if (it.id == updatedMatch.id) updatedMatch else it }
            t.safeCopy(matches = updatedMatches)
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
