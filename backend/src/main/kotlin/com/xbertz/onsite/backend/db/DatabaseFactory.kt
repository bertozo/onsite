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
