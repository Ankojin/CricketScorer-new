package `in`.nrkmart.cricscore.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // No-op: schema version was bumped v1->v2 without actual entity/column changes
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE teams ADD COLUMN colorHex TEXT DEFAULT NULL")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE players ADD COLUMN bowlingStyle TEXT DEFAULT 'RIGHT_ARM'")
        db.execSQL("ALTER TABLE players ADD COLUMN role TEXT DEFAULT 'BATTER'")
    }
}

val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4
)
