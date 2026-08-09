package com.example.cricketscorer

data class Player(
    val id: String,
    val name: String,
    val battingStats: BattingStats = BattingStats(),
    val bowlingStats: BowlingStats = BowlingStats(),
    val fieldingStats: FieldingStats = FieldingStats()
)

data class BattingStats(
    val runs: Int = 0,
    val balls: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    val isOut: Boolean = false,
    val wicketType: WicketType = WicketType.NONE,
    val dismissalBowlerId: String? = null,
    val dismissalFielderId: String? = null
) {
    val strikeRate: Double
        get() = if (balls > 0) (runs.toDouble() / balls) * 100 else 0.0
}

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

data class FieldingStats(
    val catches: Int = 0,
    val runOuts: Int = 0,
    val stumpings: Int = 0
)

data class Team(
    val id: String,
    val name: String,
    val players: List<Player> = emptyList(),
    val matchesPlayed: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val points: Int = 0,
    val nrr: Double = 0.0
)

enum class ExtrasType {
    NONE, WIDE, NO_BALL, BYE, LEG_BYE
}

enum class WicketType {
    NONE, BOWLED, CAUGHT, LBW, RUN_OUT, STUMPED, HIT_WICKET, HANDLED_BALL, OBSTRUCTING_FIELD
}

data class Ball(
    val runs: Int,
    val extrasType: ExtrasType = ExtrasType.NONE,
    val extraRuns: Int = 0,
    val wicketType: WicketType = WicketType.NONE,
    val strikerId: String,
    val nonStrikerId: String,
    val bowlerId: String,
    val fielderId: String? = null,
    val isLegalBall: Boolean = true,
    val outPlayerId: String? = null,
    val rotateStrike: Boolean = true
)

data class WicketRecord(
    val wicketNumber: Int,
    val batterName: String,
    val totalRuns: Int,
    val over: String,
    val wicketType: WicketType = WicketType.NONE,
    val bowlerName: String? = null,
    val fielderName: String? = null
)

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
    val recordedBallsCount: Int = 0
)

data class Match(
    val id: String,
    val tournamentId: String? = null,
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
    val pendingAction: PendingAction = PendingAction.NONE,
    val innings1Data: InningsSummary? = null
)

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
    SELECT_CAPTAIN_A, SELECT_CAPTAIN_B, SELECT_WK_A, SELECT_WK_B, SELECT_FIELDER,
    START_SECOND_INNINGS
}

enum class MatchStatus {
    UPCOMING, LIVE, COMPLETED, ABANDONED
}

data class Tournament(
    val id: String,
    val name: String,
    val teams: List<Team> = emptyList(),
    val matches: List<Match> = emptyList(),
    val settings: TournamentSettings = TournamentSettings()
)

data class TournamentSettings(
    val overs: Int = 20,
    val ballType: String = "Leather",
    val powerplayOvers: Int = 6
)
