package com.example.cricketscorer

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

class TournamentViewModel : ViewModel() {
    val tournaments: StateFlow<List<Tournament>> = TournamentRepository.tournaments

    fun createTournament(name: String, overs: Int) {
        TournamentRepository.createTournament(name, overs)
    }

    fun deleteTournament(id: String) {
        TournamentRepository.deleteTournament(id)
    }

    fun addTeam(tournamentId: String, name: String) {
        TournamentRepository.addTeamToTournament(tournamentId, name)
    }

    fun deleteTeam(tournamentId: String, teamId: String) {
        TournamentRepository.deleteTeam(tournamentId, teamId)
    }

    fun addPlayer(tournamentId: String, teamId: String, name: String) {
        TournamentRepository.addPlayerToTeam(tournamentId, teamId, name)
    }

    fun deletePlayer(tournamentId: String, teamId: String, playerId: String) {
        TournamentRepository.deletePlayer(tournamentId, teamId, playerId)
    }

    fun updatePlayerName(tournamentId: String, teamId: String, playerId: String, newName: String) {
        TournamentRepository.updatePlayerName(tournamentId, teamId, playerId, newName)
    }

    fun scheduleMatch(tournamentId: String, teamAId: String, teamBId: String) {
        TournamentRepository.scheduleMatch(tournamentId, teamAId, teamBId)
    }

    fun deleteMatch(tournamentId: String, matchId: String) {
        TournamentRepository.deleteMatch(tournamentId, matchId)
    }

    fun exportTournament(id: String): String? {
        return TournamentRepository.exportTournament(id)
    }

    fun importTournament(json: String): Boolean {
        return TournamentRepository.importTournament(json)
    }
}
