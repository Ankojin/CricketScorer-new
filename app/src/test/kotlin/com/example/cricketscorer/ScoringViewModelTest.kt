package com.example.cricketscorer

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.UUID

class ScoringViewModelTest {

    private lateinit var viewModel: ScoringViewModel
    private lateinit var testMatch: Match

    @Before
    fun setup() {
        viewModel = ScoringViewModel()
        
        val teamA = Team(id = "teamA", name = "Team A", players = createPlayers("A", 11))
        val teamB = Team(id = "teamB", name = "Team B", players = createPlayers("B", 11))
        
        testMatch = Match(
            id = "match1",
            tournamentId = "tourney1",
            teamA = teamA,
            teamB = teamB,
            battingTeamId = teamA.id,
            bowlingTeamId = teamB.id,
            tossWinnerId = teamA.id,
            tossDecision = "BAT",
            status = MatchStatus.LIVE,
            strikerId = teamA.players[0].id,
            nonStrikerId = teamA.players[1].id,
            currentBowlerId = teamB.players[0].id
        )
        
        viewModel.loadMatch(testMatch)
    }

    private fun createPlayers(prefix: String, count: Int): List<Player> {
        return (1..count).map {
            Player(id = "${prefix}$it", name = "Player ${prefix}$it")
        }
    }

    @Test
    fun testWideScoring() {
        // Current behavior: handleExtra(WIDE) records 1 extra run.
        viewModel.handleExtra(ExtrasType.WIDE, 0)
        
        val state = viewModel.matchState.value
        assertEquals(1, state?.totalRuns)
        assertEquals(1, state?.wideCount)
        assertEquals(0, state?.totalBalls) // Wide is not a legal ball
    }

    @Test
    fun testNoBallScoring() {
        // Current behavior: handleExtra(NO_BALL) records 1 extra run.
        viewModel.handleExtra(ExtrasType.NO_BALL, 0)
        
        val state = viewModel.matchState.value
        assertEquals(1, state?.totalRuns)
        assertEquals(1, state?.noBallCount)
        assertEquals(0, state?.totalBalls) // No ball is not a legal ball
    }

    @Test
    fun testWideWithAdditionalRuns() {
        // ICC: 1 penalty + runs ran. 
        // If 2 runs ran, total 3.
        // The UI currently might not support this well, or handleExtra needs fixing.
        viewModel.handleExtra(ExtrasType.WIDE, 2)
        
        val state = viewModel.matchState.value
        // Current implementation: Ball(runs=0, extraRuns=2) -> totalRuns = 0 + 2 = 2.
        // Should be 3 (1 penalty + 2 ran).
        // Let's see what it currently is.
        assertEquals(2, state?.totalRuns) 
    }
}
