package com.example.cricketscorer

import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
data class Player(
    val id: String,
    val name: String,
    val battingStats: BattingStats = BattingStats(),
    val bowlingStats: BowlingStats = BowlingStats(),
    val fieldingStats: FieldingStats = FieldingStats(),
    val isJoker: Boolean = false,
    val isCaptain: Boolean = false,
    val isViceCaptain: Boolean = false,
    val battingStyle: BattingStyle? = BattingStyle.RHB
)

enum class BattingStyle {
    RHB, LHB
}

@Immutable
data class BattingStats(
    val runs: Int = 0,
    val balls: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    val isOut: Boolean = false,
    val isRetiredHurt: Boolean = false,
    val wicketType: WicketType = WicketType.NONE,
    val dismissalBowlerId: String? = null,
    val dismissalFielderId: String? = null
) {
    val strikeRate: Double
        get() = if (balls > 0) (runs.toDouble() / balls) * 100 else 0.0
}

@Immutable
data class BowlingStats(
    val overs: Int = 0,
    val balls: Int = 0,
    val maidens: Int = 0,
    val runsConceded: Int = 0,
    val wickets: Int = 0,
    val dotBalls: Int = 0,
    val wides: Int = 0,
    val noBalls: Int = 0
) {
    val economy: Double
        get() {
            val totalOvers = overs + (balls / 6.0)
            return if (totalOvers > 0) runsConceded / totalOvers else 0.0
        }
    
    val formattedOvers: String
        get() = "$overs.${balls % 6}"
}

@Immutable
data class FieldingStats(
    val catches: Int = 0,
    val runOuts: Int = 0,
    val stumpings: Int = 0,
    val droppedCatches: Int = 0
)

@Immutable
data class Team(
    val id: String,
    val name: String,
    val players: List<Player> = emptyList(),
    val matchesPlayed: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val points: Int = 0,
    val nrr: Double = 0.0,
    val colorHex: String? = null
)

enum class ExtrasType {
    NONE, WIDE, NO_BALL, BYE, LEG_BYE, GRANTED
}

enum class WicketType {
    NONE, BOWLED, CAUGHT, LBW, RUN_OUT, STUMPED, HIT_WICKET, HANDLED_BALL, OBSTRUCTING_FIELD, RETIRED_HURT
}

@Immutable
data class Ball(
    val runs: Int,
    val extrasType: ExtrasType = ExtrasType.NONE,
    val extraRuns: Int = 0,
    val wicketType: WicketType = WicketType.NONE,
    val strikerId: String? = null,
    val nonStrikerId: String? = null,
    val bowlerId: String? = null,
    val fielderId: String? = null,
    val isLegalBall: Boolean = true,
    val outPlayerId: String? = null,
    val rotateStrike: Boolean = true,
    val hadCrossed: Boolean = false, // v2.27.1: True if batters crossed for the ATTEMPTED run where wicket fell 🏏🚀⚖️🏅
    val isDroppedCatch: Boolean = false,
    val dismissalReason: String? = null,
    val isAdjustment: Boolean = false,
    val adjustmentSlot: String? = null, // "STRIKER", "NON_STRIKER", "BOWLER"
    val adjustmentPlayerId: String? = null,
    val isReplacement: Boolean = false // v2.27.3: True if manual sub, False if filling a hole 🏏🚀⚖️🏅
) {
    val isPhysicalBall: Boolean
        get() = !isAdjustment && extrasType != ExtrasType.WIDE && extrasType != ExtrasType.NO_BALL && wicketType != WicketType.RETIRED_HURT
}

@Immutable
data class WicketRecord(
    val wicketNumber: Int,
    val batterName: String,
    val totalRuns: Int,
    val over: String,
    val wicketType: WicketType = WicketType.NONE,
    val bowlerName: String? = null,
    val fielderName: String? = null,
    val dismissalReason: String? = null
)

@Immutable
data class InningsSummary(
    val runs: Int,
    val wickets: Int,
    val balls: Int,
    val teamId: String,
    val wicketHistory: List<WicketRecord> = emptyList(),
    val wideCount: Int = 0,
    val noBallCount: Int = 0,
    val byeCount: Int = 0,
    val legByeCount: Int = 0,
    val recordedBallsCount: Int = 0,
    val durationMinutes: Int = 0,
    val battingOrder: List<String> = emptyList()
)

@Immutable
data class GullyRules(
    // SQUAD & TEAMS
    val commonPlayer: Boolean = false,
    val unequalTeams: Boolean = false,
    val playersJoinMidMatch: Boolean = false,
    val playersSwitchMidMatch: Boolean = false,

    // BATTING FORMAT
    val lastManStanding: Boolean = false,
    val singleSideBatting: Boolean = false,

    // SCORING & MATCH
    val noExtraRunsForWidesNoBalls: Boolean = false
) {
    val squadCount: Int
        get() = (if (commonPlayer) 1 else 0) +
                (if (unequalTeams) 1 else 0) +
                (if (playersJoinMidMatch) 1 else 0) +
                (if (playersSwitchMidMatch) 1 else 0)

    val battingCount: Int
        get() = (if (lastManStanding) 1 else 0) +
                (if (singleSideBatting) 1 else 0)

    val scoringCount: Int
        get() = if (noExtraRunsForWidesNoBalls) 1 else 0

    val totalActiveCount: Int
        get() = squadCount + battingCount + scoringCount
}

