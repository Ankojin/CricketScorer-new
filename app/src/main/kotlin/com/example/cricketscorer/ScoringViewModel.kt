package com.example.cricketscorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
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
    private var lastNotifiedBowlerId: String? = null
    private val notifiedBowlerIds = mutableSetOf<String>()

    private val _isSelectingPlayer = MutableStateFlow(false)
    val isSelectingPlayer: StateFlow<Boolean> = _isSelectingPlayer.asStateFlow()

    init {
        viewModelScope.launch {
            TournamentRepository.tournaments.collect { tournaments ->
                if (_isSelectingPlayer.value) return@collect
                
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
        val updated = _matchState.updateAndGet { current ->
            current?.copy(
                isSecondInningsStarted = true,
                pendingAction = PendingAction.NONE
            )
        }
        if (updated != null) {
            // v1.72: Definitive Transition Fix - Recalculate immediately to trigger selection overlays
            val finalized = recalculateMatchFromHistory(updated)
            _matchState.value = finalized
            TournamentRepository.updateMatch(finalized.tournamentId ?: "", finalized)
        }
    }

    fun swapStrike() {
        val updated = _matchState.updateAndGet { current ->
            if (current == null || current.strikerId == null || current.nonStrikerId == null) return@updateAndGet current
            current.copy(
                strikerId = current.nonStrikerId,
                nonStrikerId = current.strikerId
            )
        }
        if (updated != null) {
            TournamentRepository.updateMatch(updated.tournamentId ?: "", updated)
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
        val recalculated = recalculateMatchFromHistory(match)
        _matchState.value = recalculated
        notifiedBowlerIds.clear()
        if (recalculated.tossWinnerId == null && recalculated.status != MatchStatus.COMPLETED) {
            _matchState.update { it?.copy(pendingAction = PendingAction.TOSS_REQUIRED) }
        }
    }

    fun handleToss(winnerId: String, decision: String) {
        val updated = _matchState.updateAndGet { current ->
            if (current == null) return@updateAndGet null
            
            val battingTeamId = if ((winnerId == current.teamA.id && decision == "BAT") || (winnerId == current.teamB.id && decision == "BOWL")) current.teamA.id else current.teamB.id
            val bowlingTeamId = if (battingTeamId == current.teamA.id) current.teamB.id else current.teamA.id
            
            // v1.89: Emergency Hard Reset to kill ghost scores permanently
            val updatedMatch = current.copy(
                tossWinnerId = winnerId,
                tossDecision = decision,
                initialBattingTeamId = battingTeamId,
                initialBowlingTeamId = bowlingTeamId,
                battingTeamId = battingTeamId,
                bowlingTeamId = bowlingTeamId,
                status = MatchStatus.LIVE,
                pendingAction = PendingAction.NONE,
                ballHistory = emptyList(),
                wicketHistory = emptyList(),
                battingOrder = emptyList(),
                innings1Data = null,
                target = null,
                totalRuns = 0,
                totalWickets = 0,
                totalBalls = 0,
                wideCount = 0,
                noBallCount = 0,
                byeCount = 0,
                legByeCount = 0,
                strikerId = null,
                nonStrikerId = null,
                currentBowlerId = null,
                lastBowlerId = null,
                isSecondInningsStarted = false,
                teamA = resetTeamStats(current.teamA),
                teamB = resetTeamStats(current.teamB)
            )
            recalculateMatchFromHistory(updatedMatch)
        }
        if (updated != null) {
            TournamentRepository.updateMatch(updated.tournamentId ?: "", updated)
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
        if (type == WicketType.RUN_OUT) {
            pendingWicketBall = ball
            _matchState.update { it?.copy(pendingAction = PendingAction.SELECT_RUNS_WICKET) }
        } else if (needsFielder) {
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

    fun handleRunsForWicket(runs: Int, rotateStrike: Boolean) {
        val ball = pendingWicketBall ?: return
        val updatedBall = ball.copy(runs = runs, rotateStrike = rotateStrike)
        pendingWicketBall = updatedBall
        _matchState.update { it?.copy(pendingAction = PendingAction.SELECT_FIELDER) }
    }

    fun editBall(index: Int, updatedBall: Ball) {
        _matchState.update { current ->
            if (current == null || index < 0 || index >= current.ballHistory.size) return@update current
            val newHistory = current.ballHistory.toMutableList()
            newHistory[index] = updatedBall
            val updatedMatch = current.copy(ballHistory = newHistory)
            val finalizedMatch = recalculateMatchFromHistory(updatedMatch)
            finalizedMatch
        }
        _matchState.value?.let { 
            TournamentRepository.updateMatch(it.tournamentId ?: "", it)
        }
    }

    fun selectNewPlayer(playerId: String) {
        _isSelectingPlayer.value = true
        val updated = _matchState.updateAndGet { current ->
            if (current == null) return@updateAndGet null
            
            var nextAction = PendingAction.NONE
            val withSelection = when (current.pendingAction ?: PendingAction.NONE) {
                PendingAction.SELECT_STRIKER, PendingAction.REPLACE_STRIKER -> {
                    nextAction = PendingAction.SELECT_NON_STRIKER
                    val newOrder = current.battingOrder.toMutableList()
                    if (!newOrder.contains(playerId)) newOrder.add(playerId)
                    current.copy(strikerId = playerId, battingOrder = newOrder)
                }
                PendingAction.SELECT_NON_STRIKER, PendingAction.REPLACE_NON_STRIKER -> {
                    nextAction = PendingAction.SELECT_BOWLER
                    val newOrder = current.battingOrder.toMutableList()
                    if (!newOrder.contains(playerId)) newOrder.add(playerId)
                    current.copy(nonStrikerId = playerId, battingOrder = newOrder)
                }
                PendingAction.SELECT_BOWLER, PendingAction.REPLACE_BOWLER -> {
                    nextAction = PendingAction.NONE
                    current.copy(currentBowlerId = playerId)
                }
                PendingAction.SELECT_WK_A -> {
                    nextAction = PendingAction.NONE
                    current.copy(teamAWicketKeeperId = playerId)
                }
                PendingAction.SELECT_WK_B -> {
                    nextAction = PendingAction.NONE
                    current.copy(teamBWicketKeeperId = playerId)
                }
                else -> current
            }
            
            withSelection.copy(pendingAction = nextAction)
        }
        // v1.97: Process Lock management
        if (updated != null && (updated.pendingAction ?: PendingAction.NONE) == PendingAction.NONE) {
            TournamentRepository.updateMatch(updated.tournamentId ?: "", updated)
            _isSelectingPlayer.value = false
        }
    }

    fun cancelSelection() {
        _isSelectingPlayer.value = false
        _matchState.update { it?.copy(pendingAction = PendingAction.NONE) }
        refreshMatchEngine()
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
        var notificationText: String? = null
        var bowlerToNotifyId: String? = null

        val finalizedMatch = _matchState.updateAndGet { current ->
            if (current == null) return@updateAndGet null
            
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

            val result = recalculateMatchFromHistory(updatedMatch)
            
            // v1.61: Prepare spell notification logic (Side-effect free preparation)
            if (result.status == MatchStatus.LIVE && ball.isLegalBall && result.totalBalls % 6 == 0 && _bowlerNotification.value == null) {
                val bowlingTeam = if (result.bowlingTeamId == result.teamA.id) result.teamA else result.teamB
                val ballBowlerId = ball.bowlerId ?: ""
                val bPlayer = bowlingTeam.players.find { it.id == ballBowlerId }
                if (bPlayer != null && result.maxOversPerBowler != null && result.lastNotifiedBowlerId != bPlayer.id) {
                    val base = result.maxOversPerBowler!!
                    val qLimit = result.quotaMaxOvers ?: base
                    val qCount = result.quotaBowlersCount ?: 0
                    
                    val pOvers = bPlayer.bowlingStats.overs
                    val othersUsingQuota = bowlingTeam.players.count { p ->
                        p.id != ballBowlerId && (p.bowlingStats.overs > base || (p.bowlingStats.overs == base && p.bowlingStats.balls > 0))
                    }
                    
                    if (pOvers >= qLimit || (pOvers >= base && othersUsingQuota >= qCount)) {
                        notificationText = "${bPlayer.name} has completed their spell! 🛑"
                        bowlerToNotifyId = bPlayer.id
                    }
                }
            }
            result
        }

        // Apply side effects outside of update block
        if (finalizedMatch != null) {
            val finalWithNotification = if (bowlerToNotifyId != null) {
                finalizedMatch.copy(lastNotifiedBowlerId = bowlerToNotifyId)
            } else finalizedMatch

            TournamentRepository.updateMatch(finalWithNotification.tournamentId ?: "", finalWithNotification)
            
            notificationText?.let { text ->
                _bowlerNotification.value = text
                bowlerToNotifyId?.let { id -> 
                    notifiedBowlerIds.add(id)
                    lastNotifiedBowlerId = id
                }
            }
            
            // If we updated the match with lastNotifiedBowlerId, ensure state is updated
            if (bowlerToNotifyId != null) {
                _matchState.update { finalWithNotification }
            }
        }
    }

    fun refreshMatchEngine() {
        _matchState.update { current ->
            if (current == null) return@update null
            recalculateMatchFromHistory(current)
        }
        _matchState.value?.let { 
            TournamentRepository.updateMatch(it.tournamentId ?: "", it)
        }
    }

    private fun healLegacyId(id: String?, match: Match, team: Team): String? {
        if (id.isNullOrEmpty()) return null
        // If it's a UUID (typical length > 30, no spaces, contains hyphens), it's likely not legacy
        if (id.length > 30 && id.contains("-") && !id.contains(" ")) return id
        // Otherwise, try to find a player by name in the provided team
        return team.players.find { it.name.equals(id, ignoreCase = true) }?.id ?: id
    }

    private fun recalculateMatchFromHistory(match: Match): Match {
        if (match.tossWinnerId == null) {
            return match.copy(pendingAction = PendingAction.TOSS_REQUIRED)
        }

        // v1.90: Definitive Recalculation Engine Fix
        val wasSecondInningsStarted = match.isSecondInningsStarted
        val startInnings = if (match.ballHistory.isEmpty() && match.currentInnings == 2) 2 else 1
        
        // v1.90: Strict Team Assignment Synchronization
        val startBattingTeamId = if (startInnings == 2) {
            match.initialBowlingTeamId ?: if (match.initialBattingTeamId == match.teamA.id) match.teamB.id else match.teamA.id
        } else {
            match.initialBattingTeamId ?: match.teamA.id
        }
        val startBowlingTeamId = if (startInnings == 2) {
            match.initialBattingTeamId ?: match.teamA.id
        } else {
            match.initialBowlingTeamId ?: if (match.initialBattingTeamId == match.teamA.id) match.teamB.id else match.teamA.id
        }

        val shouldReset = match.ballHistory.isNotEmpty()
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
            currentInnings = startInnings,
            battingTeamId = startBattingTeamId,
            bowlingTeamId = startBowlingTeamId,
            initialBattingTeamId = match.initialBattingTeamId,
            initialBowlingTeamId = match.initialBowlingTeamId,
            innings1Data = if (startInnings == 2) match.innings1Data else null,
            target = if (startInnings == 2) match.target else null,
            winnerId = null,
            strikerId = if (shouldReset) null else match.strikerId, 
            nonStrikerId = if (shouldReset) null else match.nonStrikerId, 
            currentBowlerId = if (shouldReset) null else match.currentBowlerId, 
            lastBowlerId = if (shouldReset) null else match.lastBowlerId,
            isSecondInningsStarted = if (startInnings == 2) wasSecondInningsStarted else false,
            lastNotifiedBowlerId = null,
            pendingAction = PendingAction.NONE
        )

        var ballsProcessedInInnings1 = 0

        match.ballHistory.forEach { ball ->
            if (current.status == MatchStatus.COMPLETED) return@forEach
            
            val battingTeam = if (current.battingTeamId == current.teamA.id) current.teamA else current.teamB
            val bowlingTeam = if (current.bowlingTeamId == current.teamA.id) current.teamA else current.teamB

            // Legacy ID Healing - v1.74 Fix
            val sHealed = healLegacyId(ball.strikerId, match, battingTeam)
            val nsHealed = healLegacyId(ball.nonStrikerId, match, battingTeam)
            val bHealed = healLegacyId(ball.bowlerId, match, bowlingTeam)
            val oHealed = healLegacyId(ball.outPlayerId, match, battingTeam)
            val fHealed = healLegacyId(ball.fielderId, match, bowlingTeam)

            val healedBall = ball.copy(
                strikerId = sHealed,
                nonStrikerId = nsHealed,
                bowlerId = bHealed,
                outPlayerId = oHealed,
                fielderId = fHealed
            )

            // Track batting order
            val newBattingOrder = current.battingOrder.toMutableList()
            healedBall.strikerId?.let { id -> if (id.isNotEmpty() && !newBattingOrder.contains(id)) newBattingOrder.add(id) }
            healedBall.nonStrikerId?.let { id -> if (id.isNotEmpty() && !newBattingOrder.contains(id)) newBattingOrder.add(id) }
            
            val outId = healedBall.outPlayerId ?: (if (healedBall.wicketType != WicketType.NONE && healedBall.wicketType != WicketType.RETIRED_HURT) healedBall.strikerId else null)
            if (!outId.isNullOrEmpty() && !newBattingOrder.contains(outId)) newBattingOrder.add(outId)

            // v1.79 Precise Ball Counting: Increment BEFORE inningsEnded check
            if (current.currentInnings == 1) {
                ballsProcessedInInnings1++
            }

            current = current.copy(
                totalRuns = current.totalRuns + healedBall.runs + healedBall.extraRuns,
                totalWickets = current.totalWickets + (if (healedBall.wicketType != WicketType.NONE && healedBall.wicketType != WicketType.RETIRED_HURT) 1 else 0),
                totalBalls = current.totalBalls + (if (healedBall.isLegalBall) 1 else 0),
                wideCount = current.wideCount + (if (healedBall.extrasType == ExtrasType.WIDE) healedBall.extraRuns else 0),
                noBallCount = current.noBallCount + (if (healedBall.extrasType == ExtrasType.NO_BALL) healedBall.extraRuns else 0),
                byeCount = current.byeCount + (if (healedBall.extrasType == ExtrasType.BYE) healedBall.extraRuns else 0),
                legByeCount = current.legByeCount + (if (healedBall.extrasType == ExtrasType.LEG_BYE) healedBall.extraRuns else 0),
                battingOrder = newBattingOrder,
                teamA = updateTeamStats(current.teamA, healedBall, current.battingTeamId == current.teamA.id, current.bowlingTeamId == current.teamA.id),
                teamB = updateTeamStats(current.teamB, healedBall, current.battingTeamId == current.teamB.id, current.bowlingTeamId == current.teamB.id)
            )

            if (healedBall.wicketType != WicketType.NONE && healedBall.wicketType != WicketType.RETIRED_HURT) {
                val outName = battingTeam.players.find { it.id == outId }?.name 
                    ?: outId?.let { if (!it.contains("-") || it.contains(" ")) it else "Unknown" } ?: "Unknown"
                val displayOutName = "☝️ $outName"
                val bName = bowlingTeam.players.find { it.id == healedBall.bowlerId }?.name
                    ?: (healedBall.bowlerId?.let { if (!it.contains("-") || it.contains(" ")) it else null })
                val fName = bowlingTeam.players.find { it.id == healedBall.fielderId }?.name
                    ?: (healedBall.fielderId?.let { if (!it.contains("-") || it.contains(" ")) it else null })
                current = current.copy(wicketHistory = current.wicketHistory + WicketRecord(current.totalWickets, displayOutName, current.totalRuns, "${current.totalBalls/6}.${current.totalBalls%6}", healedBall.wicketType, bName, fName))
            }

            var sId: String? = healedBall.strikerId
            var nsId: String? = healedBall.nonStrikerId
            var bId: String? = healedBall.bowlerId
            var lbId: String? = current.lastBowlerId

            val physicalRuns = when (healedBall.extrasType) {
                ExtrasType.WIDE -> (healedBall.extraRuns - 1).coerceAtLeast(0)
                ExtrasType.BYE, ExtrasType.LEG_BYE -> healedBall.extraRuns
                else -> healedBall.runs
            }
            
            val rotateOnRuns = healedBall.rotateStrike && physicalRuns % 2 != 0
            val overEnd = healedBall.isLegalBall && current.totalBalls % 6 == 0
            
            if (rotateOnRuns) {
                val t = sId; sId = nsId; nsId = t
            }
            if (overEnd) {
                val t = sId; sId = nsId; nsId = t
                lbId = bId; bId = null
            }

            if (healedBall.wicketType != WicketType.NONE) {
                val actualOutId = healedBall.outPlayerId ?: healedBall.strikerId
                if (sId == actualOutId && sId != null) sId = null
                else if (nsId == actualOutId && nsId != null) nsId = null
            }

            current = current.copy(strikerId = sId, nonStrikerId = nsId, currentBowlerId = bId, lastBowlerId = lbId)

            val maxWickets = (battingTeam.players.size - 1).coerceAtLeast(1)
            val inningsEnded = current.totalWickets >= maxWickets || current.totalBalls >= current.oversPerInnings * 6
            
            // v1.79 Transition Integrity: Once in 2nd innings, stay in 2nd innings
            if (current.currentInnings == 1 && inningsEnded) {
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
                        recordedBallsCount = ballsProcessedInInnings1,
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
                    pendingAction = if (wasSecondInningsStarted) PendingAction.NONE else PendingAction.START_SECOND_INNINGS,
                    isSecondInningsStarted = wasSecondInningsStarted,
                    startTimeMillis = if (match.currentInnings == 2) match.startTimeMillis else System.currentTimeMillis()
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



        if (current.status == MatchStatus.COMPLETED) {
            current = current.copy(pendingAction = PendingAction.NONE)
        }

        return current
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
        val updated = _matchState.updateAndGet { current ->
            if (current == null) return@updateAndGet null
            
            current.copy(
                oversPerInnings = newOvers, 
                maxOversPerBowler = newMaxOvers,
                quotaBowlersCount = newQuotaCount,
                quotaMaxOvers = newQuotaLimit
            ).let { recalculateMatchFromHistory(it) }
        }
        
        updated?.let { 
            TournamentRepository.updateTournamentSettings(
                it.tournamentId ?: "", 
                newOvers, 
                newMaxOvers,
                newQuotaCount,
                newQuotaLimit
            )
            TournamentRepository.updateMatch(it.tournamentId ?: "", it)
        }
    }

    fun undo() {
        _matchState.update { current ->
            if (current == null || current.ballHistory.isEmpty()) return@update current
            pendingWicketBall = null
            pendingDroppedCatchBall = null
            notifiedBowlerIds.clear()
            val undone = current.copy(ballHistory = current.ballHistory.dropLast(1), strikerId = null, nonStrikerId = null, currentBowlerId = null)
            recalculateMatchFromHistory(undone)
        }
        _matchState.value?.let { 
            TournamentRepository.updateMatch(it.tournamentId ?: "", it)
        }
    }

    fun addNewPlayerToMatch(context: android.content.Context, playerName: String, battingStyle: BattingStyle = BattingStyle.RHB, bowlingStyle: BowlingStyle = BowlingStyle.RFM, canBowl: Boolean = true) {
        val current = _matchState.value ?: return
        val teamToAddId = when (current.pendingAction ?: PendingAction.NONE) {
            PendingAction.SELECT_STRIKER, PendingAction.SELECT_NON_STRIKER, PendingAction.REPLACE_STRIKER, PendingAction.REPLACE_NON_STRIKER -> current.battingTeamId
            PendingAction.SELECT_BOWLER, PendingAction.REPLACE_BOWLER, PendingAction.SELECT_FIELDER, PendingAction.SELECT_FIELDER_DROPPED_CATCH -> current.bowlingTeamId
            PendingAction.SELECT_WK_A -> current.teamA.id
            PendingAction.SELECT_WK_B -> current.teamB.id
            else -> current.battingTeamId
        }

        val success = TournamentRepository.addPlayerToTeam(current.tournamentId ?: "", teamToAddId, playerName, battingStyle, bowlingStyle, canBowl = canBowl)
        if (!success) {
            android.widget.Toast.makeText(context, "Player $playerName already exists in this tournament! 👤❌", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        _matchState.update { state ->
            if (state == null) return@update null
            
            val newPlayer = Player(id = java.util.UUID.randomUUID().toString(), name = playerName.trim(), battingStyle = battingStyle, bowlingStyle = bowlingStyle, canBowl = canBowl)

            val updatedTeamA = if (state.teamA.id == teamToAddId) {
                state.teamA.copy(players = state.teamA.players + newPlayer)
            } else state.teamA

            val updatedTeamB = if (state.teamB.id == teamToAddId) {
                state.teamB.copy(players = state.teamB.players + newPlayer)
            } else state.teamB

            val updatedMatch = state.copy(teamA = updatedTeamA, teamB = updatedTeamB)
            recalculateMatchFromHistory(updatedMatch)
        }
        _matchState.value?.let { 
            TournamentRepository.updateMatch(it.tournamentId ?: "", it)
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

            // Update repository
            TournamentRepository.deletePlayer(current.tournamentId ?: "", teamId, playerId)

            val updatedMatch = current.copy(
                teamA = current.teamA.copy(players = current.teamA.players.filter { it.id != playerId }),
                teamB = current.teamB.copy(players = current.teamB.players.filter { it.id != playerId }),
                teamACaptainId = if (current.teamACaptainId == playerId) null else current.teamACaptainId,
                teamBCaptainId = if (current.teamBCaptainId == playerId) null else current.teamBCaptainId,
                teamAWicketKeeperId = if (current.teamAWicketKeeperId == playerId) null else current.teamAWicketKeeperId,
                teamBWicketKeeperId = if (current.teamBWicketKeeperId == playerId) null else current.teamBWicketKeeperId
            )

            recalculateMatchFromHistory(updatedMatch)
        }
        _matchState.value?.let { 
            TournamentRepository.updateMatch(it.tournamentId ?: "", it)
        }
    }
}
