package com.example.cricketscorer

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ScoringViewModel : ViewModel() {

    private val _matchState = MutableStateFlow<Match?>(null)
    val matchState: StateFlow<Match?> = _matchState.asStateFlow()

    fun loadMatch(match: Match) {
        _matchState.value = match
        if (match.tossWinnerId == null && match.status != MatchStatus.COMPLETED) {
            _matchState.update { it?.copy(pendingAction = PendingAction.TOSS_REQUIRED) }
        }
    }

    fun handleToss(winnerId: String, decision: String) {
        _matchState.update { current ->
            if (current == null) return@update null
            
            val battingTeamId = if ((winnerId == current.teamA.id && decision == "BAT") || (winnerId == current.teamB.id && decision == "BOWL")) current.teamA.id else current.teamB.id
            val bowlingTeamId = if (battingTeamId == current.teamA.id) current.teamB.id else current.teamA.id
            
            val updatedMatch = current.copy(
                tossWinnerId = winnerId,
                tossDecision = decision,
                initialBattingTeamId = battingTeamId,
                initialBowlingTeamId = bowlingTeamId,
                battingTeamId = battingTeamId,
                bowlingTeamId = bowlingTeamId,
                status = MatchStatus.LIVE,
                pendingAction = PendingAction.SELECT_STRIKER,
                strikerId = null,
                nonStrikerId = null,
                currentBowlerId = null
            )
            TournamentRepository.updateMatch(current.tournamentId ?: "", updatedMatch)
            updatedMatch
        }
    }

    fun handleRuns(runs: Int) {
        val currentMatch = _matchState.value ?: return
        if (currentMatch.pendingAction != PendingAction.NONE) return
        
        val ball = Ball(
            runs = runs,
            strikerId = currentMatch.strikerId ?: return,
            nonStrikerId = currentMatch.nonStrikerId ?: return,
            bowlerId = currentMatch.currentBowlerId ?: return
        )
        recordBall(ball)
    }

    fun handleExtra(type: ExtrasType) {
        val currentMatch = _matchState.value ?: return
        if (currentMatch.pendingAction != PendingAction.NONE) return
        
        val isLegal = type == ExtrasType.BYE || type == ExtrasType.LEG_BYE
        val ball = Ball(
            runs = 0,
            extrasType = type,
            extraRuns = 1,
            strikerId = currentMatch.strikerId ?: return,
            nonStrikerId = currentMatch.nonStrikerId ?: return,
            bowlerId = currentMatch.currentBowlerId ?: return,
            isLegalBall = isLegal
        )
        recordBall(ball)
    }

    fun handleWicket(type: WicketType = WicketType.BOWLED) {
        val currentMatch = _matchState.value ?: return
        if (currentMatch.pendingAction != PendingAction.NONE) return
        
        val ball = Ball(
            runs = 0,
            wicketType = type,
            strikerId = currentMatch.strikerId ?: return,
            nonStrikerId = currentMatch.nonStrikerId ?: return,
            bowlerId = currentMatch.currentBowlerId ?: return
        )
        recordBall(ball)
    }

    fun selectNewPlayer(playerId: String) {
        _matchState.update { current ->
            if (current == null) return@update null
            
            val withSelection = when (current.pendingAction) {
                PendingAction.SELECT_STRIKER -> current.copy(strikerId = playerId)
                PendingAction.SELECT_NON_STRIKER -> current.copy(nonStrikerId = playerId)
                PendingAction.SELECT_BOWLER -> current.copy(currentBowlerId = playerId, lastBowlerId = null)
                else -> current
            }
            
            val finalizedMatch = recalculateMatchFromHistory(withSelection)
            TournamentRepository.updateMatch(finalizedMatch.tournamentId ?: "", finalizedMatch)
            finalizedMatch
        }
    }

    private fun recordBall(ball: Ball) {
        _matchState.update { current ->
            if (current == null) return@update null
            val updatedMatch = current.copy(ballHistory = current.ballHistory + ball)
            val finalizedMatch = recalculateMatchFromHistory(updatedMatch)
            TournamentRepository.updateMatch(finalizedMatch.tournamentId ?: "", finalizedMatch)
            finalizedMatch
        }
    }

    private fun recalculateMatchFromHistory(match: Match): Match {
        if (match.tossWinnerId == null) {
            return match.copy(pendingAction = PendingAction.TOSS_REQUIRED)
        }

        var current = match.copy(
            totalRuns = 0, totalWickets = 0, totalBalls = 0,
            wideCount = 0, noBallCount = 0, byeCount = 0, legByeCount = 0,
            wicketHistory = emptyList(),
            teamA = resetTeamStats(match.teamA),
            teamB = resetTeamStats(match.teamB),
            status = MatchStatus.LIVE,
            currentInnings = 1,
            battingTeamId = match.initialBattingTeamId ?: match.battingTeamId,
            bowlingTeamId = match.initialBowlingTeamId ?: match.bowlingTeamId,
            innings1Data = null, target = null, winnerId = null,
            strikerId = null, nonStrikerId = null, currentBowlerId = null, lastBowlerId = null,
            pendingAction = PendingAction.NONE
        )

        match.ballHistory.forEach { ball ->
            if (current.status == MatchStatus.COMPLETED) return@forEach

            // Update extras
            var w = current.wideCount; var nb = current.noBallCount; var b = current.byeCount; var lb = current.legByeCount
            when(ball.extrasType) {
                ExtrasType.WIDE -> w++
                ExtrasType.NO_BALL -> nb++
                ExtrasType.BYE -> b++
                ExtrasType.LEG_BYE -> lb++
                else -> {}
            }

            current = current.copy(
                totalRuns = current.totalRuns + ball.runs + ball.extraRuns,
                totalWickets = current.totalWickets + (if (ball.wicketType != WicketType.NONE) 1 else 0),
                totalBalls = current.totalBalls + (if (ball.isLegalBall) 1 else 0),
                wideCount = w, noBallCount = nb, byeCount = b, legByeCount = lb,
                teamA = updateTeamStats(current.teamA, ball, current.battingTeamId == current.teamA.id, current.bowlingTeamId == current.teamA.id),
                teamB = updateTeamStats(current.teamB, ball, current.battingTeamId == current.teamB.id, current.bowlingTeamId == current.teamB.id)
            )

            if (ball.wicketType != WicketType.NONE) {
                val battingTeam = if (current.battingTeamId == current.teamA.id) current.teamA else current.teamB
                val outName = battingTeam.players.find { it.id == ball.strikerId }?.name ?: "Unknown"
                current = current.copy(wicketHistory = current.wicketHistory + WicketRecord(current.totalWickets, outName, current.totalRuns, "${current.totalBalls/6}.${current.totalBalls%6}"))
            }

            var sId: String? = ball.strikerId
            var nsId: String? = ball.nonStrikerId
            var bId: String? = ball.bowlerId
            var lbId: String? = current.lastBowlerId

            if (ball.runs % 2 != 0) { val t = sId; sId = nsId; nsId = t }
            if (ball.isLegalBall && current.totalBalls % 6 == 0) {
                val t = sId; sId = nsId; nsId = t; lbId = bId; bId = null
            }
            if (ball.wicketType != WicketType.NONE && ball.wicketType != WicketType.RUN_OUT) sId = null

            current = current.copy(strikerId = sId, nonStrikerId = nsId, currentBowlerId = bId, lastBowlerId = lbId)

            val inningsEnded = current.totalWickets >= 10 || current.totalBalls >= current.oversPerInnings * 6
            if (current.currentInnings == 1 && inningsEnded) {
                current = current.copy(
                    innings1Data = InningsSummary(current.totalRuns, current.totalWickets, current.totalBalls, current.battingTeamId, current.wicketHistory, current.wideCount, current.noBallCount, current.byeCount, current.legByeCount, match.ballHistory.indexOf(ball) + 1),
                    currentInnings = 2,
                    target = current.totalRuns + 1,
                    battingTeamId = current.bowlingTeamId,
                    bowlingTeamId = current.battingTeamId,
                    totalRuns = 0, totalWickets = 0, totalBalls = 0,
                    wideCount = 0, noBallCount = 0, byeCount = 0, legByeCount = 0,
                    wicketHistory = emptyList(), strikerId = null, nonStrikerId = null, currentBowlerId = null, lastBowlerId = null
                )
            } else if (current.currentInnings == 2) {
                if (current.totalRuns >= (current.target ?: 0)) {
                    current = current.copy(status = MatchStatus.COMPLETED, winnerId = current.battingTeamId)
                } else if (inningsEnded) {
                    current = current.copy(status = MatchStatus.COMPLETED, winnerId = if (current.totalRuns < (current.target ?: 0) - 1) current.bowlingTeamId else null)
                }
            }
        }

        var final = current
        if (final.status != MatchStatus.COMPLETED) {
            if (final.strikerId == null && isAvailable(final, match.strikerId)) final = final.copy(strikerId = match.strikerId)
            if (final.nonStrikerId == null && isAvailable(final, match.nonStrikerId)) final = final.copy(nonStrikerId = match.nonStrikerId)
            if (final.currentBowlerId == null && canBowl(final, match.currentBowlerId)) final = final.copy(currentBowlerId = match.currentBowlerId)

            final = when {
                final.strikerId == null -> final.copy(pendingAction = PendingAction.SELECT_STRIKER)
                final.nonStrikerId == null -> final.copy(pendingAction = PendingAction.SELECT_NON_STRIKER)
                final.currentBowlerId == null -> final.copy(pendingAction = PendingAction.SELECT_BOWLER)
                else -> final.copy(pendingAction = PendingAction.NONE)
            }
        }
        return final
    }

    private fun isAvailable(m: Match, pId: String?): Boolean {
        if (pId == null) return false
        val team = if (m.battingTeamId == m.teamA.id) m.teamA else m.teamB
        val player = team.players.find { it.id == pId } ?: return false
        return !player.battingStats.isOut && pId != m.strikerId && pId != m.nonStrikerId
    }

    private fun canBowl(m: Match, pId: String?): Boolean {
        if (pId == null) return false
        val team = if (m.bowlingTeamId == m.teamA.id) m.teamA else m.teamB
        return team.players.any { it.id == pId } && pId != m.lastBowlerId
    }

    private fun resetTeamStats(team: Team) = team.copy(players = team.players.map { it.copy(battingStats = BattingStats(), bowlingStats = BowlingStats()) })

    private fun updateTeamStats(team: Team, ball: Ball, isBat: Boolean, isBowl: Boolean): Team {
        return team.copy(players = team.players.map { p ->
            var np = p
            if (isBat && p.id == ball.strikerId) np = np.copy(battingStats = p.battingStats.copy(runs = p.battingStats.runs + ball.runs, balls = p.battingStats.balls + (if (ball.isLegalBall) 1 else 0), fours = p.battingStats.fours + (if (ball.runs == 4) 1 else 0), sixes = p.battingStats.sixes + (if (ball.runs == 6) 1 else 0), isOut = p.battingStats.isOut || ball.wicketType != WicketType.NONE, dismissalBowlerId = if (ball.wicketType != WicketType.NONE) ball.bowlerId else p.battingStats.dismissalBowlerId))
            if (isBowl && p.id == ball.bowlerId) {
                var nb = p.bowlingStats.balls; var no = p.bowlingStats.overs
                if (ball.isLegalBall) { nb++; if (nb == 6) { no++; nb = 0 } }
                np = np.copy(bowlingStats = p.bowlingStats.copy(
                    runsConceded = p.bowlingStats.runsConceded + ball.runs + ball.extraRuns, 
                    balls = nb, overs = no, 
                    wickets = p.bowlingStats.wickets + (if (ball.wicketType != WicketType.NONE && ball.wicketType != WicketType.RUN_OUT) 1 else 0),
                    wides = p.bowlingStats.wides + (if (ball.extrasType == ExtrasType.WIDE) 1 else 0),
                    noBalls = p.bowlingStats.noBalls + (if (ball.extrasType == ExtrasType.NO_BALL) 1 else 0)
                ))
            }
            np
        })
    }

    fun undo() {
        _matchState.update { current ->
            if (current == null || current.ballHistory.isEmpty()) return@update current
            val undone = current.copy(ballHistory = current.ballHistory.dropLast(1), strikerId = null, nonStrikerId = null, currentBowlerId = null)
            val finalized = recalculateMatchFromHistory(undone)
            TournamentRepository.updateMatch(finalized.tournamentId ?: "", finalized)
            finalized
        }
    }
}
