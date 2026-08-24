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
                pendingAction = PendingAction.NONE
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
        if (currentMatch.pendingAction != PendingAction.NONE) return // Safeguard v2.19 (Engine Hardened & Draw Fix) 🏏🚀⚖️🏅
        
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
        val updated = _matchState.updateAndGet { current ->
            if (current == null) return@updateAndGet null
            
            val withSelection = when (current.pendingAction ?: PendingAction.NONE) {
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
                PendingAction.SELECT_WK_A -> {
                    current.copy(teamAWicketKeeperId = playerId)
                }
                PendingAction.SELECT_WK_B -> {
                    current.copy(teamBWicketKeeperId = playerId)
                }
                else -> current
            }
            
            val nextAction = when {
                withSelection.strikerId == null -> PendingAction.SELECT_STRIKER
                withSelection.nonStrikerId == null -> PendingAction.SELECT_NON_STRIKER
                withSelection.currentBowlerId == null -> PendingAction.SELECT_BOWLER
                else -> PendingAction.NONE
            }
            
            withSelection.copy(pendingAction = nextAction)
        }
        if (updated != null && (updated.pendingAction ?: PendingAction.NONE) == PendingAction.NONE) {
            TournamentRepository.updateMatch(updated.tournamentId ?: "", updated)
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
            
            // v2.19 (Engine Hardened & Draw Fix) 🏏🚀⚖️🏅: Prepare spell notification logic (Side-effect free preparation)
            if (result.status == MatchStatus.LIVE && ball.isLegalBall && result.totalBalls % 6 == 0 && _bowlerNotification.value == null) {
                val bowlingTeam = if (isTeamA(result.bowlingTeamId, result)) result.teamA else result.teamB
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

        // Definitive Match Engine Overhaul (v2.23 Logic & Selection Integrity) 🏏🚀⚖️🏅
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
        match.ballHistory.forEach { ball ->
            itemsProcessed++
            // In-Loop Selection Reset: Consume previous selection requirement if ball exists
            current = current.copy(pendingAction = PendingAction.NONE)
            
            if (current.status == MatchStatus.COMPLETED) return@forEach
            
            val isBattingA = isTeamA(current.battingTeamId, current)
            val battingTeam = if (isBattingA) current.teamA else current.teamB
            val bowlingTeam = if (isBattingA) current.teamB else current.teamA

            // ID Healing for safe matching across devices/versions
            val hStrikerId = healLegacyId(ball.strikerId, battingTeam)
            val hNonStrikerId = healLegacyId(ball.nonStrikerId, battingTeam)
            val hBowlerId = healLegacyId(ball.bowlerId, bowlingTeam)
            val hOutPlayerId = healLegacyId(ball.outPlayerId, battingTeam)
            
            val healedBall = ball.copy(
                strikerId = hStrikerId,
                nonStrikerId = hNonStrikerId,
                bowlerId = hBowlerId,
                outPlayerId = hOutPlayerId
            )

            val newBattingOrder = current.battingOrder.toMutableList()
            healedBall.strikerId?.let { if (it.isNotEmpty() && !newBattingOrder.contains(it)) newBattingOrder.add(it) }
            healedBall.nonStrikerId?.let { if (it.isNotEmpty() && !newBattingOrder.contains(it)) newBattingOrder.add(it) }
            val outId = healedBall.outPlayerId ?: (if (healedBall.wicketType != WicketType.NONE && healedBall.wicketType != WicketType.RETIRED_HURT) healedBall.strikerId else null)
            if (!outId.isNullOrEmpty() && !newBattingOrder.contains(outId)) newBattingOrder.add(outId)

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

            if (healedBall.wicketType != WicketType.NONE && healedBall.wicketType != WicketType.RETIRED_HURT) {
                val outName = battingTeam.players.find { it.id == outId }?.name ?: "Unknown"
                val bName = bowlingTeam.players.find { it.id == healedBall.bowlerId }?.name
                val fName = bowlingTeam.players.find { it.id == healedBall.fielderId }?.name
                current = current.copy(wicketHistory = current.wicketHistory + WicketRecord(current.totalWickets, "☝️ $outName", current.totalRuns, "${current.totalBalls/6}.${current.totalBalls%6}", healedBall.wicketType, bName, fName))
            }

            // Track Balls Locally for Over Management
            if (healedBall.isLegalBall) ballsInOver++

            // History Integrity: Engine is source of truth. Use loop's current state if not null.
            var sId = current.strikerId ?: healedBall.strikerId
            var nsId = current.nonStrikerId ?: healedBall.nonStrikerId
            var bId = healedBall.bowlerId; var lbId = current.lastBowlerId

            val physicalRuns = if (healedBall.extrasType == ExtrasType.WIDE) (healedBall.extraRuns - 1).coerceAtLeast(0) 
                               else if (healedBall.extrasType == ExtrasType.BYE || healedBall.extrasType == ExtrasType.LEG_BYE) healedBall.extraRuns 
                               else healedBall.runs
            
            // ICC Strike Rotation Logic
            if (healedBall.rotateStrike && physicalRuns % 2 != 0) {
                val t = sId; sId = nsId; nsId = t
            }

            // Over-End Swap
            if (ballsInOver == 6) {
                val t = sId; sId = nsId; nsId = t
                lbId = bId; bId = null; ballsInOver = 0
                if (current.status == MatchStatus.LIVE) current = current.copy(pendingAction = PendingAction.SELECT_BOWLER)
            }
            
            val actualOutId = if (healedBall.wicketType != WicketType.NONE) healedBall.outPlayerId ?: healedBall.strikerId else null
            val wasNsOut = actualOutId != null && actualOutId == nsId
            if (actualOutId != null) { 
                if (sId == actualOutId) sId = null else if (nsId == actualOutId) nsId = null 
            }
            current = current.copy(strikerId = sId, nonStrikerId = nsId, currentBowlerId = bId, lastBowlerId = lbId)

            val inningsEnded = current.totalWickets >= (battingTeam.players.size - 1).coerceAtLeast(1) || current.totalBalls >= current.oversPerInnings * 6
            
            // Wicket Logic: Selection Fix (v2.23)
            if (healedBall.wicketType != WicketType.NONE && healedBall.wicketType != WicketType.RETIRED_HURT && !inningsEnded && current.status == MatchStatus.LIVE) {
                current = current.copy(pendingAction = if (wasNsOut) PendingAction.SELECT_NON_STRIKER else PendingAction.SELECT_STRIKER)
            }

            if (current.currentInnings == 1 && inningsEnded) {
                current = current.copy(
                    innings1Data = InningsSummary(current.totalRuns, current.totalWickets, current.totalBalls, current.battingTeamId, current.wicketHistory, current.wideCount, current.noBallCount, current.byeCount, current.legByeCount, itemsProcessed, 0, current.battingOrder),
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

        val finalBattingTeam = if (isTeamA(current.battingTeamId, current)) current.teamA else current.teamB
        val finalInningsEnded = current.totalWickets >= (finalBattingTeam.players.size - 1).coerceAtLeast(1) || current.totalBalls >= current.oversPerInnings * 6

        // Manual Transition: After the loop finishes
        if (current.currentInnings == 1 && match.isSecondInningsStarted && finalInningsEnded) {
            current = current.copy(
                innings1Data = InningsSummary(current.totalRuns, current.totalWickets, current.totalBalls, current.battingTeamId, current.wicketHistory, current.wideCount, current.noBallCount, current.byeCount, current.legByeCount, itemsProcessed, 0, current.battingOrder),
                currentInnings = 2, target = current.totalRuns + 1, battingTeamId = current.bowlingTeamId, bowlingTeamId = current.battingTeamId,
                totalRuns = 0, totalWickets = 0, totalBalls = 0, wicketHistory = emptyList(), battingOrder = emptyList(),
                strikerId = null, nonStrikerId = null, currentBowlerId = null, lastBowlerId = null,
                pendingAction = PendingAction.NONE // v2.23: Explicitly NONE if started
            )
            ballsInOver = 0
        }

        // Final Restoration Guard (v2.24) 🏏🚀⚖️🏅
        if (current.status == MatchStatus.LIVE) {
            val batTeam = if (isTeamA(current.battingTeamId, current)) current.teamA else current.teamB
            val bowlTeam = if (isTeamA(current.bowlingTeamId, current)) current.teamA else current.teamB

            // Strictly restore striker/non-striker ONLY if they belong to batting team and are NOT out
            if (current.strikerId == null && match.strikerId != null &&
                batTeam.players.any { it.id == match.strikerId } && !isPlayerOut(match.strikerId, current)) {
                current = current.copy(strikerId = match.strikerId)
            }
            if (current.nonStrikerId == null && match.nonStrikerId != null &&
                batTeam.players.any { it.id == match.nonStrikerId } && !isPlayerOut(match.nonStrikerId, current)) {
                current = current.copy(nonStrikerId = match.nonStrikerId)
            }
            // Strictly restore bowler ONLY if they belong to bowling team
            if (current.currentBowlerId == null && match.currentBowlerId != null &&
                bowlTeam.players.any { it.id == match.currentBowlerId }) {
                current = current.copy(currentBowlerId = match.currentBowlerId)
            }
        }

        if (current.status == MatchStatus.LIVE && current.pendingAction == PendingAction.NONE) {
            val nextAction = when {
                current.strikerId == null -> PendingAction.SELECT_STRIKER
                current.nonStrikerId == null -> PendingAction.SELECT_NON_STRIKER
                current.currentBowlerId == null -> PendingAction.SELECT_BOWLER
                else -> current.pendingAction
            }
            current = current.copy(pendingAction = nextAction)
        }
        return current
    }

    private fun isPlayerOut(pId: String?, m: Match) = m.teamA.players.find { it.id == pId }?.battingStats?.isOut == true || m.teamB.players.find { it.id == pId }?.battingStats?.isOut == true

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
