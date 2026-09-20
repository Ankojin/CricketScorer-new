package com.example.cricketscorer.db

import androidx.room.*
import com.example.cricketscorer.*

@Entity(tableName = "tournaments")
data class TournamentEntity(
    @PrimaryKey val id: String,
    val name: String,
    @Embedded val settings: TournamentSettings
)

@Entity(
    tableName = "teams",
    foreignKeys = [
        ForeignKey(
            entity = TournamentEntity::class,
            parentColumns = ["id"],
            childColumns = ["tournamentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tournamentId")]
)
data class TeamEntity(
    @PrimaryKey val id: String,
    val tournamentId: String,
    val name: String,
    val matchesPlayed: Int,
    val wins: Int,
    val losses: Int,
    val points: Int,
    val nrr: Double
)

@Entity(
    tableName = "players",
    foreignKeys = [
        ForeignKey(
            entity = TournamentEntity::class,
            parentColumns = ["id"],
            childColumns = ["tournamentId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TeamEntity::class,
            parentColumns = ["id"],
            childColumns = ["teamId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("tournamentId"), Index("teamId")]
)
data class PlayerEntity(
    @PrimaryKey val id: String,
    val tournamentId: String,
    val teamId: String?,
    val name: String,
    val isJoker: Boolean,
    val isCaptain: Boolean,
    val isViceCaptain: Boolean,
    val battingStyle: BattingStyle?,
    
    // Flattened BattingStats
    val battingRuns: Int,
    val battingBalls: Int,
    val battingFours: Int,
    val battingSixes: Int,
    val battingIsOut: Boolean,
    val battingIsRetiredHurt: Boolean,
    val battingWicketType: WicketType,
    val battingDismissalBowlerId: String?,
    val battingDismissalFielderId: String?,

    // Flattened BowlingStats
    val bowlingOvers: Int,
    val bowlingBalls: Int,
    val bowlingMaidens: Int,
    val bowlingRunsConceded: Int,
    val bowlingWickets: Int,
    val bowlingDotBalls: Int,
    val bowlingWides: Int,
    val bowlingNoBalls: Int,

    // Flattened FieldingStats
    val fieldingCatches: Int,
    val fieldingRunOuts: Int,
    val fieldingStumpings: Int,
    val fieldingDroppedCatches: Int
)

@Entity(
    tableName = "matches",
    foreignKeys = [
        ForeignKey(
            entity = TournamentEntity::class,
            parentColumns = ["id"],
            childColumns = ["tournamentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tournamentId")]
)
data class MatchEntity(
    @PrimaryKey val id: String,
    val tournamentId: String,
    val tournamentName: String?,
    val teamAId: String,
    val teamBId: String,
    val teamAPlayerIds: List<String>,
    val teamBPlayerIds: List<String>,
    val tossWinnerId: String?,
    val tossDecision: String?,
    val initialBattingTeamId: String?,
    val initialBowlingTeamId: String?,
    val teamACaptainId: String?,
    val teamBCaptainId: String?,
    val teamAWicketKeeperId: String?,
    val teamBWicketKeeperId: String?,
    val target: Int?,
    val status: MatchStatus,
    val currentInnings: Int,
    val battingTeamId: String,
    val bowlingTeamId: String,
    val totalRuns: Int,
    val totalWickets: Int,
    val totalBalls: Int,
    val wideCount: Int,
    val noBallCount: Int,
    val byeCount: Int,
    val legByeCount: Int,
    val wicketHistory: List<WicketRecord>,
    val strikerId: String?,
    val nonStrikerId: String?,
    val currentBowlerId: String?,
    val lastBowlerId: String?,
    val winnerId: String?,
    val manOfTheMatchId: String?,
    val oversPerInnings: Int,
    val maxOversPerBowler: Int?,
    val quotaBowlersCount: Int?,
    val quotaMaxOvers: Int?,
    val pendingAction: PendingAction?,
    val innings1Data: InningsSummary?,
    val isSecondInningsStarted: Boolean,
    val innings1EndTimeMillis: Long?,
    val innings2StartTimeMillis: Long?,
    val lastNotifiedBowlerId: String?,
    val battingOrder: List<String>,
    val startTimeMillis: Long?,
    val endTimeMillis: Long?,
    val dateMillis: Long
)

@Entity(
    tableName = "balls",
    foreignKeys = [
        ForeignKey(
            entity = MatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["matchId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("matchId")]
)
data class BallEntity(
    @PrimaryKey(autoGenerate = true) val ballId: Long = 0,
    val matchId: String,
    val runs: Int,
    val extrasType: ExtrasType,
    val extraRuns: Int,
    val wicketType: WicketType,
    val strikerId: String?,
    val nonStrikerId: String?,
    val bowlerId: String?,
    val fielderId: String?,
    val isLegalBall: Boolean,
    val outPlayerId: String?,
    val rotateStrike: Boolean,
    val hadCrossed: Boolean,
    val isDroppedCatch: Boolean,
    val dismissalReason: String?,
    val isAdjustment: Boolean,
    val adjustmentSlot: String?,
    val adjustmentPlayerId: String?,
    val isReplacement: Boolean
)

data class MatchWithBalls(
    @Embedded val match: MatchEntity,
    @Relation(parentColumn = "id", entityColumn = "matchId")
    val balls: List<BallEntity>
)

data class TournamentWithDetails(
    @Embedded val tournament: TournamentEntity,
    @Relation(parentColumn = "id", entityColumn = "tournamentId")
    val teams: List<TeamEntity>,
    @Relation(
        entity = MatchEntity::class,
        parentColumn = "id", 
        entityColumn = "tournamentId"
    )
    val matches: List<MatchWithBalls>,
    @Relation(parentColumn = "id", entityColumn = "tournamentId")
    val players: List<PlayerEntity>
)

// Extension functions to convert between Room and Domain models
fun PlayerEntity.toDomain(): Player = Player(
    id = id,
    name = name,
    battingStats = BattingStats(
        runs = battingRuns,
        balls = battingBalls,
        fours = battingFours,
        sixes = battingSixes,
        isOut = battingIsOut,
        isRetiredHurt = battingIsRetiredHurt,
        wicketType = battingWicketType,
        dismissalBowlerId = battingDismissalBowlerId,
        dismissalFielderId = battingDismissalFielderId
    ),
    bowlingStats = BowlingStats(
        overs = bowlingOvers,
        balls = bowlingBalls,
        maidens = bowlingMaidens,
        runsConceded = bowlingRunsConceded,
        wickets = bowlingWickets,
        dotBalls = bowlingDotBalls,
        wides = bowlingWides,
        noBalls = bowlingNoBalls
    ),
    fieldingStats = FieldingStats(
        catches = fieldingCatches,
        runOuts = fieldingRunOuts,
        stumpings = fieldingStumpings,
        droppedCatches = fieldingDroppedCatches
    ),
    isJoker = isJoker,
    isCaptain = isCaptain,
    isViceCaptain = isViceCaptain,
    battingStyle = battingStyle
)

fun Player.toEntity(tournamentId: String, teamId: String?): PlayerEntity = PlayerEntity(
    id = id,
    tournamentId = tournamentId,
    teamId = teamId,
    name = name,
    isJoker = isJoker,
    isCaptain = isCaptain,
    isViceCaptain = isViceCaptain,
    battingStyle = battingStyle,
    battingRuns = battingStats.runs,
    battingBalls = battingStats.balls,
    battingFours = battingStats.fours,
    battingSixes = battingStats.sixes,
    battingIsOut = battingStats.isOut,
    battingIsRetiredHurt = battingStats.isRetiredHurt,
    battingWicketType = battingStats.wicketType,
    battingDismissalBowlerId = battingStats.dismissalBowlerId,
    battingDismissalFielderId = battingStats.dismissalFielderId,
    bowlingOvers = bowlingStats.overs,
    bowlingBalls = bowlingStats.balls,
    bowlingMaidens = bowlingStats.maidens,
    bowlingRunsConceded = bowlingStats.runsConceded,
    bowlingWickets = bowlingStats.wickets,
    bowlingDotBalls = bowlingStats.dotBalls,
    bowlingWides = bowlingStats.wides,
    bowlingNoBalls = bowlingStats.noBalls,
    fieldingCatches = fieldingStats.catches,
    fieldingRunOuts = fieldingStats.runOuts,
    fieldingStumpings = fieldingStats.stumpings,
    fieldingDroppedCatches = fieldingStats.droppedCatches
)

fun BallEntity.toDomain(): Ball = Ball(
    runs = runs,
    extrasType = extrasType,
    extraRuns = extraRuns,
    wicketType = wicketType,
    strikerId = strikerId,
    nonStrikerId = nonStrikerId,
    bowlerId = bowlerId,
    fielderId = fielderId,
    isLegalBall = isLegalBall,
    outPlayerId = outPlayerId,
    rotateStrike = rotateStrike,
    hadCrossed = hadCrossed,
    isDroppedCatch = isDroppedCatch,
    dismissalReason = dismissalReason,
    isAdjustment = isAdjustment,
    adjustmentSlot = adjustmentSlot,
    adjustmentPlayerId = adjustmentPlayerId,
    isReplacement = isReplacement
)

fun Ball.toEntity(matchId: String): BallEntity = BallEntity(
    matchId = matchId,
    runs = runs,
    extrasType = extrasType,
    extraRuns = extraRuns,
    wicketType = wicketType,
    strikerId = strikerId,
    nonStrikerId = nonStrikerId,
    bowlerId = bowlerId,
    fielderId = fielderId,
    isLegalBall = isLegalBall,
    outPlayerId = outPlayerId,
    rotateStrike = rotateStrike,
    hadCrossed = hadCrossed,
    isDroppedCatch = isDroppedCatch,
    dismissalReason = dismissalReason,
    isAdjustment = isAdjustment,
    adjustmentSlot = adjustmentSlot,
    adjustmentPlayerId = adjustmentPlayerId,
    isReplacement = isReplacement
)

fun MatchWithBalls.toDomain(availableTeams: List<Team> = emptyList()): Match = match.let { m ->
    val baseTeamA = availableTeams.find { it.id == m.teamAId } ?: Team(id = m.teamAId, name = m.tournamentName ?: "Team A")
    val baseTeamB = availableTeams.find { it.id == m.teamBId } ?: Team(id = m.teamBId, name = m.tournamentName ?: "Team B")
    
    val rebuiltTeamA = baseTeamA.copy(players = baseTeamA.players.filter { m.teamAPlayerIds.contains(it.id) })
    val rebuiltTeamB = baseTeamB.copy(players = baseTeamB.players.filter { m.teamBPlayerIds.contains(it.id) })

    Match(
        id = m.id,
        tournamentId = m.tournamentId,
        tournamentName = m.tournamentName,
        teamA = rebuiltTeamA,
        teamB = rebuiltTeamB,
        tossWinnerId = m.tossWinnerId,
        tossDecision = m.tossDecision,
        initialBattingTeamId = m.initialBattingTeamId,
        initialBowlingTeamId = m.initialBowlingTeamId,
        teamACaptainId = m.teamACaptainId,
        teamBCaptainId = m.teamBCaptainId,
        teamAWicketKeeperId = m.teamAWicketKeeperId,
        teamBWicketKeeperId = m.teamBWicketKeeperId,
        target = m.target,
        status = m.status,
        currentInnings = m.currentInnings,
        battingTeamId = m.battingTeamId,
        bowlingTeamId = m.bowlingTeamId,
        totalRuns = m.totalRuns,
        totalWickets = m.totalWickets,
        totalBalls = m.totalBalls,
        wideCount = m.wideCount,
        noBallCount = m.noBallCount,
        byeCount = m.byeCount,
        legByeCount = m.legByeCount,
        ballHistory = balls.map { it.toDomain() },
        wicketHistory = m.wicketHistory,
        strikerId = m.strikerId,
        nonStrikerId = m.nonStrikerId,
        currentBowlerId = m.currentBowlerId,
        lastBowlerId = m.lastBowlerId,
        winnerId = m.winnerId,
        manOfTheMatchId = m.manOfTheMatchId,
        oversPerInnings = m.oversPerInnings,
        maxOversPerBowler = m.maxOversPerBowler,
        quotaBowlersCount = m.quotaBowlersCount,
        quotaMaxOvers = m.quotaMaxOvers,
        pendingAction = m.pendingAction,
        innings1Data = m.innings1Data,
        isSecondInningsStarted = m.isSecondInningsStarted,
        innings1EndTimeMillis = m.innings1EndTimeMillis,
        innings2StartTimeMillis = m.innings2StartTimeMillis,
        lastNotifiedBowlerId = m.lastNotifiedBowlerId,
        battingOrder = m.battingOrder,
        startTimeMillis = m.startTimeMillis,
        endTimeMillis = m.endTimeMillis,
        dateMillis = m.dateMillis
    )
}

fun Match.toEntity(): MatchEntity = MatchEntity(
    id = id,
    tournamentId = tournamentId ?: "",
    tournamentName = tournamentName,
    teamAId = teamA.id,
    teamBId = teamB.id,
    teamAPlayerIds = teamA.players.map { it.id },
    teamBPlayerIds = teamB.players.map { it.id },
    tossWinnerId = tossWinnerId,
    tossDecision = tossDecision,
    initialBattingTeamId = initialBattingTeamId,
    initialBowlingTeamId = initialBowlingTeamId,
    teamACaptainId = teamACaptainId,
    teamBCaptainId = teamBCaptainId,
    teamAWicketKeeperId = teamAWicketKeeperId,
    teamBWicketKeeperId = teamBWicketKeeperId,
    target = target,
    status = status,
    currentInnings = currentInnings,
    battingTeamId = battingTeamId,
    bowlingTeamId = bowlingTeamId,
    totalRuns = totalRuns,
    totalWickets = totalWickets,
    totalBalls = totalBalls,
    wideCount = wideCount,
    noBallCount = noBallCount,
    byeCount = byeCount,
    legByeCount = legByeCount,
    wicketHistory = wicketHistory,
    strikerId = strikerId,
    nonStrikerId = nonStrikerId,
    currentBowlerId = currentBowlerId,
    lastBowlerId = lastBowlerId,
    winnerId = winnerId,
    manOfTheMatchId = manOfTheMatchId,
    oversPerInnings = oversPerInnings,
    maxOversPerBowler = maxOversPerBowler,
    quotaBowlersCount = quotaBowlersCount,
    quotaMaxOvers = quotaMaxOvers,
    pendingAction = pendingAction,
    innings1Data = innings1Data,
    isSecondInningsStarted = isSecondInningsStarted,
    innings1EndTimeMillis = innings1EndTimeMillis,
    innings2StartTimeMillis = innings2StartTimeMillis,
    lastNotifiedBowlerId = lastNotifiedBowlerId,
    battingOrder = battingOrder,
    startTimeMillis = startTimeMillis,
    endTimeMillis = endTimeMillis,
    dateMillis = dateMillis
)

fun TeamEntity.toDomain(players: List<Player>): Team = Team(
    id = id,
    name = name,
    players = players,
    matchesPlayed = matchesPlayed,
    wins = wins,
    losses = losses,
    points = points,
    nrr = nrr
)

fun Team.toEntity(tournamentId: String): TeamEntity = TeamEntity(
    id = id,
    tournamentId = tournamentId,
    name = name,
    matchesPlayed = matchesPlayed,
    wins = wins,
    losses = losses,
    points = points,
    nrr = nrr
)

fun Tournament.toEntity(): TournamentEntity = TournamentEntity(
    id = id,
    name = name,
    settings = settings
)

fun TournamentWithDetails.toDomain(): Tournament = tournament.let { t ->
    val finalizedTeams = teams.map { te ->
        te.toDomain(players.filter { it.teamId == te.id }.map { it.toDomain() })
    }
    Tournament(
        id = t.id,
        name = t.name,
        settings = t.settings,
        teams = finalizedTeams,
        matches = matches.map { it.toDomain(finalizedTeams) },
        participants = players.map { it.toDomain() }
    )
}
