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

    private val _activeWicketContext = MutableStateFlow<ActiveWicketContext?>(null)
    val activeWicketContext: StateFlow<ActiveWicketContext?> = _activeWicketContext.asStateFlow()

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
                    // v2.19 (Engine Hardened & Draw Fix) 🏏🚀⚖️🏅: Match Deletion Sync - Clear live state if match is gone
                    if (tournament == null || tournament.matches.none { it.id == current.id }) {
                        _matchState.value = null
                    } else {
                        // v2.19 (Engine Hardened & Draw Fix) 🏏🚀⚖️🏅: Enhanced Player Sync - Refresh metadata ONLY, keep current stats
                        val masterTeamA = tournament.teams.find { it.id == current.teamA.id }
                        val updatedTeamA = if (masterTeamA != null) {
                            current.teamA.copy(
                                name = masterTeamA.name,
                                players = current.teamA.players.map { p ->
                                    val masterP = masterTeamA.players.find { it.id == p.id }
                                    if (masterP != null) p.copy(
                                        name = masterP.name,
                                        battingStyle = masterP.battingStyle,
                                        bowlingStyle = masterP.bowlingStyle,
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
                                        bowlingStyle = masterP.bowlingStyle,
                                        isCaptain = masterP.isCaptain,
                                        isViceCaptain = masterP.isViceCaptain
                                    ) else p
                                }
                            )
                        } else current.teamB

                        _matchState.update { it?.copy(teamA = updatedTeamA, teamB = updatedTeamB) }
                    }
                }
                
                // v2.19 (Engine Hardened & Draw Fix) 🏏🚀⚖️🏅: Auto-load LIVE match if current is null
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
        val bowlingTeam = if (isTeamA(match.bowlingTeamId, match)) match.teamA else match.teamB
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
                innings2StartTimeMillis = System.currentTimeMillis(), // Track start of 2nd innings
                pendingAction = PendingAction.NONE,
                strikerId = null,
                nonStrikerId = null,
                currentBowlerId = null
            )
        }
        if (updated != null) {
            // v2.19 (Engine Hardened & Draw Fix) 🏏🚀⚖️🏅: Definitive 2nd Innings Initialization
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
            
            // v2.19 (Engine Hardened & Draw Fix) 🏏🚀⚖️🏅 Clean Toss Implementation
            val updatedMatch = current.copy(
                tossWinnerId = winnerId,
                tossDecision = decision,
                initialBattingTeamId = battingTeamId,
                initialBowlingTeamId = bowlingTeamId,
                battingTeamId = battingTeamId,
                bowlingTeamId = bowlingTeamId,
                status = MatchStatus.LIVE,
                pendingAction = PendingAction.SELECT_MATCH_SETTINGS
            )
            recalculateMatchFromHistory(updatedMatch)
        }
        if (updated != null) {
            TournamentRepository.updateMatch(updated.tournamentId ?: "", updated)
        }
    }

    fun handleRuns(runs: Int, rotateStrike: Boolean = true) {
        val currentMatch = _matchState.value ?: return
        if (currentMatch.pendingAction != PendingAction.NONE) return // Safeguard v2.19 (Engine Hardened & Draw Fix) 🏏🚀⚖️🏅

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
        if (currentMatch.pendingAction != PendingAction.NONE) return // Safeguard v2.19 (Engine Hardened & Draw Fix) 🏏🚀⚖️🏅
        
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
                extraRuns = 1 + additionalRuns,
                strikerId = currentMatch.strikerId ?: return,
                nonStrikerId = currentMatch.nonStrikerId ?: return,
                bowlerId = currentMatch.currentBowlerId ?: return,
                isLegalBall = false,
                rotateStrike = (additionalRuns % 2 != 0)
            )
            ExtrasType.GRANTED -> Ball(
                runs = additionalRuns,
                extrasType = type,
                extraRuns = 0,
                strikerId = currentMatch.strikerId ?: return,
                nonStrikerId = currentMatch.nonStrikerId ?: return,
                bowlerId = currentMatch.currentBowlerId ?: return,
                isLegalBall = true, // Updated: 1G counts as a ball per TASK 5 instructions
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
                    isLegalBall = isLegal,
                    rotateStrike = additionalRuns % 2 != 0
                )
            }
        }
        recordBall(ball)
    }

    fun handleWicket(type: WicketType = WicketType.BOWLED, outPlayerId: String? = null) {
        val currentMatch = _matchState.value ?: return
        if (currentMatch.pendingAction != PendingAction.NONE) return 
        
        val strikerId = currentMatch.strikerId ?: return
        val nonStrikerId = currentMatch.nonStrikerId ?: return
        val bowlerId = currentMatch.currentBowlerId ?: return
        
        val finalOutPlayerId = outPlayerId ?: strikerId
        
        val ball = Ball(
            runs = 0,
            wicketType = type,
            strikerId = strikerId,
            nonStrikerId = nonStrikerId,
            bowlerId = bowlerId,
            outPlayerId = finalOutPlayerId,
            isLegalBall = type != WicketType.RETIRED_HURT
        )

        // Initialize spatial baseline for multi-step workflows v2.26 🏏🚀⚖️🏅
        if (type == WicketType.RUN_OUT || type == WicketType.CAUGHT || type == WicketType.STUMPED) {
            _activeWicketContext.value = ActiveWicketContext(
                type = type,
                initialStrikerId = strikerId,
                initialNonStrikerId = nonStrikerId,
                initialBowlerId = bowlerId
            )
            pendingWicketBall = ball
            
            if (type == WicketType.RUN_OUT) {
                _matchState.update { it?.copy(pendingAction = PendingAction.SELECT_RUNS_WICKET) }
            } else {
                _matchState.update { it?.copy(pendingAction = PendingAction.SELECT_FIELDER) }
            }
        } else {
            recordBall(ball)
        }
    }

    fun handleDroppedCatch() {
        _matchState.update { it?.copy(pendingAction = PendingAction.SELECT_FIELDER_DROPPED_CATCH) }
    }

    fun handleRunsForDroppedCatch(runs: Int, rotateStrike: Boolean) {
        val ball = pendingDroppedCatchBall ?: return
        val updatedBall = ball.copy(runs = runs, rotateStrike = rotateStrike)
        pendingDroppedCatchBall = null
        recordBall(updatedBall)
    }

    fun handleRunOutWicket(runs: Int, brokenEnd: String, reason: String?) {
        val context = _activeWicketContext.value ?: return
        
        // Physics-based victim identification: Victim is the one running TOWARD the broken end.
        val victimId = if (brokenEnd == "BOWLER_CREASE") {
            if (runs % 2 == 0) context.initialStrikerId else context.initialNonStrikerId
        } else {
            if (runs % 2 == 0) context.initialNonStrikerId else context.initialStrikerId
        }
        
        _activeWicketContext.update { it?.copy(completedRuns = runs, brokenEnd = brokenEnd, dismissalReason = reason, calculatedVictimId = victimId) }
        _matchState.update { it?.copy(pendingAction = PendingAction.SELECT_FIELDER) }
    }

    fun selectFielder(fielderId: String) {
        val currentMatch = _matchState.value ?: return
        val currentAction = currentMatch.pendingAction ?: PendingAction.NONE
        
        if (currentAction == PendingAction.SELECT_FIELDER) {
            val ball = pendingWicketBall ?: return
            val context = _activeWicketContext.value
            
            val updatedBall = if (context != null) {
                ball.copy(
                    fielderId = fielderId,
                    runs = context.completedRuns,
                    outPlayerId = context.calculatedVictimId ?: ball.outPlayerId,
                    dismissalReason = context.dismissalReason
                )
            } else {
                ball.copy(fielderId = fielderId)
            }

            pendingWicketBall = null
            recordBall(updatedBall)
            _activeWicketContext.value = null // Ensure cleanup
            
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

    fun assignPlayerToAction(playerId: String) {
        val updated = _matchState.updateAndGet { current ->
            if (current == null) return@updateAndGet null
            
            val action = current.pendingAction ?: PendingAction.NONE
            val withSelection = when (action) {
                PendingAction.SELECT_STRIKER, PendingAction.REPLACE_STRIKER -> {
                    val newOrder = current.battingOrder.toMutableList()
                    if (!newOrder.contains(playerId)) newOrder.add(playerId)
                    current.copy(strikerId = playerId, battingOrder = newOrder)
                }
                PendingAction.SELECT_NON_STRIKER, PendingAction.REPLACE_NON_STRIKER -> {
                    val newOrder = current.battingOrder.toMutableList()
                    if (!newOrder.contains(playerId)) newOrder.add(playerId)
                    current.copy(nonStrikerId = playerId, battingOrder = newOrder)
                }
                PendingAction.SELECT_BOWLER, PendingAction.REPLACE_BOWLER -> {
                    current.copy(currentBowlerId = playerId)
                }
                PendingAction.SELECT_WK_A -> current.copy(teamAWicketKeeperId = playerId)
                PendingAction.SELECT_WK_B -> current.copy(teamBWicketKeeperId = playerId)
                else -> current
            }
            
            // Clear workflow context on successful substitution
            if (action == PendingAction.REPLACE_STRIKER || action == PendingAction.REPLACE_NON_STRIKER) {
                _activeWicketContext.value = null
            }

            // targeted recalculation ensures engine determines the NEXT gap
            val result = recalculateMatchFromHistory(withSelection)
            result
        }
        if (updated != null && (updated.pendingAction ?: PendingAction.NONE) == PendingAction.NONE) {
            TournamentRepository.updateMatch(updated.tournamentId ?: "", updated)
        }
    }

    fun editBall(index: Int, updatedBall: Ball) {
        _matchState.update { current ->
            if (current == null || index < 0 || index >= current.ballHistory.size) return@update current
            val newHistory = current.ballHistory.toMutableList()
            newHistory[index] = updatedBall
            
            // v2.26.68: Clear end-state pointers to force fresh derivation from the edited history 🏏🚀⚖️🏅
            val updatedMatch = current.copy(
                ballHistory = newHistory,
                strikerId = null,
                nonStrikerId = null,
                currentBowlerId = null
            )
            recalculateMatchFromHistory(updatedMatch)
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
            // v2.26.75: Clear action AND trigger recalculation to find next logical requirement 🏏🚀⚖️🏅
            val cleared = current.copy(pendingAction = PendingAction.NONE)
            recalculateMatchFromHistory(cleared)
        }
        _activeWicketContext.value = null
        pendingWicketBall = null
        pendingDroppedCatchBall = null
    }

    fun changeWicketKeeper() {
        _matchState.update { current ->
            if (current == null) return@update null
            val action = if (isTeamA(current.bowlingTeamId, current)) PendingAction.SELECT_WK_A else PendingAction.SELECT_WK_B
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
            
            // v2.26.67: Track innings and match completion times 🏏🚀⚖️🏅
            var transitionMatch = result
            if (current.currentInnings == 1 && result.currentInnings == 2 && result.innings1EndTimeMillis == null) {
                transitionMatch = transitionMatch.copy(innings1EndTimeMillis = System.currentTimeMillis())
            }
            if (current.status == MatchStatus.LIVE && result.status == MatchStatus.COMPLETED && result.endTimeMillis == null) {
                transitionMatch = transitionMatch.copy(endTimeMillis = System.currentTimeMillis())
            }
            
            val finalResult = recalculateMatchFromHistory(transitionMatch) // Final pass to bake in durations
            
            // v2.19 (Engine Hardened & Draw Fix) 🏏🚀⚖️🏅: Prepare spell notification logic (Side-effect free preparation)
            if (finalResult.status == MatchStatus.LIVE && ball.isLegalBall && finalResult.totalBalls % 6 == 0 && _bowlerNotification.value == null) {
                val bowlingTeam = if (isTeamA(finalResult.bowlingTeamId, finalResult)) finalResult.teamA else finalResult.teamB
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


    private fun healLegacyId(id: String?, team: Team): String? {
        if (id.isNullOrEmpty()) return id
        val isLikelyUuid = id.length >= 32 && !id.contains(" ")
        if (isLikelyUuid) return id
        return team.players.find { it.name.trim().equals(id.trim(), ignoreCase = true) }?.id ?: id
    }

    private fun isTeamA(idOrName: String?, m: Match): Boolean {
        if (idOrName.isNullOrEmpty()) return false
        return idOrName == m.teamA.id || idOrName.trim().equals(m.teamA.name.trim(), ignoreCase = true)
    }

    private fun recalculateMatchFromHistory(match: Match): Match {
        if (match.tossWinnerId == null) {
            return match.copy(pendingAction = PendingAction.TOSS_REQUIRED)
        }

        // 1. Initial State Baseline v2.26 Critical Physics Engine 🏏🚀⚖️🏅
        val teamABatsFirst = if (match.tossWinnerId == match.teamA.id) match.tossDecision == "BAT" else match.tossDecision == "BOWL"
        val innings1BattingTeamId = if (teamABatsFirst) match.teamA.id else match.teamB.id
        val innings1BowlingTeamId = if (innings1BattingTeamId == match.teamA.id) match.teamB.id else match.teamA.id

        var current = match.copy(
            totalRuns = 0, totalWickets = 0, totalBalls = 0,
            wideCount = 0, noBallCount = 0, byeCount = 0, legByeCount = 0,
            wicketHistory = emptyList(), battingOrder = emptyList(),
            teamA = resetTeamStats(match.teamA), teamB = resetTeamStats(match.teamB),
            status = MatchStatus.LIVE, currentInnings = 1,
            battingTeamId = innings1BattingTeamId, bowlingTeamId = innings1BowlingTeamId,
            strikerId = null, nonStrikerId = null, currentBowlerId = null, lastBowlerId = null,
            pendingAction = PendingAction.NONE
        )

        var ballsInOver = 0
        var itemsProcessed = 0
        
            // 2. Event Processing Loop (Historical derive)
        match.ballHistory.forEach { ball ->
            itemsProcessed++
            if (current.status == MatchStatus.COMPLETED) return@forEach
            
            val isBattingA = isTeamA(current.battingTeamId, current)
            val battingTeam = if (isBattingA) current.teamA else current.teamB
            val bowlingTeam = if (isBattingA) current.teamB else current.teamA

            // ID Healing for safe matching across devices/versions
            val healedBall = ball.copy(
                strikerId = healLegacyId(ball.strikerId, battingTeam),
                nonStrikerId = healLegacyId(ball.nonStrikerId, battingTeam),
                bowlerId = healLegacyId(ball.bowlerId, bowlingTeam),
                outPlayerId = healLegacyId(ball.outPlayerId, battingTeam)
            )

            // Update Batting Order
            val newBattingOrder = current.battingOrder.toMutableList()
            healedBall.strikerId?.let { if (it.isNotEmpty() && !newBattingOrder.contains(it)) newBattingOrder.add(it) }
            healedBall.nonStrikerId?.let { if (it.isNotEmpty() && !newBattingOrder.contains(it)) newBattingOrder.add(it) }
            val outId = healedBall.outPlayerId ?: (if (healedBall.wicketType != WicketType.NONE && healedBall.wicketType != WicketType.RETIRED_HURT) healedBall.strikerId else null)
            if (!outId.isNullOrEmpty() && !newBattingOrder.contains(outId)) newBattingOrder.add(outId)

            // Update Team Totals & Individual Stats
            current = current.copy(
                totalRuns = current.totalRuns + healedBall.runs + healedBall.extraRuns,
                totalWickets = current.totalWickets + (if (healedBall.wicketType != WicketType.NONE && healedBall.wicketType != WicketType.RETIRED_HURT) 1 else 0),
                totalBalls = current.totalBalls + (if (healedBall.isLegalBall) 1 else 0),
                wideCount = current.wideCount + (if (healedBall.extrasType == ExtrasType.WIDE) healedBall.extraRuns else 0),
                noBallCount = current.noBallCount + (if (healedBall.extrasType == ExtrasType.NO_BALL) healedBall.extraRuns else 0),
                byeCount = current.byeCount + (if (healedBall.extrasType == ExtrasType.BYE) healedBall.extraRuns else 0),
                legByeCount = current.legByeCount + (if (healedBall.extrasType == ExtrasType.LEG_BYE) healedBall.extraRuns else 0),
                battingOrder = newBattingOrder,
                teamA = updateTeamStats(current.teamA, healedBall, isBattingA, !isBattingA),
                teamB = updateTeamStats(current.teamB, healedBall, !isBattingA, isBattingA)
            )

            // Record Wicket History
            if (healedBall.wicketType != WicketType.NONE && healedBall.wicketType != WicketType.RETIRED_HURT) {
                val outName = battingTeam.players.find { it.id == outId }?.name ?: "Unknown"
                val bName = bowlingTeam.players.find { it.id == healedBall.bowlerId }?.name
                val fName = bowlingTeam.players.find { it.id == healedBall.fielderId }?.name
                current = current.copy(wicketHistory = current.wicketHistory + WicketRecord(current.totalWickets, "☝️ $outName", current.totalRuns, "${current.totalBalls/6}.${current.totalBalls%6}", healedBall.wicketType, bName, fName, healedBall.dismissalReason))
            }

            if (healedBall.isLegalBall) ballsInOver++

            // Spatial Tracking Engine v2.26 🏏🚀⚖️🏅
            var sId = current.strikerId ?: healedBall.strikerId
            var nsId = current.nonStrikerId ?: healedBall.nonStrikerId
            val bId = healedBall.bowlerId
            var lbId = current.lastBowlerId

            val physicalRuns = if (healedBall.extrasType == ExtrasType.WIDE) (healedBall.extraRuns - 1).coerceAtLeast(0) 
                               else if (healedBall.extrasType == ExtrasType.BYE || healedBall.extrasType == ExtrasType.LEG_BYE) healedBall.extraRuns 
                               else healedBall.runs
            
            val isCaught = healedBall.wicketType == WicketType.CAUGHT
            val is1G = healedBall.extrasType == ExtrasType.GRANTED

            // A. Run-Based Strike Rotation (Skip for 1G as per requirements)
            if (!is1G && healedBall.rotateStrike && physicalRuns % 2 != 0) {
                val t = sId; sId = nsId; nsId = t
            }

            // B. Dismissal Physics (Crease Clearing)
            if (healedBall.wicketType != WicketType.NONE) {
                val victimId = healedBall.outPlayerId ?: healedBall.strikerId
                if (isCaught) {
                    sId = null // ICC 2022: New batter takes striker end
                } else {
                    if (sId == victimId) sId = null
                    else if (nsId == victimId) nsId = null
                }
            }

            // C. Over-End Logic (Happens AFTER all ball physics)
            if (ballsInOver == 6) {
                val t = sId; sId = nsId; nsId = t // Mandatory swap
                lbId = bId; ballsInOver = 0
            }
            
            // v2.26.68: Precise Current Bowler Tracking. 
            // In recalculation, mid-over currentBowlerId is always the bowler of the LAST ball processed.
            current = current.copy(strikerId = sId, nonStrikerId = nsId, currentBowlerId = if (ballsInOver == 0) null else healedBall.bowlerId, lastBowlerId = lbId)

            // Innings Completion Logic
            val inningsEnded = current.totalWickets >= (battingTeam.players.size - 1).coerceAtLeast(1) || current.totalBalls >= current.oversPerInnings * 6
            
            if (current.currentInnings == 1 && inningsEnded) {
                // v2.26.67: Accurate duration calculation 🏏🚀⚖️🏅
                val i1EndTime = match.innings1EndTimeMillis ?: System.currentTimeMillis()
                val i1StartTime = match.startTimeMillis ?: i1EndTime
                val i1Duration = ((i1EndTime - i1StartTime) / 60000).toInt().coerceAtLeast(0)

                current = current.copy(
                    innings1Data = InningsSummary(current.totalRuns, current.totalWickets, current.totalBalls, current.battingTeamId, current.wicketHistory, current.wideCount, current.noBallCount, current.byeCount, current.legByeCount, itemsProcessed, i1Duration, current.battingOrder),
                    currentInnings = 2, target = current.totalRuns + 1, battingTeamId = current.bowlingTeamId, bowlingTeamId = current.battingTeamId,
                    totalRuns = 0, totalWickets = 0, totalBalls = 0, wicketHistory = emptyList(), battingOrder = emptyList(),
                    strikerId = null, nonStrikerId = null, currentBowlerId = null, lastBowlerId = null,
                    pendingAction = if (match.isSecondInningsStarted) PendingAction.NONE else PendingAction.START_SECOND_INNINGS
                )
                ballsInOver = 0
            } else if (current.currentInnings == 2 && current.status == MatchStatus.LIVE && (current.totalBalls > 0 || current.totalWickets > 0)) {
                if (current.totalRuns >= current.target!!) current = current.copy(status = MatchStatus.COMPLETED, winnerId = current.battingTeamId)
                else if (inningsEnded) current = current.copy(status = MatchStatus.COMPLETED, winnerId = if (current.totalRuns < current.target!! - 1) current.bowlingTeamId else null)
            }
        }

        // 3. Manual Override & Restoration Guard v2.26.68 🏏🚀⚖️🏅
        if (current.status == MatchStatus.LIVE) {
            val batTeam = if (isTeamA(current.battingTeamId, current)) current.teamA else current.teamB
            val bowlTeam = if (isTeamA(current.bowlingTeamId, current)) current.teamA else current.teamB

            // A. Bowler Switch: If derivation says Sathish, but Intent says Ankoji (and we're mid-over)
            if (match.currentBowlerId != null && match.currentBowlerId != current.currentBowlerId) {
                if (bowlTeam.players.any { it.id == match.currentBowlerId }) {
                    // Over-end swap check: only override if not the last guy who finished
                    if (current.currentBowlerId != null || match.currentBowlerId != current.lastBowlerId) {
                        current = current.copy(currentBowlerId = match.currentBowlerId)
                    }
                }
            }

            // B. Striker/Non-Striker Vacancy Restoration
            if (current.strikerId == null && match.strikerId != null) {
                val p = batTeam.players.find { it.id == match.strikerId }
                if (p != null && !p.battingStats.isOut && match.strikerId != current.nonStrikerId) {
                    current = current.copy(strikerId = match.strikerId)
                }
            }
            if (current.nonStrikerId == null && match.nonStrikerId != null) {
                val p = batTeam.players.find { it.id == match.nonStrikerId }
                if (p != null && !p.battingStats.isOut && match.nonStrikerId != current.strikerId) {
                    current = current.copy(nonStrikerId = match.nonStrikerId)
                }
            }
        }

        // 4. Final Targeted Pending Action Selection (Special Priority) v2.26.68 🏏🚀⚖️🏅
        if (current.status == MatchStatus.LIVE) {
            // Priority 1: User requested special dialogs (Match Settings, Toss, etc.)
            if (match.pendingAction == PendingAction.SELECT_MATCH_SETTINGS || match.pendingAction == PendingAction.TOSS_REQUIRED) {
                current = current.copy(pendingAction = match.pendingAction)
            } 
            // Priority 2: Standard vacancy detection
            else if (current.pendingAction == PendingAction.NONE) {
                val batTeam = if (isTeamA(current.battingTeamId, current)) current.teamA else current.teamB
                val inningsEnded = current.totalWickets >= (batTeam.players.size - 1).coerceAtLeast(1) || current.totalBalls >= current.oversPerInnings * 6
                
                if (!inningsEnded) {
                    current = when {
                        current.strikerId == null -> current.copy(pendingAction = PendingAction.SELECT_STRIKER)
                        current.nonStrikerId == null -> current.copy(pendingAction = PendingAction.SELECT_NON_STRIKER)
                        current.currentBowlerId == null -> current.copy(pendingAction = PendingAction.SELECT_BOWLER)
                        else -> current
                    }
                }
            }
        }

        return current
    }

    private fun isPlayerOut(pId: String?, m: Match): Boolean {
        val p = m.teamA.players.find { it.id == pId } ?: m.teamB.players.find { it.id == pId }
        // Retired Hurt is NOT out. Only isOut (permanent dismissals) counts here.
        return p?.battingStats?.isOut == true
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
                        balls = p.battingStats.balls + (if (ball.isLegalBall || ball.extrasType == ExtrasType.NO_BALL) 1 else 0), 
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
                
                val runsToBowler = if (ball.extrasType == ExtrasType.BYE || ball.extrasType == ExtrasType.LEG_BYE) {
                    ball.runs // Credited if off-bat
                } else {
                    ball.runs + ball.extraRuns
                }
                
                np = np.copy(bowlingStats = p.bowlingStats.copy(
                    runsConceded = p.bowlingStats.runsConceded + runsToBowler, 
                    balls = nb, overs = no, 
                    wickets = p.bowlingStats.wickets + (if (ball.wicketType != WicketType.NONE && ball.wicketType != WicketType.RUN_OUT && ball.wicketType != WicketType.RETIRED_HURT) 1 else 0),
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
            
            // v2.26.75: Explicitly clear pendingAction before recalculating to allow engine to proceed to strikers 🏏🚀⚖️🏅
            val base = current.copy(
                oversPerInnings = newOvers, 
                maxOversPerBowler = newMaxOvers,
                quotaBowlersCount = newQuotaCount,
                quotaMaxOvers = newQuotaLimit,
                pendingAction = PendingAction.NONE
            )
            recalculateMatchFromHistory(base)
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

            // v2.25: Innings Revert Logic
            val updatedMatch = if (current.currentInnings == 2 && current.totalBalls == 0 && current.totalWickets == 0) {
                current.copy(isSecondInningsStarted = false)
            } else {
                current
            }

            val undone = updatedMatch.copy(ballHistory = updatedMatch.ballHistory.dropLast(1))
            recalculateMatchFromHistory(undone)
        }
        _matchState.value?.let { 
            TournamentRepository.updateMatch(it.tournamentId ?: "", it)
        }
    }

    fun addNewPlayerToMatch(context: android.content.Context, playerName: String, battingStyle: BattingStyle = BattingStyle.RHB, bowlingStyle: BowlingStyle = BowlingStyle.RFM) {
        val current = _matchState.value ?: return
        val teamToAddId = when (current.pendingAction ?: PendingAction.NONE) {
            PendingAction.SELECT_STRIKER, PendingAction.SELECT_NON_STRIKER, PendingAction.REPLACE_STRIKER, PendingAction.REPLACE_NON_STRIKER -> current.battingTeamId
            PendingAction.SELECT_BOWLER, PendingAction.REPLACE_BOWLER, PendingAction.SELECT_FIELDER, PendingAction.SELECT_FIELDER_DROPPED_CATCH -> current.bowlingTeamId
            PendingAction.SELECT_WK_A -> current.teamA.id
            PendingAction.SELECT_WK_B -> current.teamB.id
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

    fun clearIfDeleted(matchId: String) {
        if (_matchState.value?.id == matchId) {
            _matchState.value = null
        }
    }

    fun deleteMatch(tournamentId: String, matchId: String) {
        TournamentRepository.deleteMatch(tournamentId, matchId)
        clearIfDeleted(matchId)
    }
}

data class ActiveWicketContext(
    val type: WicketType,
    val initialStrikerId: String,
    val initialNonStrikerId: String,
    val initialBowlerId: String,
    val completedRuns: Int = 0,
    val brokenEnd: String = "STRIKER_CREASE",
    val dismissalReason: String? = null,
    val calculatedVictimId: String? = null,
    val expectedReplacementAction: PendingAction = PendingAction.NONE
)
