package com.example.cricketscorer.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        TournamentEntity::class,
        TeamEntity::class,
        PlayerEntity::class,
        MatchEntity::class,
        BallEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CricketDatabase : RoomDatabase() {
    abstract fun tournamentDao(): TournamentDao
    abstract fun teamDao(): TeamDao
    abstract fun playerDao(): PlayerDao
    abstract fun matchDao(): MatchDao
    abstract fun ballDao(): BallDao

    companion object {
        @Volatile
        private var INSTANCE: CricketDatabase? = null

        fun getInstance(context: Context): CricketDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CricketDatabase::class.java,
                    "cricket_database"
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
