package `in`.nrkmart.cricscore.ui

import androidx.compose.runtime.Immutable
import `in`.nrkmart.cricscore.*

@Immutable
data class PlayerMatchLog(
    val matchId: String,
    val dateMillis: Long,
    val opponentName: String,
    val runs: Int,
    val balls: Int,
    val fours: Int,
    val sixes: Int,
    val isOut: Boolean,
    val isDidNotBat: Boolean,
    val oversBowled: Double,
    val runsConceded: Int,
    val wicketsTaken: Int,
    val catches: Int,
    val stumpings: Int
)

@Immutable
data class PlayerCareerStats(
    val playerId: String,
    val playerName: String,
    val battingStyle: BattingStyle,
    val bowlingStyle: BowlingStyle,
    val role: PlayerRole,
    val isCaptain: Boolean,
    val isViceCaptain: Boolean,
    val isJoker: Boolean,
    val currentTeamName: String?,
    
    // Batting
    val matchesPlayed: Int,
    val inningsBatted: Int,
    val notOuts: Int,
    val totalRuns: Int,
    val highestScore: Int,
    val isHighestScoreNotOut: Boolean,
    val fours: Int,
    val sixes: Int,
    val fifties: Int,
    val hundreds: Int,
    val battingAverage: Double,
    val battingStrikeRate: Double,
    
    // Bowling
    val inningsBowled: Int,
    val totalOversBowled: Double,
    val totalBallsBowled: Int,
    val wicketsTaken: Int,
    val runsConceded: Int,
    val bestBowlingWickets: Int,
    val bestBowlingRuns: Int,
    val bowlingEconomy: Double,
    val bowlingAverage: Double,
    val bowlingStrikeRate: Double,
    val dotBalls: Int,
    val maidens: Int,
    
    // Fielding
    val catches: Int,
    val stumpings: Int,
    val runOuts: Int,
    
    // Logs
    val matchLogs: List<PlayerMatchLog>
)

object PlayerStatsCalculator {

