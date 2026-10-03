package `in`.nrkmart.cricscore.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import `in`.nrkmart.cricscore.BuildConfig
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

@Database(
    entities = [
        TournamentEntity::class,
        TeamEntity::class,
        PlayerEntity::class,
        MatchEntity::class,
        BallEntity::class
    ],
    version = 4,
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
        private val INSTANCES = ConcurrentHashMap<String, CricketDatabase>()

        fun databaseNameForProfile(userId: String?): String {
            if (userId.isNullOrBlank()) return "cricket_database"
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(userId.toByteArray(Charsets.UTF_8))
                .take(16)
                .joinToString("") { byte -> "%02x".format(byte) }
            return "cricket_database_user_$digest"
        }

        // Every future version bump of CricketDatabase must add a corresponding Migration(n, n+1)
        // to Migrations.kt and include it in ALL_MIGRATIONS.
        fun getInstance(context: Context, userId: String? = null): CricketDatabase {
            val databaseName = databaseNameForProfile(userId)
            return INSTANCES[databaseName] ?: synchronized(this) {
                INSTANCES[databaseName]?.let { return@synchronized it }
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    CricketDatabase::class.java,
                    databaseName
                )
                .addMigrations(*ALL_MIGRATIONS)

                if (BuildConfig.DEBUG) {
                    builder.fallbackToDestructiveMigration(dropAllTables = true)
                }

                val instance = builder.build()
                INSTANCES[databaseName] = instance
                instance
            }
        }
    }
}
