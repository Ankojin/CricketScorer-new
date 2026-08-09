package com.example.cricketscorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ScoringViewModel : ViewModel() {

    private val _matchState = MutableStateFlow<Match?>(null)
    val matchState: StateFlow<Match?> = _matchState.asStateFlow()

    private val _isDarkMode = MutableStateFlow<Boolean?>(null) // null means follow system
    val isDarkMode: StateFlow<Boolean?> = _isDarkMode.asStateFlow()

    private var pendingWicketBall: Ball? = null

    init {
        viewModelScope.launch {
            TournamentRepository.tournaments.collect { tournaments ->
                val current = _matchState.value ?: return@collect
                val tournamentId = current.tournamentId ?: return@collect
                val tournament = tournaments.find { it.id == tournamentId } ?: return@collect
                val matchInRepo = tournament.matches.find { it.id == current.id } ?: return@collect
                
                // Sync player names if they changed in the repository
                if (current.teamA.players != matchInRepo.teamA.players || 
                    current.teamB.players != matchInRepo.teamB.players) {
                    
                    _matchState.update { m ->
                        m?.copy(
                            teamA = m.teamA.copy(players = matchInRepo.teamA.players),
                            teamB = m.teamB.copy(players = matchInRepo.teamB.players)
                        )
                    }
                    // Trigger a recalculation to update names in wicket history etc.
                    _matchState.update { m -> if (m != null) recalculateMatchFromHistory(m) else null }
                }
            }
        }
    }

    fun toggleTheme(dark: Boolean?) {
        _isDarkMode.value = dark
    }

    fun startSecondInnings() {
        _matchState.update { it?.copy(pendingAction = PendingAction.NONE) }
    }

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
                pendingAction = PendingAction.SELECT_CAPTAIN_A,
                strikerId = null,
                nonStrikerId = null,
                currentBowlerId = null
            )
            TournamentRepository.updateMatch(current.tournamentId ?: "", updatedMatch)
            updatedMatch
        }
    }

    fun handleRuns(runs: Int, rotateStrike: Boolean = true) {
        val currentMatch = _matchState.value ?: return
        if (currentMatch.pendingAction != PendingAction.NONE) return
        
        val ball = Ball(
            runs = runs,
            rotateStrike = rotateStrike,
            strikerId = currentMatch.strikerId ?: return,
            nonStrikerId = currentMatch.nonStrikerId ?: return,
            bowlerId = currentMatch.currentBowlerId ?: return
        )
        recordBall(ball)
    }

    fun handleExtra(type: ExtrasType, additionalRuns: Int = 0) {
        val currentMatch = _matchState.value ?: return
        if (currentMatch.pendingAction != PendingAction.NONE) return
        
        val ball = when (type) {
            ExtrasType.NO_BALL -> Ball(
                runs = additionalRuns,
                extrasType = type,
                extraRuns = 1,
                strikerId = currentMatch.strikerId ?: return,
                nonStrikerId = currentMatch.nonStrikerId ?: return,
                bowlerId = currentMatch.currentBowlerId ?: return,
                isLegalBall = false
            )
            ExtrasType.WIDE -> Ball(
                runs = 0,
                extrasType = type,
                extraRuns = 1,
                strikerId = currentMatch.strikerId ?: return,
                nonStrikerId = currentMatch.nonStrikerId ?: return,
                bowlerId = currentMatch.currentBowlerId ?: return,
                isLegalBall = false
            )
            else -> {
                val isLegal = type == ExtrasType.BYE || type == ExtrasType.LEG_BYE
                Ball(
                    runs = 0,
                    extrasType = type,
                    extraRuns = additionalRuns,
                    strikerId = currentMatch.strikerId ?: return,
                    nonStrikerId = currentMatch.nonStrikerId ?: return,
                    bowlerId = currentMatch.currentBowlerId ?: return,
                    isLegalBall = isLegal
                )
            }
        }
        recordBall(ball)
    }

    fun handleWicket(type: WicketType = WicketType.BOWLED, outPlayerId: String? = null) {
        val currentMatch = _matchState.value ?: return
        if (currentMatch.pendingAction != PendingAction.NONE) return
        
        val finalOutPlayerId = outPlayerId ?: currentMatch.strikerId ?: return
        
        val ball = Ball(
            runs = 0,
            wicketType = type,
            strikerId = currentMatch.strikerId ?: return,
            nonStrikerId = currentMatch.nonStrikerId ?: return,
            bowlerId = currentMatch.currentBowlerId ?: return,
            outPlayerId = finalOutPlayerId
        )

        val needsFielder = type == WicketType.CAUGHT || type == WicketType.RUN_OUT || type == WicketType.STUMPED
        if (needsFielder) {
            pendingWicketBall = ball
            _matchState.update { it?.copy(pendingAction = PendingAction.SELECT_FIELDER) }
        } else {
            recordBall(ball)
        }
    }

    fun selectFielder(fielderId: String) {
        val ball = pendingWicketBall ?: return
        val updatedBall = ball.copy(fielderId = fielderId)
        pendingWicketBall = null
        recordBall(updatedBall)
    }

    fun editBall(index: Int, updatedBall: Ball) {
        _matchState.update { current ->
            if (current == null || index < 0 || index >= current.ballHistory.size) return@update current
            val newHistory = current.ballHistory.toMutableList()
            newHistory[index] = updatedBall
            val updatedMatch = current.copy(ballHistory = newHistory)
            val finalizedMatch = recalculateMatchFromHistory(updatedMatch)
            TournamentRepository.updateMatch(finalizedMatch.tournamentId ?: "", finalizedMatch)
            finalizedMatch
        }
    }

    fun selectNewPlayer(playerId: String) {
        _matchState.update { current ->
            if (current == null) return@update null
            
            val withSelection = when (current.pendingAction) {
                PendingAction.SELECT_STRIKER -> current.copy(strikerId = playerId)
                PendingAction.SELECT_NON_STRIKER -> current.copy(nonStrikerId = playerId)
                PendingAction.SELECT_BOWLER -> {
                    var nextState = current.copy(currentBowlerId = playerId)
                    val bowlingTeamId = current.bowlingTeamId
                    val isWK = if (bowlingTeamId == current.teamA.id) current.teamAWicketKeeperId == playerId else current.teamBWicketKeeperId == playerId
                    if (isWK) {
                        nextState = nextState.copy(pendingAction = if (bowlingTeamId == current.teamA.id) PendingAction.SELECT_WK_A else PendingAction.SELECT_WK_B)
                    }
                    nextState
                }
                PendingAction.SELECT_CAPTAIN_A -> current.copy(teamACaptainId = playerId, pendingAction = PendingAction.SELECT_CAPTAIN_B)
                PendingAction.SELECT_CAPTAIN_B -> current.copy(teamBCaptainId = playerId, pendingAction = PendingAction.NONE)
                PendingAction.SELECT_WK_A -> current.copy(teamAWicketKeeperId = playerId, pendingAction = PendingAction.NONE)
                PendingAction.SELECT_WK_B -> current.copy(teamBWicketKeeperId = playerId, pendingAction = PendingAction.NONE)
                else -> current
            }
            
            val finalizedMatch = recalculateMatchFromHistory(withSelection)
            TournamentRepository.updateMatch(finalizedMatch.tournamentId ?: "", finalizedMatch)
            finalizedMatch
        }
    }

    fun changeWicketKeeper() {
        _matchState.update { current ->
            if (current == null) return@update null
            val action = if (current.bowlingTeamId == current.teamA.id) PendingAction.SELECT_WK_A else PendingAction.SELECT_WK_B
            current.copy(pendingAction = action)
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
            
            val battingTeam = if (current.battingTeamId == current.teamA.id) current.teamA else current.teamB
            val bowlingTeam = if (current.bowlingTeamId == current.teamA.id) current.teamA else current.teamB

            current = current.copy(
                totalRuns = current.totalRuns + ball.runs + ball.extraRuns,
                totalWickets = current.totalWickets + (if (ball.wicketType != WicketType.NONE) 1 else 0),
                totalBalls = current.totalBalls + (if (ball.isLegalBall) 1 else 0),
                wideCount = current.wideCount + (if (ball.extrasType == ExtrasType.WIDE) ball.extraRuns else 0),
                noBallCount = current.noBallCount + (if (ball.extrasType == ExtrasType.NO_BALL) ball.extraRuns else 0),
                byeCount = current.byeCount + (if (ball.extrasType == ExtrasType.BYE) ball.extraRuns else 0),
                legByeCount = current.legByeCount + (if (ball.extrasType == ExtrasType.LEG_BYE) ball.extraRuns else 0),
                teamA = updateTeamStats(current.teamA, ball, current.battingTeamId == current.teamA.id, current.bowlingTeamId == current.teamA.id),
                teamB = updateTeamStats(current.teamB, ball, current.battingTeamId == current.teamB.id, current.bowlingTeamId == current.teamB.id)
            )

            if (ball.wicketType != WicketType.NONE) {
                val outId = ball.outPlayerId ?: ball.strikerId
                val outName = battingTeam.players.find { it.id == outId }?.name ?: "Unknown"
                val bName = bowlingTeam.players.find { it.id == ball.bowlerId }?.name
                val fName = bowlingTeam.players.find { it.id == ball.fielderId }?.name
                current = current.copy(wicketHistory = current.wicketHistory + WicketRecord(current.totalWickets, outName, current.totalRuns, "${current.totalBalls/6}.${current.totalBalls%6}", ball.wicketType, bName, fName))
            }

            var sId: String? = ball.strikerId
            var nsId: String? = ball.nonStrikerId
            var bId: String? = ball.bowlerId
            var lbId: String? = current.lastBowlerId

            if (ball.rotateStrike && ball.runs % 2 != 0) { val t = sId; sId = nsId; nsId = t }
            if (ball.isLegalBall && current.totalBalls % 6 == 0) {
                val t = sId; sId = nsId; nsId = t; lbId = bId; bId = null
            }
            if (ball.wicketType != WicketType.NONE) {
                val outId = ball.outPlayerId ?: ball.strikerId
                if (sId == outId) sId = null
                else if (nsId == outId) nsId = null
            }

            current = current.copy(strikerId = sId, nonStrikerId = nsId, currentBowlerId = bId, lastBowlerId = lbId)

            val maxWickets = (battingTeam.players.size - 1).coerceAtLeast(1)
            val inningsEnded = current.totalWickets >= maxWickets || current.totalBalls >= current.oversPerInnings * 6
            if (current.currentInnings == 1 && inningsEnded) {
                current = current.copy(
                    innings1Data = InningsSummary(
                        runs = current.totalRuns,
                        wickets = current.totalWickets,
                        balls = current.totalBalls,
                        teamId = current.battingTeamId,
                        wicketHistory = current.wicketHistory,
                        wideCount = current.wideCount,
                        noBallCount = current.noBallCount,
                        byeCount = current.byeCount,
                        legByeCount = current.legByeCount,
                        recordedBallsCount = current.ballHistory.indexOf(ball) + 1
                    ),
                    currentInnings = 2,
                    target = current.totalRuns + 1,
                    battingTeamId = current.bowlingTeamId,
                    bowlingTeamId = current.battingTeamId,
                    totalRuns = 0, totalWickets = 0, totalBalls = 0,
                    wicketHistory = emptyList(), strikerId = null, nonStrikerId = null, currentBowlerId = null, lastBowlerId = null,
                    pendingAction = PendingAction.START_SECOND_INNINGS
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
            // Restore manual selections if they are valid
            if (final.strikerId == null && isAvailableForNew(final, match.strikerId)) final = final.copy(strikerId = match.strikerId)
            if (final.nonStrikerId == null && isAvailableForNew(final, match.nonStrikerId)) final = final.copy(nonStrikerId = match.nonStrikerId)
            if (final.currentBowlerId == null && canBowlNew(final, match.currentBowlerId)) final = final.copy(currentBowlerId = match.currentBowlerId)

            // Auto-advance Captain selection
            if (match.teamACaptainId == null) final = final.copy(pendingAction = PendingAction.SELECT_CAPTAIN_A)
            else if (match.teamBCaptainId == null) final = final.copy(pendingAction = PendingAction.SELECT_CAPTAIN_B)
            else {
                val currentWK = if (final.bowlingTeamId == final.teamA.id) final.teamAWicketKeeperId else final.teamBWicketKeeperId
                final = when {
                    currentWK == null -> final.copy(pendingAction = if (final.bowlingTeamId == final.teamA.id) PendingAction.SELECT_WK_A else PendingAction.SELECT_WK_B)
                    final.strikerId == null -> final.copy(pendingAction = PendingAction.SELECT_STRIKER)
                    final.nonStrikerId == null -> final.copy(pendingAction = PendingAction.SELECT_NON_STRIKER)
                    final.currentBowlerId == null -> final.copy(pendingAction = PendingAction.SELECT_BOWLER)
                    else -> final.copy(pendingAction = PendingAction.NONE)
                }
            }
        }
        return final
    }

    private fun isAvailableForNew(m: Match, pId: String?): Boolean {
        if (pId == null) return false
        val team = if (m.battingTeamId == m.teamA.id) m.teamA else m.teamB
        val player = team.players.find { it.id == pId } ?: return false
        return !player.battingStats.isOut && pId != m.strikerId && pId != m.nonStrikerId
    }

    private fun canBowlNew(m: Match, pId: String?): Boolean {
        if (pId == null) return false
        val team = if (m.bowlingTeamId == m.teamA.id) m.teamA else m.teamB
        return team.players.any { it.id == pId } && pId != m.lastBowlerId
    }

    private fun resetTeamStats(team: Team) = team.copy(players = team.players.map { it.copy(battingStats = BattingStats(), bowlingStats = BowlingStats(), fieldingStats = FieldingStats()) })

    private fun updateTeamStats(team: Team, ball: Ball, isBat: Boolean, isBowl: Boolean): Team {
        return team.copy(players = team.players.map { p ->
            var np = p
            if (isBat) {
                val outId = ball.outPlayerId ?: (if (ball.wicketType != WicketType.NONE) ball.strikerId else null)
                val isOut = p.id == outId
                
                if (p.id == ball.strikerId) {
                    np = np.copy(battingStats = p.battingStats.copy(
                        runs = p.battingStats.runs + ball.runs, 
                        balls = p.battingStats.balls + (if (ball.isLegalBall) 1 else 0), 
                        fours = p.battingStats.fours + (if (ball.runs == 4) 1 else 0), 
                        sixes = p.battingStats.sixes + (if (ball.runs == 6) 1 else 0), 
                        isOut = p.battingStats.isOut || isOut,
                        wicketType = if (isOut) ball.wicketType else p.battingStats.wicketType,
                        dismissalBowlerId = if (isOut && ball.wicketType != WicketType.RUN_OUT) ball.bowlerId else p.battingStats.dismissalBowlerId,
                        dismissalFielderId = if (isOut) ball.fielderId else p.battingStats.dismissalFielderId
                    ))
                } else if (p.id == ball.nonStrikerId && isOut) {
                    np = np.copy(battingStats = p.battingStats.copy(
                        isOut = true,
                        wicketType = ball.wicketType,
                        dismissalFielderId = ball.fielderId
                    ))
                }
            }
            if (isBowl && p.id == ball.bowlerId) {
                var nb = p.bowlingStats.balls; var no = p.bowlingStats.overs
                if (ball.isLegalBall) { nb++; if (nb == 6) { no++; nb = 0 } }
                np = np.copy(bowlingStats = p.bowlingStats.copy(
                    runsConceded = p.bowlingStats.runsConceded + ball.runs + ball.extraRuns, 
                    balls = nb, overs = no, 
                    wickets = p.bowlingStats.wickets + (if (ball.wicketType != WicketType.NONE && ball.wicketType != WicketType.RUN_OUT) 1 else 0),
                    dotBalls = p.bowlingStats.dotBalls + (if (ball.runs == 0 && ball.extraRuns == 0) 1 else 0),
                    wides = p.bowlingStats.wides + (if (ball.extrasType == ExtrasType.WIDE) 1 else 0),
                    noBalls = p.bowlingStats.noBalls + (if (ball.extrasType == ExtrasType.NO_BALL) 1 else 0)
                ))
            }
            if (!isBat && p.id == ball.fielderId) {
                np = np.copy(fieldingStats = p.fieldingStats.copy(
                    catches = p.fieldingStats.catches + (if (ball.wicketType == WicketType.CAUGHT) 1 else 0),
                    runOuts = p.fieldingStats.runOuts + (if (ball.wicketType == WicketType.RUN_OUT) 1 else 0),
                    stumpings = p.fieldingStats.stumpings + (if (ball.wicketType == WicketType.STUMPED) 1 else 0)
                ))
            }
            np
        })
    }

    fun updateMatchOvers(newOvers: Int) {
        _matchState.update { current ->
            if (current == null) return@update null
            val updatedMatch = current.copy(oversPerInnings = newOvers)
            val finalizedMatch = recalculateMatchFromHistory(updatedMatch)
            TournamentRepository.updateMatch(finalizedMatch.tournamentId ?: "", finalizedMatch)
            finalizedMatch
        }
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

    fun addNewPlayerToMatch(playerName: String) {
        _matchState.update { current ->
            if (current == null) return@update null

            val teamToAddId = when (current.pendingAction) {
                PendingAction.SELECT_STRIKER, PendingAction.SELECT_NON_STRIKER -> current.battingTeamId
                PendingAction.SELECT_BOWLER, PendingAction.SELECT_FIELDER -> current.bowlingTeamId
                PendingAction.SELECT_CAPTAIN_A, PendingAction.SELECT_WK_A -> current.teamA.id
                PendingAction.SELECT_CAPTAIN_B, PendingAction.SELECT_WK_B -> current.teamB.id
                else -> current.battingTeamId
            }

            val newPlayer = Player(id = java.util.UUID.randomUUID().toString(), name = playerName)

            val updatedTeamA = if (current.teamA.id == teamToAddId) {
                current.teamA.copy(players = current.teamA.players + newPlayer)
            } else current.teamA

            val updatedTeamB = if (current.teamB.id == teamToAddId) {
                current.teamB.copy(players = current.teamB.players + newPlayer)
            } else current.teamB

            val updatedMatch = current.copy(teamA = updatedTeamA, teamB = updatedTeamB)

            // Update repository
            TournamentRepository.addPlayerToTeam(current.tournamentId ?: "", teamToAddId, playerName)

            val finalizedMatch = recalculateMatchFromHistory(updatedMatch)
            TournamentRepository.updateMatch(finalizedMatch.tournamentId ?: "", finalizedMatch)
            finalizedMatch
        }
    }

    fun deletePlayerFromMatch(playerId: String) {
        _matchState.update { current ->
            if (current == null) return@update null

            // Check if player participated
            val hasParticipated = current.ballHistory.any {
                it.strikerId == playerId || it.nonStrikerId == playerId ||
                        it.bowlerId == playerId || it.fielderId == playerId || it.outPlayerId == playerId
            } || current.strikerId == playerId || current.nonStrikerId == playerId || current.currentBowlerId == playerId

            if (hasParticipated) return@update current

            val teamId = if (current.teamA.players.any { it.id == playerId }) current.teamA.id else current.teamB.id

            val updatedTeamA = current.teamA.copy(players = current.teamA.players.filter { it.id != playerId })
            val updatedTeamB = current.teamB.copy(players = current.teamB.players.filter { it.id != playerId })

            val updatedMatch = current.copy(
                teamA = updatedTeamA,
                teamB = updatedTeamB,
                teamACaptainId = if (current.teamACaptainId == playerId) null else current.teamACaptainId,
                teamBCaptainId = if (current.teamBCaptainId == playerId) null else current.teamBCaptainId,
                teamAWicketKeeperId = if (current.teamAWicketKeeperId == playerId) null else current.teamAWicketKeeperId,
                teamBWicketKeeperId = if (current.teamBWicketKeeperId == playerId) null else current.teamBWicketKeeperId
            )

            // Update repository
            TournamentRepository.deletePlayer(current.tournamentId ?: "", teamId, playerId)

            val finalizedMatch = recalculateMatchFromHistory(updatedMatch)
            TournamentRepository.updateMatch(finalizedMatch.tournamentId ?: "", finalizedMatch)
            finalizedMatch
        }
    }
}
