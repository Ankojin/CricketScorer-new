package com.example.cricketscorer

import android.widget.Toast
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

    fun addPlayer(context: android.content.Context, tournamentId: String, teamId: String, name: String, bStyle: BattingStyle, isCaptain: Boolean = false, isViceCaptain: Boolean = false) {
        // v2.27.0: Automatically save to global playlist 🏏🚀⚖️🏅
        GlobalPlayerRepository.addPlayer(name, bStyle)
        
        val success = TournamentRepository.addPlayerToTeam(tournamentId, teamId, name, bStyle, isCaptain, isViceCaptain)
        if (!success) {
            Toast.makeText(context, "Player $name already exists in this team! 👤❌", Toast.LENGTH_SHORT).show()
        }
    }

    fun addGlobalPlayer(tournamentId: String, teamId: String, player: Player) {
        TournamentRepository.addPlayerToTeam(tournamentId, teamId, player.name, player.battingStyle ?: BattingStyle.RHB)
    }

    fun addGlobalPlayers(tournamentId: String, teamId: String, players: List<Player>) {
        TournamentRepository.addPlayersToTeam(tournamentId, teamId, players)
    }

    fun deletePlayer(tournamentId: String, teamId: String, playerId: String) {
        TournamentRepository.deletePlayer(tournamentId, teamId, playerId)
    }

    fun updatePlayerDetails(tournamentId: String, teamId: String, playerId: String, newName: String, bStyle: BattingStyle, isCaptain: Boolean, isViceCaptain: Boolean) {
        TournamentRepository.updatePlayerDetails(tournamentId, teamId, playerId, newName, bStyle, isCaptain, isViceCaptain)
    }

    fun togglePlayerJokerStatus(tournamentId: String, teamId: String, playerId: String) {
        TournamentRepository.togglePlayerJokerStatus(tournamentId, teamId, playerId)
    }

    fun scheduleMatch(
        tournamentId: String, 
        teamAId: String, 
        teamBId: String, 
        scheduledDate: Long? = null
    ) {
        TournamentRepository.scheduleMatch(tournamentId, teamAId, teamBId, scheduledDate)
    }

    fun deleteMatch(tournamentId: String, matchId: String, scoringViewModel: ScoringViewModel? = null) {
        TournamentRepository.deleteMatch(tournamentId, matchId)
        scoringViewModel?.clearIfDeleted(matchId)
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