    fun calculatePlayerStats(
        playerId: String,
        tournaments: List<Tournament>,
        globalPlayer: Player? = null
    ): PlayerCareerStats {
        var playerName = globalPlayer?.name ?: "Player"
        var bStyle = globalPlayer?.battingStyle ?: BattingStyle.RHB
        var bwStyle = globalPlayer?.bowlingStyle ?: BowlingStyle.NONE
        var pRole = globalPlayer?.role ?: PlayerRole.BATTER
        var isCap = globalPlayer?.isCaptain ?: false
        var isVc = globalPlayer?.isViceCaptain ?: false
        var isJkr = globalPlayer?.isJoker ?: false
        var teamName: String? = null

        val matches = mutableListOf<Pair<Match, Player>>()
        
        // Scan all tournaments for player occurrences
        tournaments.forEach { t ->
            t.teams.forEach { team ->
                team.players.find { it.id == playerId }?.let { p ->
                    if (playerName == "Player") playerName = p.name
                    bStyle = p.battingStyle ?: bStyle
                    bwStyle = p.bowlingStyle ?: bwStyle
                    pRole = p.role
                    isCap = isCap || p.isCaptain
                    isVc = isVc || p.isViceCaptain
                    isJkr = isJkr || p.isJoker
                    teamName = teamName ?: team.name
                }
            }
            
            t.matches.forEach { m ->
                val pInA = m.teamA.players.find { it.id == playerId }
                val pInB = m.teamB.players.find { it.id == playerId }
                val p = pInA ?: pInB
                
                if (p != null) {
                    matches.add(m to p)
                    if (teamName == null) {
                        teamName = if (pInA != null) m.teamA.name else m.teamB.name
                    }
                }
            }
        }

        val totalMatches = matches.distinctBy { it.first.id }.size
        var inningsBatted = 0
        var totalRuns = 0
        var totalBallsFaced = 0
        var notOuts = 0
        var highestScore = 0
        var isHighestNotOut = false
        var fours = 0
        var sixes = 0
        var fifties = 0
        var hundreds = 0

        var inningsBowled = 0
        var totalBallsBowled = 0
        var wicketsTaken = 0
        var runsConceded = 0
        var bestWickets = 0
        var bestRuns = Int.MAX_VALUE
        var dotBalls = 0
        var maidens = 0

        var catches = 0
        var stumpings = 0
        var runOuts = 0

        val logs = mutableListOf<PlayerMatchLog>()

        matches.distinctBy { it.first.id }.forEach { (m, p) ->
            val opponent = if (m.teamA.players.any { it.id == playerId }) m.teamB.name else m.teamA.name
            val bat = p.battingStats
            val bowl = p.bowlingStats
            val field = p.fieldingStats

            val isDnb = bat.balls == 0 && !bat.isOut
            if (!isDnb || bat.runs > 0) {
                inningsBatted++
                totalRuns += bat.runs
                totalBallsFaced += bat.balls
                fours += bat.fours
                sixes += bat.sixes

                if (!bat.isOut) {
                    notOuts++
                }

                if (bat.runs > highestScore || (bat.runs == highestScore && !bat.isOut && !isHighestNotOut)) {
                    highestScore = bat.runs
                    isHighestNotOut = !bat.isOut
                }

                if (bat.runs in 50..99) fifties++
                if (bat.runs >= 100) hundreds++
            }

            val bBalls = bowl.overs * 6 + bowl.balls
            if (bBalls > 0) {
                inningsBowled++
                totalBallsBowled += bBalls
                wicketsTaken += bowl.wickets
                runsConceded += bowl.runsConceded
                dotBalls += bowl.dotBalls
                maidens += bowl.maidens

                if (bowl.wickets > bestWickets || (bowl.wickets == bestWickets && bowl.runsConceded < bestRuns)) {
                    bestWickets = bowl.wickets
                    bestRuns = bowl.runsConceded
                }
            }

            catches += field.catches
            stumpings += field.stumpings
            runOuts += field.runOuts

            val oversDecimal = bowl.overs + (bowl.balls % 6) / 10.0

            logs.add(
                PlayerMatchLog(
                    matchId = m.id,
                    dateMillis = m.dateMillis,
                    opponentName = opponent,
                    runs = bat.runs,
                    balls = bat.balls,
                    fours = bat.fours,
                    sixes = bat.sixes,
                    isOut = bat.isOut,
                    isDidNotBat = isDnb,
                    oversBowled = oversDecimal,
                    runsConceded = bowl.runsConceded,
                    wicketsTaken = bowl.wickets,
                    catches = field.catches,
                    stumpings = field.stumpings
                )
            )
        }

        val timesDismissed = (inningsBatted - notOuts).coerceAtLeast(1)
        val batAvg = if (inningsBatted > 0) totalRuns.toDouble() / timesDismissed else 0.0
        val batSr = if (totalBallsFaced > 0) (totalRuns.toDouble() / totalBallsFaced) * 100 else 0.0

        val totalOversDecimal = (totalBallsBowled / 6) + (totalBallsBowled % 6) / 10.0
        val totalOversExact = totalBallsBowled / 6.0
        val bowlEco = if (totalOversExact > 0) runsConceded.toDouble() / totalOversExact else 0.0
        val bowlAvg = if (wicketsTaken > 0) runsConceded.toDouble() / wicketsTaken else 0.0
        val bowlSr = if (wicketsTaken > 0) totalBallsBowled.toDouble() / wicketsTaken else 0.0

        return PlayerCareerStats(
            playerId = playerId,
            playerName = playerName,
            battingStyle = bStyle,
            bowlingStyle = bwStyle,
            role = pRole,
            isCaptain = isCap,
            isViceCaptain = isVc,
            isJoker = isJkr,
            currentTeamName = teamName,
            matchesPlayed = totalMatches,
            inningsBatted = inningsBatted,
            notOuts = notOuts,
            totalRuns = totalRuns,
            highestScore = highestScore,
            isHighestScoreNotOut = isHighestNotOut,
            fours = fours,
            sixes = sixes,
            fifties = fifties,
            hundreds = hundreds,
            battingAverage = batAvg,
            battingStrikeRate = batSr,
            inningsBowled = inningsBowled,
            totalOversBowled = totalOversDecimal,
            totalBallsBowled = totalBallsBowled,
            wicketsTaken = wicketsTaken,
            runsConceded = runsConceded,
            bestBowlingWickets = if (bestRuns == Int.MAX_VALUE) 0 else bestWickets,
            bestBowlingRuns = if (bestRuns == Int.MAX_VALUE) 0 else bestRuns,
            bowlingEconomy = bowlEco,
            bowlingAverage = bowlAvg,
            bowlingStrikeRate = bowlSr,
            dotBalls = dotBalls,
            maidens = maidens,
            catches = catches,
            stumpings = stumpings,
            runOuts = runOuts,
            matchLogs = logs.sortedByDescending { it.dateMillis }
        )
    }
}
