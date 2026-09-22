package com.xbertz.onsite.backend.db

import com.xbertz.onsite.backend.config.AppConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory
import org.flywaydb.core.Flyway
import io.micrometer.core.instrument.MeterRegistry
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import javax.sql.DataSource

object DatabaseFactory {
    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)

    /**
     * [meterRegistry] is optional so tests and tools can connect without one; when it is
     * passed, the pool publishes hikaricp_* (pending/active/idle), which is what shows a
     * pool filling up before requests start timing out.
     */
    fun connect(config: AppConfig, meterRegistry: MeterRegistry? = null): DataSource {
        val dataSource = hikariDataSource(config, meterRegistry)
        val migration = Flyway.configure()
            .dataSource(dataSource)
            // Without this, a migration Flyway can't resolve is silently skipped (not
            // failed) - which is exactly what happened when a service-file merge bug in the
            // Docker build's fat jar broke migration scanning: the app "started fine" against
            // a schema that had zero tables. Fail loudly instead (see build.gradle.kts's
            // shadowJar mergeServiceFiles comment for the actual bug this caught).
            .validateMigrationNaming(true)
            .load()
            .migrate()
        // The count is the interesting part: "0 applied, version 5" on a boot that was
        // supposed to migrate is the visible form of the silent-skip bug described above.
        logger.info(
            "database ready schemaVersion={} migrationsApplied={}",
            // targetSchemaVersion is null when there was nothing to apply, in which case
            // the schema simply stayed where it already was.
            migration.targetSchemaVersion ?: migration.initialSchemaVersion ?: "unknown",
            migration.migrationsExecuted,
        )
        Database.connect(dataSource)
        return dataSource
    }

    private fun hikariDataSource(config: AppConfig, meterRegistry: MeterRegistry?): HikariDataSource {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.dbUrl
            username = config.dbUser
            password = config.dbPassword
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            meterRegistry?.let { metricsTrackerFactory = MicrometerMetricsTrackerFactory(it) }
            validate()
        }
        return HikariDataSource(hikariConfig)
    }
}
