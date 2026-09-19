package com.xbertz.onsite.backend.db

import com.xbertz.onsite.backend.config.AppConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import javax.sql.DataSource

object DatabaseFactory {
    fun connect(config: AppConfig): DataSource {
        val dataSource = hikariDataSource(config)
        Flyway.configure()
            .dataSource(dataSource)
            // Without this, a migration Flyway can't resolve is silently skipped (not
            // failed) - which is exactly what happened when a service-file merge bug in the
            // Docker build's fat jar broke migration scanning: the app "started fine" against
            // a schema that had zero tables. Fail loudly instead (see build.gradle.kts's
            // shadowJar mergeServiceFiles comment for the actual bug this caught).
            .validateMigrationNaming(true)
            .load()
            .migrate()
        Database.connect(dataSource)
        return dataSource
    }

    private fun hikariDataSource(config: AppConfig): HikariDataSource {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.dbUrl
            username = config.dbUser
            password = config.dbPassword
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            validate()
        }
        return HikariDataSource(hikariConfig)
    }
}
