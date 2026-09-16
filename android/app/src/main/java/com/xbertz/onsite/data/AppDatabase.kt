package com.xbertz.onsite.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TrackingSession::class, Company::class, Profile::class, Site::class, JobType::class, PlannedJob::class],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackingSessionDao(): TrackingSessionDao
    abstract fun companyDao(): CompanyDao
    abstract fun profileDao(): ProfileDao
    abstract fun siteDao(): SiteDao
    abstract fun jobTypeDao(): JobTypeDao
    abstract fun plannedJobDao(): PlannedJobDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `saved_locations` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `label` TEXT NOT NULL,
                        `address` TEXT,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `companies` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `name` TEXT NOT NULL,
                        `address` TEXT,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO companies (id, name, address, latitude, longitude, createdAtMillis)
                    SELECT id, label, address, latitude, longitude, createdAtMillis FROM saved_locations
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE saved_locations")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `profile` (
                        `id` INTEGER NOT NULL PRIMARY KEY,
                        `name` TEXT NOT NULL,
                        `phone` TEXT,
                        `email` TEXT,
                        `role` TEXT,
                        `photoPath` TEXT
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `companies_new` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `name` TEXT NOT NULL,
                        `abn` TEXT,
                        `phone` TEXT,
                        `createdAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO companies_new (id, name, createdAtMillis)
                    SELECT id, name, createdAtMillis FROM companies
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE companies")
                db.execSQL("ALTER TABLE companies_new RENAME TO companies")

                db.execSQL("ALTER TABLE profile ADD COLUMN bankBsb TEXT")
                db.execSQL("ALTER TABLE profile ADD COLUMN bankAccount TEXT")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sites` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `label` TEXT NOT NULL,
                        `address` TEXT,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE location_records ADD COLUMN companyName TEXT")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // The old two-row (START/STOP event) model is replaced by one row per
                // session; the user confirmed it's fine to lose this table's data.
                db.execSQL("DROP TABLE IF EXISTS location_records")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tracking_sessions` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `companyName` TEXT,
                        `siteLabel` TEXT,
                        `startTimestampMillis` INTEGER NOT NULL,
                        `startLatitude` REAL NOT NULL,
                        `startLongitude` REAL NOT NULL,
                        `stopTimestampMillis` INTEGER,
                        `stopLatitude` REAL,
                        `stopLongitude` REAL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `job_types` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `name` TEXT NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE tracking_sessions ADD COLUMN jobTypeLabel TEXT")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE companies ADD COLUMN email TEXT")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profile ADD COLUMN abn TEXT")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS planned_jobs (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "dateEpochDay INTEGER NOT NULL, " +
                        "startMinute INTEGER NOT NULL, " +
                        "endMinute INTEGER, " +
                        "companyName TEXT, " +
                        "siteLabel TEXT, " +
                        "jobTypeLabel TEXT, " +
                        "notes TEXT)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "onsite.db"
                ).addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                    MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10
                ).build().also { INSTANCE = it }
            }
        }
    }
}