@Immutable
data class Match(
    val id: String,
    val tournamentId: String? = null,
    val tournamentName: String? = null,
    val teamA: Team,
    val teamB: Team,
    val tossWinnerId: String? = null,
    val tossDecision: String? = null, // "BAT" or "BOWL"
    val initialBattingTeamId: String? = null,
    val initialBowlingTeamId: String? = null,
    
    val teamACaptainId: String? = null,
    val teamBCaptainId: String? = null,
    val teamAWicketKeeperId: String? = null,
    val teamBWicketKeeperId: String? = null,
    
    val target: Int? = null,
    var status: MatchStatus = MatchStatus.UPCOMING,
    val currentInnings: Int = 1,
    val battingTeamId: String,
    val bowlingTeamId: String,
    val totalRuns: Int = 0,
    val totalWickets: Int = 0,
    val totalBalls: Int = 0,
    val wideCount: Int = 0,
    val noBallCount: Int = 0,
    val byeCount: Int = 0,
    val legByeCount: Int = 0,
    val ballHistory: List<Ball> = emptyList(),
    val wicketHistory: List<WicketRecord> = emptyList(),
    val strikerId: String? = null,
    val nonStrikerId: String? = null,
    val currentBowlerId: String? = null,
    val lastBowlerId: String? = null,
    val winnerId: String? = null,
    val manOfTheMatchId: String? = null,
    val oversPerInnings: Int = 20,
    val maxOversPerBowler: Int? = null,
    val quotaBowlersCount: Int? = null,
    val quotaMaxOvers: Int? = null,
    val gullyRules: GullyRules = GullyRules(),
    val pendingAction: PendingAction? = PendingAction.NONE,
    val innings1Data: InningsSummary? = null,
    val isSecondInningsStarted: Boolean = false,
    val innings1EndTimeMillis: Long? = null,
    val innings2StartTimeMillis: Long? = null,
    val lastNotifiedBowlerId: String? = null,
    val battingOrder: List<String> = emptyList(),
    val startTimeMillis: Long? = null,
    val endTimeMillis: Long? = null,
    val dateMillis: Long = System.currentTimeMillis()
)

@Immutable
data class Partnership(
    val batter1Id: String,
    val batter1Name: String,
    val batter1Runs: Int,
    val batter1Balls: Int,
    val batter2Id: String,
    val batter2Name: String,
    val batter2Runs: Int,
    val batter2Balls: Int,
    val totalRuns: Int,
    val totalBalls: Int
)

enum class PendingAction {
    NONE, SELECT_STRIKER, SELECT_NON_STRIKER, SELECT_BOWLER, TOSS_REQUIRED, 
    SELECT_WK_A, SELECT_WK_B, SELECT_FIELDER,
    START_SECOND_INNINGS, SELECT_FIELDER_DROPPED_CATCH, SELECT_RUNS_DROPPED_CATCH,
    REPLACE_STRIKER, REPLACE_NON_STRIKER, REPLACE_BOWLER,
    SELECT_RUNS_WICKET, SELECT_MATCH_SETTINGS
}

enum class MatchStatus {
    UPCOMING, LIVE, COMPLETED, ABANDONED
}

@Immutable
data class Tournament(
    val id: String,
    val name: String,
    val teams: List<Team> = emptyList(),
    val matches: List<Match> = emptyList(),
    val settings: TournamentSettings = TournamentSettings(),
    val participants: List<Player> = emptyList() // v2.31.9: Track all players for historical stats 🏏🚀⚖️🏅
)

// v2.32.3: Null-safe bridge for legacy data handling 🏏🚀⚖️🏅
fun Tournament.safeCopy(
    id: String? = null,
    name: String? = null,
    teams: List<Team>? = null,
    matches: List<Match>? = null,
    settings: TournamentSettings? = null,
    participants: List<Player>? = null
): Tournament {
    return Tournament(
        id = id ?: this.id ?: UUID.randomUUID().toString(),
        name = name ?: this.name ?: "Tournament",
        teams = teams ?: this.teams.orEmpty().filterNotNull().map { it.safeCopy() },
        matches = matches ?: this.matches.orEmpty().filterNotNull().map { it.safeCopy() },
        settings = settings ?: this.settings ?: TournamentSettings(),
        participants = participants ?: this.participants.orEmpty().filterNotNull().map { it.safeCopy() }
    )
}

