package com.example.cricketscorer.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.cricketscorer.BuildConfig

@Database(
    entities = [
        TournamentEntity::class,
        TeamEntity::class,
        PlayerEntity::class,
        MatchEntity::class,
        BallEntity::class
    ],
    version = 3,
    exportSchema = true
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

        // Every future version bump of CricketDatabase must add a corresponding Migration(n, n+1)
        // to Migrations.kt and include it in ALL_MIGRATIONS.
        fun getInstance(context: Context): CricketDatabase {
            return INSTANCE ?: synchronized(this) {
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    CricketDatabase::class.java,
                    "cricket_database"
                )
                .addMigrations(*ALL_MIGRATIONS)

                if (BuildConfig.DEBUG) {
                    builder.fallbackToDestructiveMigration(dropAllTables = true)
                }

                val instance = builder.build()
                INSTANCE = instance
                instance
            }
        }
    }
}
