package com.xbertz.onsite.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TrackingSession::class, Client::class, Profile::class, Site::class, JobType::class,
        PlannedJob::class, Invoice::class, SyncMapping::class
    ],
    version = 15,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackingSessionDao(): TrackingSessionDao
    abstract fun clientDao(): ClientDao
    abstract fun profileDao(): ProfileDao
    abstract fun siteDao(): SiteDao
    abstract fun jobTypeDao(): JobTypeDao
    abstract fun plannedJobDao(): PlannedJobDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun syncDao(): SyncDao

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

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Hourly rates: a default per company, an optional override per session.
                db.execSQL("ALTER TABLE companies ADD COLUMN hourlyRate REAL")
                db.execSQL("ALTER TABLE tracking_sessions ADD COLUMN hourlyRate REAL")
                // Sessions remember which invoice billed them.
                db.execSQL("ALTER TABLE tracking_sessions ADD COLUMN invoiceId INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS invoices (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "number TEXT NOT NULL, " +
                        "companyName TEXT NOT NULL, " +
                        "periodStartEpochDay INTEGER NOT NULL, " +
                        "periodEndEpochDay INTEGER NOT NULL, " +
                        "issueDateEpochDay INTEGER NOT NULL, " +
                        "totalHours REAL NOT NULL, " +
                        "totalAmount REAL, " +
                        "hourlyRate REAL, " +
                        "status TEXT NOT NULL, " +
                        "sentAtMillis INTEGER, " +
                        "paidAtMillis INTEGER, " +
                        "pdfPath TEXT NOT NULL, " +
                        "notes TEXT, " +
                        "createdAtMillis INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Maps a local row to the backend's UUID for it, per entity type - see SyncEngine.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_mapping (" +
                        "entityType TEXT NOT NULL, " +
                        "localId INTEGER NOT NULL, " +
                        "remoteId TEXT NOT NULL, " +
                        "lastSyncedAtMillis INTEGER NOT NULL, " +
                        "PRIMARY KEY (entityType, localId))"
                )
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // The backend UUID of the account member this job is assigned to (see PlannedJobDto);
                // null means "not assigned to anyone specific" (an OWNER's own general schedule item).
                db.execSQL("ALTER TABLE planned_jobs ADD COLUMN assignedUserId TEXT")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // "Company" became "Client" everywhere (table, columns, sync entity type). The
                // three companyName columns are renamed by rebuilding their tables: RENAME COLUMN
                // needs SQLite 3.25 (API 30) and minSdk is 26.
                db.execSQL("ALTER TABLE companies RENAME TO clients")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tracking_sessions_new` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `clientName` TEXT,
                        `siteLabel` TEXT,
                        `jobTypeLabel` TEXT,
                        `startTimestampMillis` INTEGER NOT NULL,
                        `startLatitude` REAL NOT NULL,
                        `startLongitude` REAL NOT NULL,
                        `stopTimestampMillis` INTEGER,
                        `stopLatitude` REAL,
                        `stopLongitude` REAL,
                        `hourlyRate` REAL,
                        `invoiceId` INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO tracking_sessions_new (id, clientName, siteLabel, jobTypeLabel, startTimestampMillis,
                        startLatitude, startLongitude, stopTimestampMillis, stopLatitude, stopLongitude, hourlyRate, invoiceId)
                    SELECT id, companyName, siteLabel, jobTypeLabel, startTimestampMillis,
                        startLatitude, startLongitude, stopTimestampMillis, stopLatitude, stopLongitude, hourlyRate, invoiceId
                    FROM tracking_sessions
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE tracking_sessions")
                db.execSQL("ALTER TABLE tracking_sessions_new RENAME TO tracking_sessions")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `planned_jobs_new` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `dateEpochDay` INTEGER NOT NULL,
                        `startMinute` INTEGER NOT NULL,
                        `endMinute` INTEGER,
                        `clientName` TEXT,
                        `siteLabel` TEXT,
                        `jobTypeLabel` TEXT,
                        `notes` TEXT,
                        `assignedUserId` TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO planned_jobs_new (id, dateEpochDay, startMinute, endMinute, clientName, siteLabel, jobTypeLabel, notes, assignedUserId)
                    SELECT id, dateEpochDay, startMinute, endMinute, companyName, siteLabel, jobTypeLabel, notes, assignedUserId
                    FROM planned_jobs
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE planned_jobs")
                db.execSQL("ALTER TABLE planned_jobs_new RENAME TO planned_jobs")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `invoices_new` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `number` TEXT NOT NULL,
                        `clientName` TEXT NOT NULL,
                        `periodStartEpochDay` INTEGER NOT NULL,
                        `periodEndEpochDay` INTEGER NOT NULL,
                        `issueDateEpochDay` INTEGER NOT NULL,
                        `totalHours` REAL NOT NULL,
                        `totalAmount` REAL,
                        `hourlyRate` REAL,
                        `status` TEXT NOT NULL,
                        `sentAtMillis` INTEGER,
                        `paidAtMillis` INTEGER,
                        `pdfPath` TEXT NOT NULL,
                        `notes` TEXT,
                        `createdAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO invoices_new (id, number, clientName, periodStartEpochDay, periodEndEpochDay, issueDateEpochDay,
                        totalHours, totalAmount, hourlyRate, status, sentAtMillis, paidAtMillis, pdfPath, notes, createdAtMillis)
                    SELECT id, number, companyName, periodStartEpochDay, periodEndEpochDay, issueDateEpochDay,
                        totalHours, totalAmount, hourlyRate, status, sentAtMillis, paidAtMillis, pdfPath, notes, createdAtMillis
                    FROM invoices
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE invoices")
                db.execSQL("ALTER TABLE invoices_new RENAME TO invoices")

                db.execSQL("UPDATE sync_mapping SET entityType = 'client' WHERE entityType = 'company'")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // The profile now syncs with the backend (see SyncEngine.syncProfile); this is the
                // edit time last-write-wins compares. 0 = "saved before sync existed".
                db.execSQL("ALTER TABLE profile ADD COLUMN updatedAtMillis INTEGER NOT NULL DEFAULT 0")
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
                    MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
                    MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15
                ).build().also { INSTANCE = it }
            }
        }
    }
}
