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
            _tournaments.update { list ->
                val newList = list.filter { it.id != imported.id } + imported
                saveToDisk(newList)
                newList
            }
            true
        } catch (e: Exception) {
            Log.e("TournamentRepository", "Failed to import", e)
            false
        }
    }

    fun createTournament(name: String, overs: Int) {
        _tournaments.update { list ->
            val newList = list + Tournament(
                id = UUID.randomUUID().toString(),
                name = name,
                settings = TournamentSettings(overs = overs)
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

    fun addPlayerToTeam(tournamentId: String, teamId: String, playerName: String) {
        _tournaments.update { list ->
            val newList = list.map { t ->
                if (t.id == tournamentId) {
                    var createdPlayer: Player? = null
                    val updatedTeams = t.teams.map { team ->
                        if (team.id == teamId) {
                            val newPlayer = Player(id = UUID.randomUUID().toString(), name = playerName)
                            createdPlayer = newPlayer
                            team.copy(players = team.players + newPlayer)
                        } else team
                    }

                    val playerToAdd = createdPlayer
                    val updatedMatches = if (playerToAdd != null) {
                        t.matches.map { match ->
                            if (match.status == MatchStatus.LIVE || match.status == MatchStatus.UPCOMING) {
                                val updatedTeamA = if (match.teamA.id == teamId) {
                                    match.teamA.copy(players = match.teamA.players + playerToAdd)
                                } else match.teamA

                                val updatedTeamB = if (match.teamB.id == teamId) {
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
                            val updatedTeamA = if (match.teamA.id == teamId) {
                                match.teamA.copy(players = match.teamA.players.filter { it.id != playerId })
                            } else match.teamA

                            val updatedTeamB = if (match.teamB.id == teamId) {
                                match.teamB.copy(players = match.teamB.players.filter { it.id != playerId })
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

    fun updatePlayerName(tournamentId: String, teamId: String, playerId: String, newName: String) {
        _tournaments.update { list ->
            val newList = list.map { t ->
                if (t.id == tournamentId) {
                    val updatedTeams = t.teams.map { team ->
                        if (team.id == teamId) {
                            val updatedPlayers = team.players.map { player ->
                                if (player.id == playerId) player.copy(name = newName) else player
                            }
                            team.copy(players = updatedPlayers)
                        } else team
                    }
                    
                    val updatedMatches = t.matches.map { match ->
                        if (match.status == MatchStatus.LIVE || match.status == MatchStatus.UPCOMING) {
                            val updatedTeamA = if (match.teamA.id == teamId) {
                                match.teamA.copy(players = match.teamA.players.map { p ->
                                    if (p.id == playerId) p.copy(name = newName) else p
                                })
                            } else match.teamA
                            
                            val updatedTeamB = if (match.teamB.id == teamId) {
                                match.teamB.copy(players = match.teamB.players.map { p ->
                                    if (p.id == playerId) p.copy(name = newName) else p
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

    fun scheduleMatch(tournamentId: String, teamAId: String, teamBId: String) {
        _tournaments.update { list ->
            val newList = list.map { t ->
                if (t.id == tournamentId) {
                    val teamA = t.teams.find { it.id == teamAId } ?: return@map t
                    val teamB = t.teams.find { it.id == teamBId } ?: return@map t
                    val match = Match(
                        id = UUID.randomUUID().toString(),
                        tournamentId = tournamentId,
                        tournamentName = t.name,
                        teamA = teamA,
                        teamB = teamB,
                        battingTeamId = teamA.id,
                        bowlingTeamId = teamB.id,
                        oversPerInnings = t.settings.overs,
                        dateMillis = System.currentTimeMillis()
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
                    t.copy(matches = t.matches.filter { it.id != matchId })
                } else t
            }
            saveToDisk(newList)
            newList
        }
    }

    fun updateMatch(tournamentId: String, updatedMatch: Match) {
        _tournaments.update { list ->
            var found = false
            val newList = list.map { t ->
                if (t.id == tournamentId) {
                    found = true
                    val oldMatch = t.matches.find { it.id == updatedMatch.id }
                    val wasCompleted = oldMatch?.status == MatchStatus.COMPLETED
                    
                    val updatedMatches = if (t.matches.any { it.id == updatedMatch.id }) {
                        t.matches.map { m -> if (m.id == updatedMatch.id) updatedMatch else m }
                    } else {
                        t.matches + updatedMatch
                    }
                    
                    var updatedTeams = t.teams
                    if (updatedMatch.status == MatchStatus.COMPLETED && !wasCompleted) {
                        updatedTeams = updateTournamentPointsAndStats(t.teams, updatedMatch)
                    }
                    
                    t.copy(matches = updatedMatches, teams = updatedTeams)
                } else t
            }
            
            val finalList = if (!found && tournamentId.isNotEmpty()) {
                val newTournament = Tournament(
                    id = tournamentId,
                    name = updatedMatch.tournamentName ?: "Remote Tournament",
                    teams = listOf(updatedMatch.teamA, updatedMatch.teamB),
                    matches = listOf(updatedMatch),
                    settings = TournamentSettings(overs = updatedMatch.oversPerInnings)
                )
                newList + newTournament
            } else {
                newList
            }
            
            saveToDisk(finalList)
            finalList
        }
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
}
