package `in`.nrkmart.cricscore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScoringViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: ScoringViewModel
    private lateinit var testMatch: Match

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
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

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createPlayers(prefix: String, count: Int): List<Player> {
        return (1..count).map {
            Player(id = "${prefix}$it", name = "Player ${prefix}$it")
        }
    }

    @Test
    fun testWideScoring() {
        viewModel.handleExtra(ExtrasType.WIDE, 0)
        
        val state = viewModel.matchState.value
        assertEquals(1, state?.totalRuns)
        assertEquals(1, state?.wideCount)
        assertEquals(0, state?.totalBalls)
    }

    @Test
    fun testNoBallScoring() {
        viewModel.handleExtra(ExtrasType.NO_BALL, 0)
        
        val state = viewModel.matchState.value
        assertEquals(1, state?.totalRuns)
        assertEquals(1, state?.noBallCount)
        assertEquals(0, state?.totalBalls)
    }

    @Test
    fun testWideWithAdditionalRuns() {
        viewModel.handleExtra(ExtrasType.WIDE, 2)
        
        val state = viewModel.matchState.value
        assertEquals(3, state?.totalRuns) 
    }
}
