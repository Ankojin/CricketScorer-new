package com.example.cricketscorer

object ScoringEngine {

    fun healLegacyId(id: String?, team: Team): String? {
        if (id.isNullOrEmpty()) return id
        val isLikelyUuid = id.length >= 32 && !id.contains(" ")
        if (isLikelyUuid) return id
        return team.players.find { it.name.trim().equals(id.trim(), ignoreCase = true) }?.id ?: id
    }

    fun isTeamA(idOrName: String?, m: Match): Boolean {
        if (idOrName.isNullOrEmpty()) return false
        return idOrName == m.teamA.id || idOrName.trim().equals(m.teamA.name.trim(), ignoreCase = true)
    }

    fun recalculateMatchFromHistory(match: Match): Match {
        if (match.tossWinnerId == null) {
            return match.copy(pendingAction = PendingAction.TOSS_REQUIRED)
        }

        // 1. Initial State Baseline v2.27.0 Critical Physics Engine 🏏🚀⚖️🏅
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
        
        // 2. Event Processing Loop
        match.ballHistory.forEach { ball ->
            itemsProcessed++
            if (current.status == MatchStatus.COMPLETED) return@forEach
            
            val isBattingA = isTeamA(current.battingTeamId, current)
            val battingTeam = if (isBattingA) current.teamA else current.teamB
            val bowlingTeam = if (isBattingA) current.teamB else current.teamA

            // ID Healing for safe matching across devices/versions v2.27.1 🏏🚀⚖️🏅
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
                totalBalls = current.totalBalls + (if (healedBall.isPhysicalBall) 1 else 0),
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

            if (healedBall.isPhysicalBall) ballsInOver++

            // Spatial Tracking Engine v2.28.4 🏏🚀⚖️🏅
            // Trusted initialization: local vars start with current state.
            var sId = current.strikerId
            var nsId = current.nonStrikerId
            var activeBId = current.currentBowlerId
            var lbId = current.lastBowlerId

            // Restoration Guard: Only restore from ball record if the slot is vacant
            // AND the player being restored is not the one getting out on THIS ball.
            if (!healedBall.isAdjustment) {
                val victimId = healedBall.outPlayerId ?: (if (healedBall.wicketType != WicketType.NONE) healedBall.strikerId else null)
                
                if (sId == null && healedBall.strikerId != null && healedBall.strikerId != victimId) {
                    if (!isPlayerUnavailable(healedBall.strikerId, current)) {
                        sId = healedBall.strikerId
                    }
                }
                if (nsId == null && healedBall.nonStrikerId != null && healedBall.nonStrikerId != victimId) {
                    if (!isPlayerUnavailable(healedBall.nonStrikerId, current)) {
                        nsId = healedBall.nonStrikerId
                    }
                }
                if (activeBId == null) activeBId = healedBall.bowlerId
            }

            // A. Manual Adjustments Handling
            if (healedBall.isAdjustment) {
                when (healedBall.adjustmentSlot) {
                    "STRIKER" -> {
                        if (sId == null || healedBall.isReplacement) sId = healedBall.adjustmentPlayerId
                    }
                    "NON_STRIKER" -> {
                        if (nsId == null || healedBall.isReplacement) nsId = healedBall.adjustmentPlayerId
                    }
                    "BOWLER" -> {
                        if (activeBId == null || healedBall.isReplacement) activeBId = healedBall.adjustmentPlayerId
                    }
                    "SWAP" -> {
                        val temp = sId; sId = nsId; nsId = temp
                    }
                }
                current = current.copy(strikerId = sId, nonStrikerId = nsId, currentBowlerId = activeBId)
                return@forEach 
            }

            val physicalRuns = if (healedBall.extrasType == ExtrasType.WIDE) (healedBall.extraRuns - 1).coerceAtLeast(0) 
                               else if (healedBall.extrasType == ExtrasType.BYE || healedBall.extrasType == ExtrasType.LEG_BYE) healedBall.extraRuns 
                               else healedBall.runs
            
            val isCaught = healedBall.wicketType == WicketType.CAUGHT
            val is1G = healedBall.extrasType == ExtrasType.GRANTED

            // B. Run-Based Strike Rotation v2.27.1 🏏🚀⚖️🏅
            val baseRotation = physicalRuns % 2 != 0
            val shouldRotate = (baseRotation != healedBall.hadCrossed) && healedBall.rotateStrike && !is1G
            
            if (shouldRotate) {
                val t = sId; sId = nsId; nsId = t
            }

            // C. Dismissal Physics (Crease Clearing)
            if (healedBall.wicketType != WicketType.NONE) {
                val victimId = healedBall.outPlayerId ?: healedBall.strikerId
                if (isCaught) {
                    sId = null
                } else {
                    if (sId == victimId) sId = null
                    else if (nsId == victimId) nsId = null
                }
            }

            // D. Over-End Logic (Happens AFTER all ball physics)
            var overJustFinished = false
            if (ballsInOver == 6) {
                val t = sId; sId = nsId; nsId = t
                lbId = activeBId; ballsInOver = 0
                overJustFinished = true
            }
            
            current = current.copy(strikerId = sId, nonStrikerId = nsId, currentBowlerId = if (overJustFinished) null else activeBId, lastBowlerId = lbId)

            // Innings Completion Logic
            val inningsEnded = current.totalWickets >= (battingTeam.players.size - 1).coerceAtLeast(1) || current.totalBalls >= current.oversPerInnings * 6
            
            if (current.currentInnings == 1 && inningsEnded) {
                val i1EndTime = match.innings1EndTimeMillis ?: System.currentTimeMillis()
                val i1StartTime = match.startTimeMillis ?: i1EndTime
                val i1Duration = ((i1EndTime - i1StartTime) / 60000).toInt().coerceAtLeast(0)

                current = current.copy(
                    innings1Data = InningsSummary(current.totalRuns, current.totalWickets, current.totalBalls, current.battingTeamId, current.wicketHistory, current.wideCount, current.noBallCount, current.byeCount, current.legByeCount, itemsProcessed, i1Duration, current.battingOrder),
                    currentInnings = 2, target = current.totalRuns + 1, battingTeamId = current.bowlingTeamId, bowlingTeamId = current.battingTeamId,
                    totalRuns = 0, totalWickets = 0, totalBalls = 0, 
                    wideCount = 0, noBallCount = 0, byeCount = 0, legByeCount = 0,
                    wicketHistory = emptyList(), battingOrder = emptyList(),
                    strikerId = null, nonStrikerId = null, currentBowlerId = null, lastBowlerId = null,
                    pendingAction = if (match.isSecondInningsStarted) PendingAction.NONE else PendingAction.START_SECOND_INNINGS
                )
                ballsInOver = 0
            } else if (current.currentInnings == 2 && current.status == MatchStatus.LIVE && (current.totalBalls > 0 || current.totalWickets > 0)) {
                if (current.totalRuns >= current.target!!) current = current.copy(status = MatchStatus.COMPLETED, winnerId = current.battingTeamId)
                else if (inningsEnded) current = current.copy(status = MatchStatus.COMPLETED, winnerId = if (current.totalRuns < current.target!! - 1) current.bowlingTeamId else null)
            }
        }

        // 3. Final Targeted Pending Action Selection (Special Priority) v2.26.68 🏏🚀⚖️🏅
        if (current.status == MatchStatus.LIVE) {
            if (match.pendingAction == PendingAction.SELECT_MATCH_SETTINGS || match.pendingAction == PendingAction.TOSS_REQUIRED) {
                current = current.copy(pendingAction = match.pendingAction)
            } else if (current.pendingAction == PendingAction.NONE) {
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

    fun isPlayerOut(pId: String?, m: Match): Boolean {
        val p = m.teamA.players.find { it.id == pId } ?: m.teamB.players.find { it.id == pId }
        return p?.battingStats?.isOut == true
    }

    fun isPlayerUnavailable(pId: String?, m: Match): Boolean {
        val p = m.teamA.players.find { it.id == pId } ?: m.teamB.players.find { it.id == pId }
        return p?.battingStats?.isOut == true || p?.battingStats?.isRetiredHurt == true
    }

    fun resetTeamStats(team: Team) = team.copy(players = team.players.map { it.copy(battingStats = BattingStats(), bowlingStats = BowlingStats(), fieldingStats = FieldingStats()) })

    fun updateTeamStats(team: Team, ball: Ball, isBat: Boolean, isBowl: Boolean): Team {
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
                    ball.runs
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
}