fun Team.safeCopy(
    id: String? = null,
    name: String? = null,
    players: List<Player>? = null,
    colorHex: String? = null
): Team {
    return Team(
        id = id ?: this.id ?: UUID.randomUUID().toString(),
        name = name ?: this.name ?: "Team",
        players = players ?: this.players.orEmpty().filterNotNull().map { it.safeCopy() },
        matchesPlayed = this.matchesPlayed,
        wins = this.wins,
        losses = this.losses,
        points = this.points,
        nrr = this.nrr,
        colorHex = colorHex ?: this.colorHex
    )
}

fun Player.safeCopy(): Player {
    return Player(
        id = this.id ?: UUID.randomUUID().toString(),
        name = this.name ?: "Player",
        battingStats = this.battingStats ?: BattingStats(),
        bowlingStats = this.bowlingStats ?: BowlingStats(),
        fieldingStats = this.fieldingStats ?: FieldingStats(),
        isJoker = this.isJoker,
        isCaptain = this.isCaptain,
        isViceCaptain = this.isViceCaptain,
        battingStyle = this.battingStyle ?: BattingStyle.RHB
    )
}

fun Match.safeCopy(): Match {
    return Match(
        id = this.id ?: UUID.randomUUID().toString(),
        tournamentId = this.tournamentId,
        tournamentName = this.tournamentName,
        teamA = this.teamA?.safeCopy() ?: Team("","", emptyList()),
        teamB = this.teamB?.safeCopy() ?: Team("","", emptyList()),
        tossWinnerId = this.tossWinnerId,
        tossDecision = this.tossDecision,
        initialBattingTeamId = this.initialBattingTeamId,
        initialBowlingTeamId = this.initialBowlingTeamId,
        target = this.target,
        status = this.status ?: MatchStatus.UPCOMING,
        currentInnings = this.currentInnings,
        battingTeamId = this.battingTeamId ?: "",
        bowlingTeamId = this.bowlingTeamId ?: "",
        totalRuns = this.totalRuns,
        totalWickets = this.totalWickets,
        totalBalls = this.totalBalls,
        wideCount = this.wideCount,
        noBallCount = this.noBallCount,
        byeCount = this.byeCount,
        legByeCount = this.legByeCount,
        ballHistory = this.ballHistory.orEmpty().filterNotNull(),
        wicketHistory = this.wicketHistory.orEmpty().filterNotNull(),
        strikerId = this.strikerId,
        nonStrikerId = this.nonStrikerId,
        currentBowlerId = this.currentBowlerId,
        lastBowlerId = this.lastBowlerId,
        winnerId = this.winnerId,
        manOfTheMatchId = this.manOfTheMatchId,
        oversPerInnings = this.oversPerInnings,
        maxOversPerBowler = this.maxOversPerBowler,
        quotaBowlersCount = this.quotaBowlersCount,
        quotaMaxOvers = this.quotaMaxOvers,
        gullyRules = this.gullyRules,
        pendingAction = this.pendingAction ?: PendingAction.NONE,
        innings1Data = this.innings1Data,
        isSecondInningsStarted = this.isSecondInningsStarted,
        innings1EndTimeMillis = this.innings1EndTimeMillis,
        innings2StartTimeMillis = this.innings2StartTimeMillis,
        lastNotifiedBowlerId = this.lastNotifiedBowlerId,
        battingOrder = this.battingOrder.orEmpty().filterNotNull(),
        startTimeMillis = this.startTimeMillis,
        endTimeMillis = this.endTimeMillis,
        dateMillis = this.dateMillis
    )
}

@Immutable
data class TournamentSettings(
    val overs: Int = 20,
    val ballType: String = "Leather",
    val powerplayOvers: Int = 6,
    val maxOversPerBowler: Int? = null,
    val quotaBowlersCount: Int? = null,
    val quotaMaxOvers: Int? = null
)

@Immutable
data class ActiveWicketContext(
    val type: WicketType,
    val initialStrikerId: String,
    val initialNonStrikerId: String,
    val initialBowlerId: String,
    val completedRuns: Int,
    val brokenEnd: String,
    val dismissalReason: String? = null,
    val dismissalFielderId: String? = null,
    val hadCrossed: Boolean = false,
    val expectedReplacementAction: PendingAction
)

@Immutable
data class OverSummary(
    val overNumber: Int,
    val runs: Int,
    val wickets: Int,
    val ballLabels: List<String> = emptyList(), // Added for Pro visual style 🏏🚀⚖️🏅
    val teamTotalRuns: Int = 0,
    val teamTotalWickets: Int = 0,
    val battingTeamName: String = ""
)

@Immutable
data class MatchUiState(
    val match: Match? = null,
    val isDarkMode: Boolean? = null,
    val bowlerNotification: String? = null,
    val activeWicketContext: ActiveWicketContext? = null,
    val isSyncEnabled: Boolean = false,
    val connectedDevicesCount: Int = 0,
    val finishedOverSummary: OverSummary? = null
)
