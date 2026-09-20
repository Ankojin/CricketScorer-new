package com.example.cricketscorer

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

    private val _activeWicketContext = MutableStateFlow<ActiveWicketContext?>(null)
    val activeWicketContext: StateFlow<ActiveWicketContext?> = _activeWicketContext.asStateFlow()

    private val _finishedOverSummary = MutableStateFlow<OverSummary?>(null)
    val finishedOverSummary: StateFlow<OverSummary?> = _finishedOverSummary.asStateFlow()

    val uiState: StateFlow<MatchUiState> = combine(
        _matchState,
        _isDarkMode,
        _bowlerNotification,
        _activeWicketContext,
        _isSyncEnabled,
        NearbyManager.connectedEndpoints,
        _finishedOverSummary
    ) { args ->
        MatchUiState(
            match = args[0] as Match?,
            isDarkMode = args[1] as Boolean?,
            bowlerNotification = args[2] as String?,
            activeWicketContext = args[3] as ActiveWicketContext?,
            isSyncEnabled = args[4] as Boolean,
            connectedDevicesCount = (args[5] as Set<*>).size,
            finishedOverSummary = args[6] as OverSummary?
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MatchUiState())

    private var pendingWicketBall: Ball? = null
    private var pendingDroppedCatchBall: Ball? = null
    private var lastNotifiedBowlerId: String? = null
    private val notifiedBowlerIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            TournamentRepository.tournaments.collect { tournaments ->
                val current = _matchState.value
                
                if (current != null) {
                    val tournament = tournaments.find { it.id == current.tournamentId }
                    if (tournament == null || tournament.matches.none { it.id == current.id }) {
                        _matchState.value = null
                    } else {
                        val masterTeamA = tournament.teams.find { it.id == current.teamA.id }
                        val updatedTeamA = if (masterTeamA != null) {
                            current.teamA.copy(
                                name = masterTeamA.name,
                                players = current.teamA.players.map { p ->
                                    val masterP = masterTeamA.players.find { it.id == p.id }
                                    if (masterP != null) p.copy(
                                        name = masterP.name,
                                        battingStyle = masterP.battingStyle,
                                        isCaptain = masterP.isCaptain,
                                        isViceCaptain = masterP.isViceCaptain
                                    ) else p
                                }
                            )
                        } else current.teamA

                        val masterTeamB = tournament.teams.find { it.id == current.teamB.id }
                        val updatedTeamB = if (masterTeamB != null) {
                            current.teamB.copy(
                                name = masterTeamB.name,
                                players = current.teamB.players.map { p ->
                                    val masterP = masterTeamB.players.find { it.id == p.id }
                                    if (masterP != null) p.copy(
                                        name = masterP.name,
                                        battingStyle = masterP.battingStyle,
                                        isCaptain = masterP.isCaptain,
                                        isViceCaptain = masterP.isViceCaptain
                                    ) else p
                                }
                            )
                        } else current.teamB

                        _matchState.update { it?.copy(teamA = updatedTeamA, teamB = updatedTeamB) }
                    }
                }
                
                if (_matchState.value == null) {
                    val liveMatch = tournaments.flatMap { it.matches }.find { it.status == MatchStatus.LIVE }
                    if (liveMatch != null) {
                        loadMatch(liveMatch)
                    }
                }
            }
        }

        NearbyManager.setMatchUpdateCallback { receivedMatch ->
            _matchState.value = receivedMatch
            TournamentRepository.updateMatch(receivedMatch.tournamentId ?: "", receivedMatch)
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
        val bowlingTeam = if (ScoringEngine.isTeamA(match.bowlingTeamId, match)) match.teamA else match.teamB
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
    }

    fun clearBowlerNotification() {
        _bowlerNotification.value = null
    }

    fun startSecondInnings() {
        val updated = _matchState.updateAndGet { current ->
            current?.copy(
                isSecondInningsStarted = true,
                innings2StartTimeMillis = System.currentTimeMillis(),
                pendingAction = PendingAction.NONE,
                strikerId = null,
                nonStrikerId = null,
                currentBowlerId = null,
                lastBowlerId = null // v2.33.13: Hard reset for new innings 🏏🚀⚖️🏅
            )
        }
        if (updated != null) {
            ScoringEngine.clearCache(updated.id)
            val finalized = ScoringEngine.recalculateMatchFromHistory(updated)
            _matchState.value = finalized
            TournamentRepository.updateMatch(finalized.tournamentId ?: "", finalized)
        }
    }

    fun swapStrike() {
        val current = _matchState.value ?: return
        if (current.strikerId == null || current.nonStrikerId == null) return
        
        val adjustment = Ball(runs = 0, isLegalBall = false, isAdjustment = true, adjustmentSlot = "SWAP")
        recordBall(adjustment)
    }

    fun forceChangeBowler() {
        _matchState.update { current ->
            if (current == null) return@update null
            current.copy(
                currentBowlerId = null,
                pendingAction = PendingAction.SELECT_BOWLER
            )
        }
    }

    fun loadMatch(match: Match) {
        ScoringEngine.clearCache(match.id)
        val recalculated = ScoringEngine.recalculateMatchFromHistory(match)
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
          
            val updatedMatch = current.copy(
                tossWinnerId = winnerId,
                tossDecision = decision,
                initialBattingTeamId = battingTeamId,
                initialBowlingTeamId = bowlingTeamId,
                battingTeamId = battingTeamId,
                bowlingTeamId = bowlingTeamId,
                status = MatchStatus.LIVE,
                pendingAction = PendingAction.NONE
            )
            ScoringEngine.recalculateMatchFromHistory(updatedMatch)
        }
        if (updated != null) {
            TournamentRepository.updateMatch(updated.tournamentId ?: "", updated)
        }
    }

    fun handleRuns(runs: Int, rotateStrike: Boolean = true) {
        val currentMatch = _matchState.value ?: return
        val ball = Ball(
            runs = runs,
            strikerId = currentMatch.strikerId ?: return,
            nonStrikerId = currentMatch.nonStrikerId ?: return,
            bowlerId = currentMatch.currentBowlerId,
            rotateStrike = rotateStrike
        )
        recordBall(ball)
    }

    fun handleExtra(type: ExtrasType, extraRuns: Int) {
        val currentMatch = _matchState.value ?: return
        val strikerId = currentMatch.strikerId ?: return
        val nonStrikerId = currentMatch.nonStrikerId ?: return
        
        
        val ball = when (type) {
            ExtrasType.NO_BALL -> {
                // ICC: Bat runs on No-ball are credited to striker, +1 penalty is extra
                Ball(
                    runs = extraRuns,
                    extrasType = type,
                    extraRuns = 1,
                    strikerId = strikerId,
                    nonStrikerId = nonStrikerId,
                    bowlerId = currentMatch.currentBowlerId,
                    isLegalBall = false
                )
            }
            ExtrasType.WIDE -> {
                // ICC: All runs on Wide (penalty + runs) are extras
                Ball(
                    runs = 0,
                    extrasType = type,
                    extraRuns = extraRuns + 1,
                    strikerId = strikerId,
                    nonStrikerId = nonStrikerId,
                    bowlerId = currentMatch.currentBowlerId,
                    isLegalBall = false
                )
            }
            ExtrasType.GRANTED -> {
                Ball(
                    runs = extraRuns,
                    extrasType = type,
                    extraRuns = 0,
                    strikerId = strikerId,
                    nonStrikerId = nonStrikerId,
                    bowlerId = currentMatch.currentBowlerId,
                    isLegalBall = true
                )
            }
            else -> { // BYE, LEG_BYE
                Ball(
                    runs = 0,
                    extrasType = type,
                    extraRuns = extraRuns,
                    strikerId = strikerId,
                    nonStrikerId = nonStrikerId,
                    bowlerId = currentMatch.currentBowlerId,
                    isLegalBall = true
                )
            }
        }
        recordBall(ball)
    }

    fun handleWicket(type: WicketType, victimId: String?) {
        val currentMatch = _matchState.value ?: return
        val strikerId = currentMatch.strikerId ?: return
        val nonStrikerId = currentMatch.nonStrikerId ?: return
        
        if (type == WicketType.RUN_OUT) {
            _activeWicketContext.value = ActiveWicketContext(
                type = type,
                initialStrikerId = strikerId,
                initialNonStrikerId = nonStrikerId,
                initialBowlerId = currentMatch.currentBowlerId ?: "",
                completedRuns = 0,
                brokenEnd = "STRIKER",
                expectedReplacementAction = PendingAction.NONE
            )
            _matchState.update { it?.copy(pendingAction = PendingAction.SELECT_RUNS_WICKET) }
            return
        }
        
        if (type == WicketType.CAUGHT) {
            _activeWicketContext.value = ActiveWicketContext(
                type = type,
                initialStrikerId = strikerId,
                initialNonStrikerId = nonStrikerId,
                initialBowlerId = currentMatch.currentBowlerId ?: "",
                completedRuns = 0,
                brokenEnd = "STRIKER",
                expectedReplacementAction = PendingAction.NONE
            )
            _matchState.update { it?.copy(pendingAction = PendingAction.SELECT_FIELDER) }
            return
        }

        val ball = Ball(
            runs = 0,
            wicketType = type,
            strikerId = strikerId,
            nonStrikerId = nonStrikerId,
            bowlerId = currentMatch.currentBowlerId,
            outPlayerId = victimId,
            isLegalBall = type != WicketType.RETIRED_HURT
        )
        recordBall(ball)
    }

    fun handleDoubleRetire() {
        val current = _matchState.value ?: return
        val sId = current.strikerId ?: return
        val nsId = current.nonStrikerId ?: return
        
        val ball1 = Ball(runs = 0, wicketType = WicketType.RETIRED_HURT, strikerId = sId, nonStrikerId = nsId, bowlerId = current.currentBowlerId, isLegalBall = false)
        val ball2 = Ball(runs = 0, wicketType = WicketType.RETIRED_HURT, strikerId = sId, nonStrikerId = nsId, bowlerId = current.currentBowlerId, outPlayerId = nsId, isLegalBall = false)
        
        _matchState.update { state ->
            if (state == null) return@update null
            val updatedHistory = state.ballHistory + ball1 + ball2
            val intermediate = state.copy(ballHistory = updatedHistory, strikerId = null, nonStrikerId = null)
            ScoringEngine.recalculateMatchFromHistory(intermediate)
        }
        _matchState.value?.let { TournamentRepository.updateMatch(it.tournamentId ?: "", it) }
    }

    fun handleDroppedCatch() {
        _matchState.update { it?.copy(pendingAction = PendingAction.SELECT_FIELDER_DROPPED_CATCH) }
    }

    fun handleRunsForDroppedCatch(runs: Int, rotate: Boolean) {
        pendingDroppedCatchBall?.let {
            val finalBall = it.copy(runs = runs, rotateStrike = rotate)
            recordBall(finalBall)
            pendingDroppedCatchBall = null
        }
    }

    fun handleRunOutWicket(runs: Int, outId: String, rotate: Boolean, fielderId: String?) {
        val context = _activeWicketContext.value ?: return
        val ball = Ball(
            runs = runs, wicketType = WicketType.RUN_OUT,
            strikerId = context.initialStrikerId, nonStrikerId = context.initialNonStrikerId,
            bowlerId = context.initialBowlerId, outPlayerId = outId,
            rotateStrike = rotate, fielderId = fielderId
        )
        recordBall(ball)
        _activeWicketContext.value = null
    }

    fun selectFielder(fielderId: String) {
        val currentMatch = _matchState.value ?: return
        val action = currentMatch.pendingAction
        
        if (action == PendingAction.SELECT_FIELDER_DROPPED_CATCH) {
            val ball = Ball(
                runs = 0, strikerId = currentMatch.strikerId, nonStrikerId = currentMatch.nonStrikerId,
                bowlerId = currentMatch.currentBowlerId, isDroppedCatch = true, fielderId = fielderId
            )
            pendingDroppedCatchBall = ball
            _matchState.update { it?.copy(pendingAction = PendingAction.SELECT_RUNS_DROPPED_CATCH) }
        } else if (action == PendingAction.SELECT_FIELDER) {
            val context = _activeWicketContext.value ?: return
            
            if (context.type == WicketType.CAUGHT) {
                val ball = Ball(
                    runs = 0, wicketType = WicketType.CAUGHT,
                    strikerId = context.initialStrikerId, nonStrikerId = context.initialNonStrikerId,
                    bowlerId = context.initialBowlerId, fielderId = fielderId
                )
                recordBall(ball)
                _activeWicketContext.value = null
            } else {
                _activeWicketContext.value = context.copy(dismissalFielderId = fielderId)
                _matchState.update { it?.copy(pendingAction = PendingAction.SELECT_RUNS_WICKET) }
            }
        }
    }

    fun assignPlayerToAction(playerId: String) {
        val current = _matchState.value ?: return
        val action = current.pendingAction ?: PendingAction.NONE
        
        val isManualSub = action == PendingAction.REPLACE_STRIKER || 
                          action == PendingAction.REPLACE_NON_STRIKER || 
                          action == PendingAction.REPLACE_BOWLER

        val adjustment = when (action) {
            PendingAction.SELECT_STRIKER, PendingAction.REPLACE_STRIKER -> 
                Ball(runs = 0, isLegalBall = false, isAdjustment = true, adjustmentSlot = "STRIKER", adjustmentPlayerId = playerId, isReplacement = isManualSub)
            PendingAction.SELECT_NON_STRIKER, PendingAction.REPLACE_NON_STRIKER -> 
                Ball(runs = 0, isLegalBall = false, isAdjustment = true, adjustmentSlot = "NON_STRIKER", adjustmentPlayerId = playerId, isReplacement = isManualSub)
            PendingAction.SELECT_BOWLER, PendingAction.REPLACE_BOWLER -> 
                Ball(runs = 0, isLegalBall = false, isAdjustment = true, adjustmentSlot = "BOWLER", adjustmentPlayerId = playerId, isReplacement = isManualSub)
            else -> null
        }

        if (adjustment != null) {
            recordBall(adjustment)
            if (action == PendingAction.REPLACE_STRIKER || action == PendingAction.REPLACE_NON_STRIKER) {
                _activeWicketContext.value = null
            }
        } else if (action == PendingAction.SELECT_WK_A || action == PendingAction.SELECT_WK_B) {
            _matchState.update { m ->
                if (action == PendingAction.SELECT_WK_A) m?.copy(teamAWicketKeeperId = playerId)
                else m?.copy(teamBWicketKeeperId = playerId)
            }
        }
    }

    fun editBall(index: Int, updatedBall: Ball) {
        _matchState.update { current ->
            if (current == null || index < 0 || index >= current.ballHistory.size) return@update current
            val newHistory = current.ballHistory.toMutableList()
            newHistory[index] = updatedBall
            
            val updatedMatch = current.copy(
                ballHistory = newHistory,
                strikerId = null,
                nonStrikerId = null,
                currentBowlerId = null
            )
            ScoringEngine.recalculateMatchFromHistory(updatedMatch)
        }
        _matchState.value?.let { 
            TournamentRepository.updateMatch(it.tournamentId ?: "", it)
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

    fun cancelPendingAction() {
        _matchState.update { current ->
            if (current == null) return@update null
            current.copy(pendingAction = PendingAction.NONE)
        }
    }

    fun dismissOverSummary() {
        _finishedOverSummary.value = null
    }

    fun changeWicketKeeper() {
        _matchState.update { current ->
            if (current == null) return@update null
            val action = if (ScoringEngine.isTeamA(current.battingTeamId, current)) PendingAction.SELECT_WK_B else PendingAction.SELECT_WK_A
            current.copy(pendingAction = action)
        }
    }

    private fun recordBall(ball: Ball) {
        val currentMatch = _matchState.value
        if (currentMatch != null && currentMatch.status == MatchStatus.COMPLETED && !ball.isAdjustment) {
            return
        }
        
        var notificationText: String? = null
        var bowlerToNotifyId: String? = null

        val finalizedMatch = _matchState.updateAndGet { current ->
            if (current == null) return@updateAndGet null
            
            val matchWithTime = if (current.startTimeMillis == null) {
                current.copy(startTimeMillis = System.currentTimeMillis())
            } else current
            
            var updatedMatch = matchWithTime.copy(ballHistory = matchWithTime.ballHistory + ball)
            
            if (ball.wicketType == WicketType.RETIRED_HURT) {
                val outId = ball.outPlayerId ?: ball.strikerId
                if (updatedMatch.strikerId == outId) updatedMatch = updatedMatch.copy(strikerId = null)
                if (updatedMatch.nonStrikerId == outId) updatedMatch = updatedMatch.copy(nonStrikerId = null)
            }

            val result = ScoringEngine.recalculateMatchFromHistory(updatedMatch)
            
            var transitionMatch = result
            if (current.currentInnings == 1 && result.currentInnings == 2 && result.innings1EndTimeMillis == null) {
                transitionMatch = transitionMatch.copy(innings1EndTimeMillis = System.currentTimeMillis())
            }
            if (current.status == MatchStatus.LIVE && result.status == MatchStatus.COMPLETED && result.endTimeMillis == null) {
                transitionMatch = transitionMatch.copy(endTimeMillis = System.currentTimeMillis())
            }
            
            val finalResult = ScoringEngine.recalculateMatchFromHistory(transitionMatch)
            
            if (finalResult.status == MatchStatus.LIVE && ball.isLegalBall && finalResult.totalBalls % 6 == 0 && _bowlerNotification.value == null) {
                if (finalResult.pendingAction == PendingAction.START_SECOND_INNINGS) {
                    // v2.33.4: Clear over summary if innings ended to prevent overlay overlap 🏏🚀⚖️🏅
                    _finishedOverSummary.value = null
                } else {
                    // v2.33.2: Over Completion Summary 🏏🚀⚖️🏅
                    val lastOverBalls = mutableListOf<Ball>()
                    var physicalCount = 0
                    for (b in finalResult.ballHistory.reversed()) {
                        if (b.isAdjustment) continue
                        lastOverBalls.add(b)
                        if (b.isPhysicalBall) physicalCount++
                        if (physicalCount == 6) break
                    }
                    val runs = lastOverBalls.sumOf { it.runs + it.extraRuns }
                    val wickets = lastOverBalls.count { it.wicketType != WicketType.NONE && it.wicketType != WicketType.RETIRED_HURT }
                    
                    val labels = lastOverBalls.map { b ->
                        when {
                            b.wicketType == WicketType.RETIRED_HURT -> "RH"
                            b.wicketType != WicketType.NONE -> "W"
                            b.extrasType == ExtrasType.WIDE -> "${b.extraRuns}wd"
                            b.extrasType == ExtrasType.NO_BALL -> {
                                val total = b.runs + b.extraRuns
                                if (total > 0) "${total}nb" else "nb"
                            }
                            b.extrasType == ExtrasType.BYE -> "${b.extraRuns}b"
                            b.extrasType == ExtrasType.LEG_BYE -> "${b.extraRuns}lb"
                            b.extrasType == ExtrasType.GRANTED -> "${b.runs}G"
                            else -> "${b.runs}"
                        }
                    }

                    val bTeam = if (ScoringEngine.isTeamA(finalResult.battingTeamId, finalResult)) finalResult.teamA else finalResult.teamB
                    _finishedOverSummary.value = OverSummary(
                        overNumber = finalResult.totalBalls / 6,
                        runs = runs,
                        wickets = wickets,
                        ballLabels = labels.reversed(), // Reverse back to chronological order 🏏🚀⚖️🏅
                        teamTotalRuns = finalResult.totalRuns,
                        teamTotalWickets = finalResult.totalWickets,
                        battingTeamName = bTeam.name
                    )
                }

                val bowlingTeam = if (ScoringEngine.isTeamA(finalResult.bowlingTeamId, finalResult)) finalResult.teamA else finalResult.teamB
                val ballBowlerId = ball.bowlerId ?: ""
                val bPlayer = bowlingTeam.players.find { it.id == ballBowlerId }
                if (bPlayer != null && finalResult.maxOversPerBowler != null && finalResult.lastNotifiedBowlerId != bPlayer.id) {
                    val base = finalResult.maxOversPerBowler!!
                    val qLimit = finalResult.quotaMaxOvers ?: base
                    val qCount = finalResult.quotaBowlersCount ?: 0
                    
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
            finalResult
        }

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
            
            if (bowlerToNotifyId != null) {
                _matchState.update { finalWithNotification }
            }
        }
    }

    fun updateMatchSettings(newOvers: Int, newMaxOvers: Int?, newQuotaCount: Int? = null, newQuotaLimit: Int? = null) {
        val updated = _matchState.updateAndGet { current ->
            if (current == null) return@updateAndGet null
          
            val base = current.copy(
                oversPerInnings = newOvers, 
                maxOversPerBowler = newMaxOvers,
                quotaBowlersCount = newQuotaCount,
                quotaMaxOvers = newQuotaLimit,
                pendingAction = PendingAction.NONE
            )
            ScoringEngine.recalculateMatchFromHistory(base)
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

    fun undo(context: Context) {
        _matchState.update { current ->
            if (current == null || current.ballHistory.isEmpty()) {
                Toast.makeText(context, "Nothing to undo! 🤷‍♂️", Toast.LENGTH_SHORT).show()
                return@update current
            }
            
            val newHistory = current.ballHistory.dropLast(1)
            
            val base = current.copy(
                ballHistory = newHistory,
                strikerId = null,
                nonStrikerId = null,
                currentBowlerId = null,
                status = MatchStatus.LIVE,
                winnerId = null,
                endTimeMillis = null
            )
            
            val result = ScoringEngine.recalculateMatchFromHistory(base)
            Toast.makeText(context, "Undo successful! ↩️", Toast.LENGTH_SHORT).show()
            result
        }
        
        _matchState.value?.let { 
            TournamentRepository.updateMatch(it.tournamentId ?: "", it)
        }
    }

    fun addNewPlayerToMatch(context: Context, playerName: String, battingStyle: BattingStyle, teamId: String) {
        val current = _matchState.value ?: return
        val newId = java.util.UUID.randomUUID().toString()
        val newPlayer = Player(id = newId, name = playerName.trim(), battingStyle = battingStyle)

        // 1. Update Repository (Async)
        TournamentRepository.addPlayerToTeam(current.tournamentId ?: "", teamId, playerName, battingStyle)
        
        // 2. Update Local State (Immediate UI sync) 🏏🚀⚖️🏅
        _matchState.update { state ->
            if (state == null) return@update null
            
            val updatedTeamA = if (state.teamA.id == teamId) {
                state.teamA.copy(players = state.teamA.players + newPlayer)
            } else state.teamA

            val updatedTeamB = if (state.teamB.id == teamId) {
                state.teamB.copy(players = state.teamB.players + newPlayer)
            } else state.teamB

            val updatedMatch = state.copy(teamA = updatedTeamA, teamB = updatedTeamB)
            ScoringEngine.clearCache(state.id)
            val finalMatch = ScoringEngine.recalculateMatchFromHistory(updatedMatch)
            TournamentRepository.updateMatch(finalMatch.tournamentId ?: "", finalMatch)
            finalMatch
        }
    }

    fun addGlobalPlayersToMatch(context: Context, players: List<Player>, teamId: String) {
        val current = _matchState.value ?: return
        
        // 1. Update Repository (Async)
        TournamentRepository.addPlayersToTeam(current.tournamentId ?: "", teamId, players)
        
        // 2. Update Local State (Immediate UI sync) 🏏🚀⚖️🏅
        _matchState.update { state ->
            if (state == null) return@update null
            
            val updatedTeamA = if (state.teamA.id == teamId) {
                state.teamA.copy(players = state.teamA.players + players)
            } else state.teamA

            val updatedTeamB = if (state.teamB.id == teamId) {
                state.teamB.copy(players = state.teamB.players + players)
            } else state.teamB

            val updatedMatch = state.copy(teamA = updatedTeamA, teamB = updatedTeamB)
            ScoringEngine.clearCache(state.id)
            val finalMatch = ScoringEngine.recalculateMatchFromHistory(updatedMatch)
            TournamentRepository.updateMatch(finalMatch.tournamentId ?: "", finalMatch)
            finalMatch
        }
    }

    fun deletePlayerFromMatch(context: Context, playerId: String) {
        val currentMatch = _matchState.value ?: return
        
        // v2.33.8: Prevent removing active players 🏏🚀⚖️🏅
        if (playerId == currentMatch.strikerId || playerId == currentMatch.nonStrikerId || playerId == currentMatch.currentBowlerId) {
            Toast.makeText(context, "Cannot remove active player! 🚫", Toast.LENGTH_SHORT).show()
            return
        }

        val teamId = if (currentMatch.teamA.players.any { it.id == playerId }) currentMatch.teamA.id else currentMatch.teamB.id
        
        TournamentRepository.deletePlayer(currentMatch.tournamentId.orEmpty(), teamId, playerId)

        _matchState.update { current ->
            if (current == null) return@update null
            
            val updatedMatch = current.safeCopy().copy(
                teamA = current.teamA.safeCopy().copy(players = current.teamA.players.orEmpty().filterNotNull().filter { it.id != playerId }),
                teamB = current.teamB.safeCopy().copy(players = current.teamB.players.orEmpty().filterNotNull().filter { it.id != playerId }),
                teamACaptainId = if (current.teamACaptainId == playerId) null else current.teamACaptainId,
                teamBCaptainId = if (current.teamBCaptainId == playerId) null else current.teamBCaptainId,
                teamAWicketKeeperId = if (current.teamAWicketKeeperId == playerId) null else current.teamAWicketKeeperId,
                teamBWicketKeeperId = if (current.teamBWicketKeeperId == playerId) null else current.teamBWicketKeeperId
            )

            ScoringEngine.clearCache(current.id)
            val finalMatch = ScoringEngine.recalculateMatchFromHistory(updatedMatch)
            TournamentRepository.updateMatch(finalMatch.tournamentId ?: "", finalMatch)
            finalMatch
        }
    }

    fun clearIfDeleted(matchId: String) {
        if (_matchState.value?.id == matchId) {
            _matchState.value = null
        }
    }

    fun deleteMatch(tournamentId: String, matchId: String) {
        TournamentRepository.deleteMatch(tournamentId, matchId)
        clearIfDeleted(matchId)
    }

    fun updatePlayerInMatch(playerId: String, newName: String, bStyle: BattingStyle, isCaptain: Boolean, isViceCaptain: Boolean) {
        val current = _matchState.value ?: return
        val teamId = if (current.teamA.players.any { it.id == playerId }) current.teamA.id else current.teamB.id
        
        TournamentRepository.updatePlayerDetails(current.tournamentId ?: "", teamId, playerId, newName, bStyle, isCaptain, isViceCaptain)
        
        ScoringEngine.clearCache(current.id)
        val updated = ScoringEngine.recalculateMatchFromHistory(_matchState.value!!)
        _matchState.value = updated
        TournamentRepository.updateMatch(updated.tournamentId ?: "", updated)
    }
}


