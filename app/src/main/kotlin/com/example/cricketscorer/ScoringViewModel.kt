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

    private val _isSyncEnabled = MutableStateFlow(false)
    val isSyncEnabled: StateFlow<Boolean> = _isSyncEnabled.asStateFlow()

    private val _bowlerNotification = MutableStateFlow<String?>(null)
    val bowlerNotification: StateFlow<String?> = _bowlerNotification.asStateFlow()

    private var pendingWicketBall: Ball? = null
    private var pendingDroppedCatchBall: Ball? = null
    private val notifiedBowlerIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            TournamentRepository.tournaments.collect { tournaments ->
                if (_matchState.value == null) {
                    val liveMatch = tournaments.flatMap { it.matches }.find { it.status == MatchStatus.LIVE }
                    if (liveMatch != null) {
                        loadMatch(liveMatch)
                    }
                }

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

        NearbyManager.setMatchUpdateCallback { receivedMatch ->
            // v1.17: Sync reliability - Recalculate and update repo immediately
            val finalized = recalculateMatchFromHistory(receivedMatch)
            _matchState.value = finalized
            TournamentRepository.updateMatch(receivedMatch.tournamentId ?: "", finalized)
        }

        NearbyManager.setTournamentUpdateCallback { json ->
            TournamentRepository.importTournament(json)
        }
    }

    fun isSpellCompleted(player: Player, match: Match): Boolean {
        val base = match.maxOversPerBowler ?: return false
        val qLimit = match.quotaMaxOvers ?: base
        val qCount = match.quotaBowlersCount ?: 0
        val pOvers = player.bowlingStats.overs
        val bowlingTeam = if (match.bowlingTeamId == match.teamA.id) match.teamA else match.teamB
        val othersUsingQuota = bowlingTeam.players.count { p ->
            p.id != player.id && (p.bowlingStats.overs > base || (p.bowlingStats.overs == base && p.bowlingStats.balls > 0))
        }
        return pOvers >= qLimit || (pOvers >= base && othersUsingQuota >= qCount)
    }

    fun toggleTheme(dark: Boolean?) {
        _isDarkMode.value = dark
    }

    fun toggleSync(enabled: Boolean) {
        _isSyncEnabled.value = enabled
        if (!enabled) {
            // Logic to stop Nearby Connections will go here
        }
    }

    fun clearBowlerNotification() {
        _bowlerNotification.value = null
    }

    fun startSecondInnings() {
        _matchState.update { current ->
            val updated = current?.copy(
                isSecondInningsStarted = true,
                pendingAction = PendingAction.NONE
            )
            if (updated != null) {
                TournamentRepository.updateMatch(updated.tournamentId ?: "", updated)
            }
            updated
        }
    }

    fun swapStrike() {
        _matchState.update { current ->
            if (current == null || current.strikerId == null || current.nonStrikerId == null) return@update current
            val updated = current.copy(
                strikerId = current.nonStrikerId,
                nonStrikerId = current.strikerId
            )
            TournamentRepository.updateMatch(updated.tournamentId ?: "", updated)
            updated
        }
    }

    fun forceChangeBowler() {
        _matchState.update { current ->
            if (current == null) return@update null
            current.copy(
                pendingAction = PendingAction.SELECT_BOWLER,
                lastBowlerId = current.currentBowlerId
            )
        }
    }

    fun loadMatch(match: Match) {
        _matchState.value = match
        notifiedBowlerIds.clear()
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
        if ((currentMatch.pendingAction ?: PendingAction.NONE) != PendingAction.NONE) return
        
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
        if ((currentMatch.pendingAction ?: PendingAction.NONE) != PendingAction.NONE) return
        
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
                isLegalBall = false,
                rotateStrike = false
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
        if ((currentMatch.pendingAction ?: PendingAction.NONE) != PendingAction.NONE) return
        
        val finalOutPlayerId = outPlayerId ?: currentMatch.strikerId ?: return
        
        val ball = Ball(
            runs = 0,
            wicketType = type,
            strikerId = currentMatch.strikerId ?: return,
            nonStrikerId = currentMatch.nonStrikerId ?: return,
            bowlerId = currentMatch.currentBowlerId ?: return,
            outPlayerId = finalOutPlayerId,
            isLegalBall = type != WicketType.RETIRED_HURT
        )

        val needsFielder = type == WicketType.CAUGHT || type == WicketType.RUN_OUT || type == WicketType.STUMPED
        if (needsFielder) {
            pendingWicketBall = ball
            _matchState.update { it?.copy(pendingAction = PendingAction.SELECT_FIELDER) }
        } else {
            recordBall(ball)
        }
    }

    fun handleDroppedCatch() {
        val currentMatch = _matchState.value ?: return
        if ((currentMatch.pendingAction ?: PendingAction.NONE) != PendingAction.NONE) return
        _matchState.update { it?.copy(pendingAction = PendingAction.SELECT_FIELDER_DROPPED_CATCH) }
    }

    fun selectFielder(fielderId: String) {
        val currentMatch = _matchState.value ?: return
        val currentAction = currentMatch.pendingAction ?: PendingAction.NONE
        if (currentAction == PendingAction.SELECT_FIELDER) {
            val ball = pendingWicketBall ?: return
            val updatedBall = ball.copy(fielderId = fielderId)
            pendingWicketBall = null
            recordBall(updatedBall)
        } else if (currentAction == PendingAction.SELECT_FIELDER_DROPPED_CATCH) {
            val ball = Ball(
                runs = 0,
                strikerId = currentMatch.strikerId ?: return,
                nonStrikerId = currentMatch.nonStrikerId ?: return,
                bowlerId = currentMatch.currentBowlerId ?: return,
                isDroppedCatch = true,
                fielderId = fielderId
            )
            pendingDroppedCatchBall = ball
            _matchState.update { it?.copy(pendingAction = PendingAction.SELECT_RUNS_DROPPED_CATCH) }
        }
    }

    fun handleRunsForDroppedCatch(runs: Int, rotateStrike: Boolean) {
        val ball = pendingDroppedCatchBall ?: return
        val updatedBall = ball.copy(runs = runs, rotateStrike = rotateStrike)
        pendingDroppedCatchBall = null
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
            
            val withSelection = when (current.pendingAction ?: PendingAction.NONE) {
                PendingAction.SELECT_STRIKER -> current.copy(strikerId = playerId)
                PendingAction.SELECT_NON_STRIKER -> current.copy(nonStrikerId = playerId)
                PendingAction.SELECT_BOWLER -> {
                    current.copy(currentBowlerId = playerId)
                }
                PendingAction.SELECT_CAPTAIN_A -> current.copy(teamACaptainId = playerId, pendingAction = PendingAction.SELECT_CAPTAIN_B)
                PendingAction.SELECT_CAPTAIN_B -> current.copy(teamBCaptainId = playerId, pendingAction = PendingAction.NONE)
                PendingAction.SELECT_WK_A -> current.copy(teamAWicketKeeperId = playerId, pendingAction = PendingAction.NONE)
                PendingAction.SELECT_WK_B -> current.copy(teamBWicketKeeperId = playerId, pendingAction = PendingAction.NONE)
                PendingAction.REPLACE_STRIKER -> current.copy(strikerId = playerId, pendingAction = PendingAction.NONE)
                PendingAction.REPLACE_NON_STRIKER -> current.copy(nonStrikerId = playerId, pendingAction = PendingAction.NONE)
                PendingAction.REPLACE_BOWLER -> current.copy(currentBowlerId = playerId, pendingAction = PendingAction.NONE)
                else -> current
            }
            
            val finalizedMatch = recalculateMatchFromHistory(withSelection)
            TournamentRepository.updateMatch(finalizedMatch.tournamentId ?: "", finalizedMatch)
            finalizedMatch
        }
    }

    fun replaceStriker() {
        _matchState.update { it?.copy(pendingAction = PendingAction.REPLACE_STRIKER) }
    }

    fun replaceNonStriker() {
        _matchState.update { it?.copy(pendingAction = PendingAction.REPLACE_NON_STRIKER) }
    }

    fun replaceBowler() {
        _matchState.update { it?.copy(pendingAction = PendingAction.REPLACE_BOWLER) }
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
            
            // v1.6: Start time tracking
            val matchWithTime = if (current.startTimeMillis == null) {
                current.copy(startTimeMillis = System.currentTimeMillis())
            } else current
            
            var updatedMatch = matchWithTime.copy(ballHistory = matchWithTime.ballHistory + ball)
            
            // Fix: Clear retired batter ID so it isn't restored by recalculateMatchFromHistory
            if (ball.wicketType == WicketType.RETIRED_HURT) {
                val outId = ball.outPlayerId ?: ball.strikerId
                if (updatedMatch.strikerId == outId) updatedMatch = updatedMatch.copy(strikerId = null)
                if (updatedMatch.nonStrikerId == outId) updatedMatch = updatedMatch.copy(nonStrikerId = null)
            }

            val finalizedMatch = recalculateMatchFromHistory(updatedMatch)
            TournamentRepository.updateMatch(finalizedMatch.tournamentId ?: "", finalizedMatch)
            
            // v1.35: Trigger spell notification ONLY here (once per ball)
            if (finalizedMatch.status == MatchStatus.LIVE && ball.isLegalBall && finalizedMatch.totalBalls % 6 == 0 && _bowlerNotification.value == null) {
                val bowlingTeam = if (finalizedMatch.bowlingTeamId == finalizedMatch.teamA.id) finalizedMatch.teamA else finalizedMatch.teamB
                val bPlayer = bowlingTeam.players.find { it.id == ball.bowlerId }
                if (bPlayer != null && finalizedMatch.maxOversPerBowler != null && !notifiedBowlerIds.contains(bPlayer.id)) {
                    val base = finalizedMatch.maxOversPerBowler!!
                    val qLimit = finalizedMatch.quotaMaxOvers ?: base
                    val qCount = finalizedMatch.quotaBowlersCount ?: 0
                    
                    val pOvers = bPlayer.bowlingStats.overs
                    val othersUsingQuota = bowlingTeam.players.count { p ->
                        p.id != ball.bowlerId && (p.bowlingStats.overs > base || (p.bowlingStats.overs == base && p.bowlingStats.balls > 0))
                    }
                    
                    if (pOvers >= qLimit || (pOvers >= base && othersUsingQuota >= qCount)) {
                        _bowlerNotification.value = "${bPlayer.name} has completed their spell! 🛑"
                        notifiedBowlerIds.add(bPlayer.id)
                    }
                }
            }

            // v1.8: Sync if enabled
            if (_isSyncEnabled.value) {
                // We'll need context for Nearby, handled via a side effect or UI trigger
            }

            finalizedMatch
        }
    }

    private fun recalculateMatchFromHistory(match: Match): Match {
        if (match.tossWinnerId == null) {
            return match.copy(pendingAction = PendingAction.TOSS_REQUIRED)
        }

        // v1.17: Ensure player names are taken from the source match object
        val initialTeamA = resetTeamStats(match.teamA)
        val initialTeamB = resetTeamStats(match.teamB)

        var current = match.copy(
            totalRuns = 0, totalWickets = 0, totalBalls = 0,
            wideCount = 0, noBallCount = 0, byeCount = 0, legByeCount = 0,
            wicketHistory = emptyList(),
            battingOrder = emptyList(),
            teamA = initialTeamA,
            teamB = initialTeamB,
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

            // Track batting order
            val newBattingOrder = current.battingOrder.toMutableList()
            if (ball.strikerId.isNotEmpty() && !newBattingOrder.contains(ball.strikerId)) newBattingOrder.add(ball.strikerId)
            if (ball.nonStrikerId.isNotEmpty() && !newBattingOrder.contains(ball.nonStrikerId)) newBattingOrder.add(ball.nonStrikerId)

            current = current.copy(
                totalRuns = current.totalRuns + ball.runs + ball.extraRuns,
                totalWickets = current.totalWickets + (if (ball.wicketType != WicketType.NONE && ball.wicketType != WicketType.RETIRED_HURT) 1 else 0),
                totalBalls = current.totalBalls + (if (ball.isLegalBall) 1 else 0),
                wideCount = current.wideCount + (if (ball.extrasType == ExtrasType.WIDE) ball.extraRuns else 0),
                noBallCount = current.noBallCount + (if (ball.extrasType == ExtrasType.NO_BALL) ball.extraRuns else 0),
                byeCount = current.byeCount + (if (ball.extrasType == ExtrasType.BYE) ball.extraRuns else 0),
                legByeCount = current.legByeCount + (if (ball.extrasType == ExtrasType.LEG_BYE) ball.extraRuns else 0),
                battingOrder = newBattingOrder,
                teamA = updateTeamStats(current.teamA, ball, current.battingTeamId == current.teamA.id, current.bowlingTeamId == current.teamA.id),
                teamB = updateTeamStats(current.teamB, ball, current.battingTeamId == current.teamB.id, current.bowlingTeamId == current.teamB.id)
            )

            if (ball.wicketType != WicketType.NONE) {
                val outId = ball.outPlayerId ?: ball.strikerId
                val outName = battingTeam.players.find { it.id == outId }?.name ?: "Unknown"
                val displayOutName = "☝️ $outName" // v1.6: Wicket emoji
                val bName = bowlingTeam.players.find { it.id == ball.bowlerId }?.name
                val fName = bowlingTeam.players.find { it.id == ball.fielderId }?.name
                current = current.copy(wicketHistory = current.wicketHistory + WicketRecord(current.totalWickets, displayOutName, current.totalRuns, "${current.totalBalls/6}.${current.totalBalls%6}", ball.wicketType, bName, fName))
            }

            var sId: String? = ball.strikerId
            var nsId: String? = ball.nonStrikerId
            var bId: String? = ball.bowlerId
            var lbId: String? = current.lastBowlerId

            val physicalRuns = when (ball.extrasType) {
                ExtrasType.WIDE -> (ball.extraRuns - 1).coerceAtLeast(0)
                ExtrasType.BYE, ExtrasType.LEG_BYE -> ball.extraRuns
                else -> ball.runs
            }
            
            val rotateOnRuns = ball.rotateStrike && physicalRuns % 2 != 0
            val overEnd = ball.isLegalBall && current.totalBalls % 6 == 0
            
            if (overEnd) {
                // v1.47: Smart Strike Rotation - NS always becomes striker for next over
                val t = sId; sId = nsId; nsId = t
                lbId = bId; bId = null
            } else if (rotateOnRuns) {
                val t = sId; sId = nsId; nsId = t
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
                // v1.6: Calculate duration
                val duration = match.innings1Data?.durationMinutes ?: current.startTimeMillis?.let { start ->
                    ((System.currentTimeMillis() - start) / 60000).toInt()
                } ?: 0

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
                        recordedBallsCount = current.ballHistory.indexOf(ball) + 1,
                        durationMinutes = duration,
                        battingOrder = current.battingOrder
                    ),
                    currentInnings = 2,
                    target = current.totalRuns + 1,
                    battingTeamId = current.bowlingTeamId,
                    bowlingTeamId = current.battingTeamId,
                    totalRuns = 0, totalWickets = 0, totalBalls = 0,
                    wicketHistory = emptyList(),
                    battingOrder = emptyList(),
                    strikerId = null, nonStrikerId = null, currentBowlerId = null, lastBowlerId = null,
                    pendingAction = if (match.isSecondInningsStarted) PendingAction.NONE else PendingAction.START_SECOND_INNINGS,
                    startTimeMillis = if (match.currentInnings == 2) match.startTimeMillis else System.currentTimeMillis() // Reset for 2nd innings
                )
            } else if (current.currentInnings == 2) {
                if ((current.pendingAction ?: PendingAction.NONE) == PendingAction.START_SECOND_INNINGS && match.isSecondInningsStarted) {
                    current = current.copy(pendingAction = PendingAction.NONE)
                }
                if (current.totalRuns >= (current.target ?: 0)) {
                    current = current.copy(
                        status = MatchStatus.COMPLETED, 
                        winnerId = current.battingTeamId,
                        endTimeMillis = match.endTimeMillis ?: System.currentTimeMillis()
                    )
                } else if (inningsEnded) {
                    current = current.copy(
                        status = MatchStatus.COMPLETED, 
                        winnerId = if (current.totalRuns < (current.target ?: 0) - 1) current.bowlingTeamId else null,
                        endTimeMillis = match.endTimeMillis ?: System.currentTimeMillis()
                    )
                }
            }
        }

        var final = current
        if (final.status == MatchStatus.COMPLETED) {
            final = final.copy(pendingAction = PendingAction.NONE)
        } else {
            // Restore manual selections if they are valid
            if (final.strikerId == null && isBatterAvailable(final, match.strikerId)) {
                final = final.copy(strikerId = match.strikerId)
                val newOrder = final.battingOrder.toMutableList()
                match.strikerId?.let { if (!newOrder.contains(it)) newOrder.add(it) }
                final = final.copy(battingOrder = newOrder)
            }
            if (final.nonStrikerId == null && isBatterAvailable(final, match.nonStrikerId)) {
                final = final.copy(nonStrikerId = match.nonStrikerId)
                val newOrder = final.battingOrder.toMutableList()
                match.nonStrikerId?.let { if (!newOrder.contains(it)) newOrder.add(it) }
                final = final.copy(battingOrder = newOrder)
            }
            
            // Restore bowler: supports both start-of-over and mid-over manual changes
            val manualBowlerId = match.currentBowlerId
            if (manualBowlerId != null && manualBowlerId != final.currentBowlerId && isBowlerAvailable(final, manualBowlerId)) {
                final = final.copy(currentBowlerId = manualBowlerId)
            } else if (final.currentBowlerId == null && isBowlerAvailable(final, manualBowlerId)) {
                final = final.copy(currentBowlerId = manualBowlerId)
            }

            // Auto-advance Captain selection
            if (final.totalBalls == 0 && final.currentInnings == 1) {
                if (match.teamACaptainId == null) final = final.copy(pendingAction = PendingAction.SELECT_CAPTAIN_A)
                else if (match.teamBCaptainId == null) final = final.copy(pendingAction = PendingAction.SELECT_CAPTAIN_B)
            }
            
            if ((final.pendingAction ?: PendingAction.NONE) == PendingAction.NONE) {
                if (final.bowlingTeamId == final.teamA.id && final.teamAWicketKeeperId == null) final = final.copy(pendingAction = PendingAction.SELECT_WK_A)
                else if (final.bowlingTeamId == final.teamB.id && final.teamBWicketKeeperId == null) final = final.copy(pendingAction = PendingAction.SELECT_WK_B)
            }

            if ((final.pendingAction ?: PendingAction.NONE) == PendingAction.NONE) {
                final = when {
                    final.strikerId == null -> final.copy(pendingAction = PendingAction.SELECT_STRIKER)
                    final.nonStrikerId == null -> final.copy(pendingAction = PendingAction.SELECT_NON_STRIKER)
                    final.currentBowlerId == null -> final.copy(pendingAction = PendingAction.SELECT_BOWLER)
                    else -> final.copy(pendingAction = PendingAction.NONE)
                }
            }
        }
        return final
    }

    private fun isBatterAvailable(m: Match, pId: String?): Boolean {
        if (pId == null) return false
        val team = if (m.battingTeamId == m.teamA.id) m.teamA else m.teamB
        val player = team.players.find { it.id == pId } ?: return false
        // v1.47: Allow Retired Hurt players to re-enter
        return !player.battingStats.isOut && pId != m.strikerId && pId != m.nonStrikerId
    }

    private fun isBowlerAvailable(m: Match, pId: String?): Boolean {
        if (pId == null) return false
        val team = if (m.bowlingTeamId == m.teamA.id) m.teamA else m.teamB
        val player = team.players.find { it.id == pId } ?: return false
        val isWK = pId == (if (team.id == m.teamA.id) m.teamAWicketKeeperId else m.teamBWicketKeeperId)
        // v1.49: Permissive check to allow selection of bowlers who completed their spell
        return pId != m.lastBowlerId && !isWK
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
                        isOut = p.battingStats.isOut || (isOut && ball.wicketType != WicketType.RETIRED_HURT),
                        isRetiredHurt = ball.wicketType == WicketType.RETIRED_HURT,
                        wicketType = if (isOut) ball.wicketType else p.battingStats.wicketType,
                        dismissalBowlerId = if (isOut && ball.wicketType != WicketType.RUN_OUT && ball.wicketType != WicketType.RETIRED_HURT) ball.bowlerId else p.battingStats.dismissalBowlerId,
                        dismissalFielderId = if (isOut) ball.fielderId else p.battingStats.dismissalFielderId
                    ))
                } else if (p.id == ball.nonStrikerId) {
                    if (isOut) {
                        np = np.copy(battingStats = p.battingStats.copy(
                            isOut = ball.wicketType != WicketType.RETIRED_HURT,
                            isRetiredHurt = ball.wicketType == WicketType.RETIRED_HURT,
                            wicketType = ball.wicketType,
                            dismissalFielderId = ball.fielderId
                        ))
                    } else {
                        np = np.copy(battingStats = p.battingStats.copy(isRetiredHurt = false))
                    }
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
                    stumpings = p.fieldingStats.stumpings + (if (ball.wicketType == WicketType.STUMPED) 1 else 0),
                    droppedCatches = p.fieldingStats.droppedCatches + (if (ball.isDroppedCatch) 1 else 0)
                ))
            }
            np
        })
    }

    fun updateMatchSettings(newOvers: Int, newMaxOvers: Int?, newQuotaCount: Int? = null, newQuotaLimit: Int? = null) {
        _matchState.update { current ->
            if (current == null) return@update null
            
            // Update tournament settings in repository
            TournamentRepository.updateTournamentSettings(
                current.tournamentId ?: "", 
                newOvers, 
                newMaxOvers,
                newQuotaCount,
                newQuotaLimit
            )
            
            val updatedMatch = current.copy(
                oversPerInnings = newOvers, 
                maxOversPerBowler = newMaxOvers,
                quotaBowlersCount = newQuotaCount,
                quotaMaxOvers = newQuotaLimit
            )
            val finalizedMatch = recalculateMatchFromHistory(updatedMatch)
            TournamentRepository.updateMatch(finalizedMatch.tournamentId ?: "", finalizedMatch)
            finalizedMatch
        }
    }

    fun undo() {
        _matchState.update { current ->
            if (current == null || current.ballHistory.isEmpty()) return@update current
            pendingWicketBall = null
            pendingDroppedCatchBall = null
            notifiedBowlerIds.clear()
            val undone = current.copy(ballHistory = current.ballHistory.dropLast(1), strikerId = null, nonStrikerId = null, currentBowlerId = null)
            val finalized = recalculateMatchFromHistory(undone)
            TournamentRepository.updateMatch(finalized.tournamentId ?: "", finalized)
            finalized
        }
    }

    fun addNewPlayerToMatch(context: android.content.Context, playerName: String, battingStyle: BattingStyle = BattingStyle.RHB, bowlingStyle: BowlingStyle = BowlingStyle.RightArm) {
        val current = _matchState.value ?: return
        val teamToAddId = when (current.pendingAction ?: PendingAction.NONE) {
            PendingAction.SELECT_STRIKER, PendingAction.SELECT_NON_STRIKER, PendingAction.REPLACE_STRIKER, PendingAction.REPLACE_NON_STRIKER -> current.battingTeamId
            PendingAction.SELECT_BOWLER, PendingAction.REPLACE_BOWLER, PendingAction.SELECT_FIELDER, PendingAction.SELECT_FIELDER_DROPPED_CATCH -> current.bowlingTeamId
            PendingAction.SELECT_CAPTAIN_A, PendingAction.SELECT_WK_A -> current.teamA.id
            PendingAction.SELECT_CAPTAIN_B, PendingAction.SELECT_WK_B -> current.teamB.id
            else -> current.battingTeamId
        }

        val success = TournamentRepository.addPlayerToTeam(current.tournamentId ?: "", teamToAddId, playerName, battingStyle, bowlingStyle)
        if (!success) {
            android.widget.Toast.makeText(context, "Player $playerName already exists in this tournament! 👤❌", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        _matchState.update { state ->
            if (state == null) return@update null
            
            val newPlayer = Player(id = java.util.UUID.randomUUID().toString(), name = playerName.trim(), battingStyle = battingStyle, bowlingStyle = bowlingStyle)

            val updatedTeamA = if (state.teamA.id == teamToAddId) {
                state.teamA.copy(players = state.teamA.players + newPlayer)
            } else state.teamA

            val updatedTeamB = if (state.teamB.id == teamToAddId) {
                state.teamB.copy(players = state.teamB.players + newPlayer)
            } else state.teamB

            val updatedMatch = state.copy(teamA = updatedTeamA, teamB = updatedTeamB)
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
