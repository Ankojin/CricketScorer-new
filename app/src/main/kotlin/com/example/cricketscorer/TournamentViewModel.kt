package com.example.cricketscorer

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

class TournamentViewModel : ViewModel() {
    val tournaments: StateFlow<List<Tournament>> = TournamentRepository.tournaments

    fun createTournament(name: String, overs: Int, maxOvers: Int? = null, quotaCount: Int? = null, quotaLimit: Int? = null) {
        TournamentRepository.createTournament(name, overs, maxOvers, quotaCount, quotaLimit)
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

    fun addPlayer(context: android.content.Context, tournamentId: String, teamId: String, name: String, bStyle: BattingStyle, bowlStyle: BowlingStyle) {
        val success = TournamentRepository.addPlayerToTeam(tournamentId, teamId, name, bStyle, bowlStyle)
        if (!success) {
            android.widget.Toast.makeText(context, "Player $name already exists in this tournament! 👤❌", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun deletePlayer(tournamentId: String, teamId: String, playerId: String) {
        TournamentRepository.deletePlayer(tournamentId, teamId, playerId)
    }

    fun updatePlayerDetails(tournamentId: String, teamId: String, playerId: String, newName: String, bStyle: BattingStyle, bowlStyle: BowlingStyle) {
        TournamentRepository.updatePlayerDetails(tournamentId, teamId, playerId, newName, bStyle, bowlStyle)
    }

    fun togglePlayerJokerStatus(tournamentId: String, teamId: String, playerId: String) {
        TournamentRepository.togglePlayerJokerStatus(tournamentId, teamId, playerId)
    }

    fun scheduleMatch(
        tournamentId: String, 
        teamAId: String, 
        teamBId: String, 
        scheduledDate: Long? = null,
        overs: Int? = null,
        maxOvers: Int? = null,
        quotaCount: Int? = null,
        quotaLimit: Int? = null
    ) {
        TournamentRepository.scheduleMatch(tournamentId, teamAId, teamBId, scheduledDate, overs, maxOvers, quotaCount, quotaLimit)
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

    fun syncTournament(context: android.content.Context, tournamentId: String) {
        val tournament = TournamentRepository.getTournament(tournamentId)
        if (tournament != null) {
            NearbyManager.broadcastTournament(context, tournament)
        }
    }
}
