package com.example.cricketscorer.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TournamentDao {
    @Transaction
    @Query("SELECT * FROM tournaments")
    fun getAllTournamentsFlow(): Flow<List<TournamentWithDetails>>

    @Transaction
    @Query("SELECT * FROM tournaments WHERE id = :id")
    suspend fun getTournamentById(id: String): TournamentWithDetails?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTournament(tournament: TournamentEntity)

    @Query("DELETE FROM tournaments WHERE id = :id")
    suspend fun deleteTournamentById(id: String)
}

@Dao
interface TeamDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeam(team: TeamEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeams(teams: List<TeamEntity>)

    @Query("DELETE FROM teams WHERE id = :id")
    suspend fun deleteTeamById(id: String)
}

@Dao
interface PlayerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayer(player: PlayerEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayers(players: List<PlayerEntity>)

    @Query("DELETE FROM players WHERE id = :id")
    suspend fun deletePlayerById(id: String)

    @Query("UPDATE players SET teamId = NULL WHERE id = :playerId")
    suspend fun removePlayerFromTeam(playerId: String)
}

@Dao
interface MatchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatch(match: MatchEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatches(matches: List<MatchEntity>)

    @Query("DELETE FROM matches WHERE id = :id")
    suspend fun deleteMatchById(id: String)
}

@Dao
interface BallDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBall(ball: BallEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBalls(balls: List<BallEntity>)

    @Query("DELETE FROM balls WHERE matchId = :matchId")
    suspend fun deleteBallsByMatch(matchId: String)
}
