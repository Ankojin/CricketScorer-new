package com.example.cricketscorer

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

object TournamentRepository {
    private val _tournaments = MutableStateFlow<List<Tournament>>(emptyList())
    val tournaments: StateFlow<List<Tournament>> = _tournaments.asStateFlow()

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()
    private const val PREFS_NAME = "cricket_scorer_prefs"
    private const val TOURNAMENTS_KEY = "tournaments_data"

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadFromDisk()
    }

    private fun loadFromDisk() {
        try {
            val json = prefs.getString(TOURNAMENTS_KEY, null)
            if (json != null) {
                val type = object : TypeToken<List<Tournament>>() {}.type
                val data: List<Tournament> = gson.fromJson(json, type)
                _tournaments.value = data
            }
        } catch (e: Exception) {
            Log.e("TournamentRepository", "Failed to load data, clearing prefs", e)
            prefs.edit().remove(TOURNAMENTS_KEY).apply()
        }
    }

    private fun saveToDisk(data: List<Tournament>) {
        try {
            val json = gson.toJson(data)
            prefs.edit().putString(TOURNAMENTS_KEY, json).apply()
        } catch (e: Exception) {
            Log.e("TournamentRepository", "Failed to save data", e)
        }
    }

    fun exportTournament(id: String): String? {
        val tournament = _tournaments.value.find { it.id == id } ?: return null
        return try {
            gson.toJson(tournament)
        } catch (e: Exception) {
            null
        }
    }

    fun importTournament(json: String): Boolean {
        return try {
            val type = object : TypeToken<Tournament>() {}.type
            val imported: Tournament = gson.fromJson(json, type)
            
            // Validate that required fields are present and not empty
            if (imported == null || imported.id.isNullOrBlank() || imported.name.isNullOrBlank()) {
                Log.e("TournamentRepository", "Import failed: Missing required fields in JSON")
                return false
            }

            // Recalculate standings and stats from match history to ensure consistency
            val recalculated = recalculateTournamentStandings(imported)

            _tournaments.update { list ->
                val newList = list.filter { it.id != recalculated.id } + recalculated
                saveToDisk(newList)
                newList
            }
            true
        } catch (e: Exception) {
            Log.e("TournamentRepository", "Failed to import tournament JSON", e)
            false
        }
    }

    fun createTournament(name: String, overs: Int, maxOvers: Int? = null, quotaCount: Int? = null, quotaLimit: Int? = null) {
        _tournaments.update { list ->
            val newList = list + Tournament(
                id = UUID.randomUUID().toString(),
                name = name,
                settings = TournamentSettings(
                    overs = overs, 
                    maxOversPerBowler = maxOvers,
                    quotaBowlersCount = quotaCount,
                    quotaMaxOvers = quotaLimit
                )
            )
            saveToDisk(newList)
            newList
        }
    }

    fun deleteTournament(id: String) {
        _tournaments.update { list -> 
            val newList = list.filter { it.id != id }
            saveToDisk(newList)
            newList
        }
    }

    fun addTeamToTournament(tournamentId: String, teamName: String) {
        _tournaments.update { list ->
            val newList = list.map { t ->
                if (t.id == tournamentId) {
                    val newTeam = Team(id = UUID.randomUUID().toString(), name = teamName)
                    t.copy(teams = t.teams + newTeam)
                } else t
            }
            saveToDisk(newList)
            newList
        }
    }

    fun deleteTeam(tournamentId: String, teamId: String) {
        _tournaments.update { list ->
            val newList = list.map { t ->
                if (t.id == tournamentId) {
                    t.copy(teams = t.teams.filter { it.id != teamId })
                } else t
            }
            saveToDisk(newList)
            newList
        }
    }

    fun addPlayerToTeam(tournamentId: String, teamId: String, playerName: String, bStyle: BattingStyle = BattingStyle.RHB, bowlStyle: BowlingStyle = BowlingStyle.RFM, isCaptain: Boolean = false, isViceCaptain: Boolean = false): Boolean {
        var added = false
        val trimmedName = playerName.trim()
        
        _tournaments.update { list ->
            val tournament = list.find { it.id == tournamentId } ?: return@update list
            val isDuplicate = tournament.teams.flatMap { it.players }.any { 
                it.name.trim().equals(trimmedName, ignoreCase = true) 
            }
            
            if (isDuplicate) {
                added = false
                return@update list
            }

            added = true
            val newList = list.map { t ->
                if (t.id == tournamentId) {
                    var createdPlayer: Player? = null
                    val updatedTeams = t.teams.map { team ->
                        if (team.id == teamId) {
                            val newPlayer = Player(
                                id = UUID.randomUUID().toString(), 
                                name = trimmedName,
                                battingStyle = bStyle,
                                bowlingStyle = bowlStyle,
                                isCaptain = isCaptain,
                                isViceCaptain = isViceCaptain
                            )
                            createdPlayer = newPlayer
                            team.copy(players = team.players + newPlayer)
                        } else team
                    }

                    val playerToAdd = createdPlayer
                    val updatedMatches = if (playerToAdd != null) {
                        t.matches.map { match ->
                            if (match.status == MatchStatus.LIVE || match.status == MatchStatus.UPCOMING) {
                                val updatedTeamA = if (match.teamA.id == teamId || playerToAdd.isJoker) {
                                    match.teamA.copy(players = match.teamA.players + playerToAdd)
                                } else match.teamA

                                val updatedTeamB = if (match.teamB.id == teamId || playerToAdd.isJoker) {
                                    match.teamB.copy(players = match.teamB.players + playerToAdd)
                                } else match.teamB

                                match.copy(teamA = updatedTeamA, teamB = updatedTeamB)
                            } else match
                        }
                    } else t.matches

                    t.copy(teams = updatedTeams, matches = updatedMatches)
                } else t
            }
            saveToDisk(newList)
            newList
        }
        return added
    }

    fun deletePlayer(tournamentId: String, teamId: String, playerId: String) {
        _tournaments.update { list ->
            val newList = list.map { t ->
                if (t.id == tournamentId) {
                    val updatedTeams = t.teams.map { team ->
                        if (team.id == teamId) {
                            team.copy(players = team.players.filter { it.id != playerId })
                        } else team
                    }

                    val updatedMatches = t.matches.map { match ->
                        if (match.status == MatchStatus.LIVE || match.status == MatchStatus.UPCOMING) {
                            val updatedTeamA = match.teamA.copy(players = match.teamA.players.filter { it.id != playerId })
                            val updatedTeamB = match.teamB.copy(players = match.teamB.players.filter { it.id != playerId })
                            match.copy(teamA = updatedTeamA, teamB = updatedTeamB)
                        } else match
                    }

                    t.copy(teams = updatedTeams, matches = updatedMatches)
                } else t
            }
            saveToDisk(newList)
            newList
        }
    }

    fun updatePlayerDetails(tournamentId: String, teamId: String, playerId: String, newName: String, bStyle: BattingStyle, bowlStyle: BowlingStyle, isCaptain: Boolean, isViceCaptain: Boolean) {
        _tournaments.update { list ->
            val newList = list.map { t ->
                if (t.id == tournamentId) {
                    val updatedTeams = t.teams.map { team ->
                        if (team.id == teamId) {
                            val updatedPlayers = team.players.map { player ->
                                if (player.id == playerId) player.copy(name = newName, battingStyle = bStyle, bowlingStyle = bowlStyle, isCaptain = isCaptain, isViceCaptain = isViceCaptain) else player
                            }
                            team.copy(players = updatedPlayers)
                        } else team
                    }
                    
                    val updatedMatches = t.matches.map { match ->
                        if (match.status == MatchStatus.LIVE || match.status == MatchStatus.UPCOMING) {
                            val updatedTeamA = if (match.teamA.id == teamId || match.teamA.players.any { it.id == playerId }) {
                                match.teamA.copy(players = match.teamA.players.map { p ->
                                    if (p.id == playerId) p.copy(name = newName, battingStyle = bStyle, bowlingStyle = bowlStyle, isCaptain = isCaptain, isViceCaptain = isViceCaptain) else p
                                })
                            } else match.teamA
                            
                            val updatedTeamB = if (match.teamB.id == teamId || match.teamB.players.any { it.id == playerId }) {
                                match.teamB.copy(players = match.teamB.players.map { p ->
                                    if (p.id == playerId) p.copy(name = newName, battingStyle = bStyle, bowlingStyle = bowlStyle, isCaptain = isCaptain, isViceCaptain = isViceCaptain) else p
                                })
                            } else match.teamB
                            
                            match.copy(teamA = updatedTeamA, teamB = updatedTeamB)
                        } else match
                    }
                    
                    t.copy(teams = updatedTeams, matches = updatedMatches)
                } else t
            }
            saveToDisk(newList)
            newList
        }
    }

    fun togglePlayerJokerStatus(tournamentId: String, teamId: String, playerId: String) {
        _tournaments.update { list ->
            val newList = list.map { t ->
                if (t.id == tournamentId) {
                    val updatedTeams = t.teams.map { team ->
                        if (team.id == teamId) {
                            val updatedPlayers = team.players.map { player ->
                                if (player.id == playerId) player.copy(isJoker = !player.isJoker) else player
                            }
                            team.copy(players = updatedPlayers)
                        } else team
                    }

                    val jokerPlayer = updatedTeams.flatMap { it.players }.find { it.id == playerId } ?: return@map t

                    val updatedMatches = t.matches.map { match ->
                        if (match.status == MatchStatus.LIVE || match.status == MatchStatus.UPCOMING) {
                            var newTeamA = match.teamA
                            var newTeamB = match.teamB

                            if (jokerPlayer.isJoker) {
                                // Add Joker to both teams if not already there
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
                                // Remove Joker from teams they don't belong to originally
                                // (i.e., not in the updatedTeams master list for that team)
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

                    t.copy(teams = updatedTeams, matches = updatedMatches)
                } else t
            }
            saveToDisk(newList)
            newList
        }
    }

    fun scheduleMatch(
        tournamentId: String, 
        teamAId: String, 
        teamBId: String, 
        scheduledDate: Long? = null
    ) {
        _tournaments.update { list ->
            val newList = list.map { t ->
                if (t.id == tournamentId) {
                    val teamA = t.teams.find { it.id == teamAId } ?: return@map t
                    val teamB = t.teams.find { it.id == teamBId } ?: return@map t
                    
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
                    t.copy(matches = t.matches + match)
                } else t
            }
            saveToDisk(newList)
            newList
        }
    }

    fun deleteMatch(tournamentId: String, matchId: String) {
        _tournaments.update { list ->
            val newList = list.map { t ->
                if (t.id == tournamentId) {
                    val filteredMatches = t.matches.filter { it.id != matchId }
                    recalculateTournamentStandings(t.copy(matches = filteredMatches))
                } else t
            }
            saveToDisk(newList)
            newList
        }
    }

    fun updateMatch(tournamentId: String, updatedMatch: Match) {
        _tournaments.update { list ->
            list.map { t ->
                if (t.id == tournamentId) {
                    t.copy(matches = t.matches.map { if (it.id == updatedMatch.id) updatedMatch else it })
                } else t
            }.also { saveToDisk(it) }
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
        val resetTeams = tournament.teams.map { resetTeamStats(it) }
        var currentTeams = resetTeams
        tournament.matches.filter { it.status == MatchStatus.COMPLETED }.forEach { match ->
            currentTeams = updateTournamentPointsAndStats(currentTeams, match)
        }
        return tournament.copy(teams = currentTeams)
    }

    private fun updateTournamentPointsAndStats(teams: List<Team>, match: Match): List<Team> {
        return teams.map { team ->
            if (team.id == match.teamA.id || team.id == match.teamB.id) {
                val matchTeam = if (team.id == match.teamA.id) match.teamA else match.teamB
                
                val won = match.winnerId == team.id
                val lost = match.winnerId != null && match.winnerId != team.id
                val draw = match.status == MatchStatus.COMPLETED && match.winnerId == null
                
                val updatedPlayers = team.players.map { tp ->
                    val mp = matchTeam.players.find { it.id == tp.id }
                    if (mp != null) {
                        tp.copy(
                            battingStats = tp.battingStats.copy(
                                runs = tp.battingStats.runs + mp.battingStats.runs,
                                balls = tp.battingStats.balls + mp.battingStats.balls,
                                fours = tp.battingStats.fours + mp.battingStats.fours,
                                sixes = tp.battingStats.sixes + mp.battingStats.sixes
                            ),
                            bowlingStats = tp.bowlingStats.copy(
                                wickets = tp.bowlingStats.wickets + mp.bowlingStats.wickets,
                                runsConceded = tp.bowlingStats.runsConceded + mp.bowlingStats.runsConceded,
                                balls = tp.bowlingStats.balls + mp.bowlingStats.balls,
                                overs = tp.bowlingStats.overs + mp.bowlingStats.overs
                            ),
                            fieldingStats = tp.fieldingStats.copy(
                                catches = tp.fieldingStats.catches + mp.fieldingStats.catches,
                                runOuts = tp.fieldingStats.runOuts + mp.fieldingStats.runOuts,
                                stumpings = tp.fieldingStats.stumpings + mp.fieldingStats.stumpings
                            )
                        )
                    } else tp
                }

                team.copy(
                    players = updatedPlayers,
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
        _tournaments.update { list ->
            val newList = list.map { t ->
                if (t.id == tournamentId) {
                    t.copy(settings = t.settings.copy(
                        overs = overs, 
                        maxOversPerBowler = maxOvers,
                        quotaBowlersCount = quotaCount,
                        quotaMaxOvers = quotaLimit
                    ))
                } else t
            }
            saveToDisk(newList)
            newList
        }
    }
}
