package com.xbertz.onsite.backend.plugins

import com.xbertz.onsite.backend.config.AppConfig
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import javax.sql.DataSource

private val logger = LoggerFactory.getLogger("com.xbertz.onsite.backend.health")

/** Long enough for a healthy pool to answer, short enough not to hold up a probe. */
private const val DB_CHECK_TIMEOUT_MILLIS = 3_000L

/**
 * Two endpoints, both unauthenticated, because the caller is a container probe or a
 * platform's load balancer:
 *
 * - `/health` is liveness: the process is up and answering. It deliberately touches
 *   nothing else, so a database outage never gets the app killed and restarted - restarting
 *   it would not bring Postgres back.
 * - `/health/ready` is readiness: it actually asks the database. This is the one worth
 *   watching, because "backend up, Postgres unreachable" is this app's real failure mode
 *   and it answers `ok` to any check that doesn't look.
 *
 * Neither appears in the request log (see plugins/Logging.kt): a probe every ten seconds
 * would bury everything else. A failing readiness check logs itself instead.
 */
fun Route.healthRoutes(dataSource: DataSource, config: AppConfig) {
    get("/health") {
        call.respond(
            mapOf(
                "status" to "ok",
                "service" to "onsite-backend",
                "version" to config.appVersion,
                "env" to config.appEnv,
            ),
        )
    }

    get("/health/ready") {
        // The timeout bounds the *response*, not the JDBC call: a borrowed-connection
        // attempt keeps blocking its IO thread until Hikari's own connectionTimeout gives
        // up. That is the tradeoff for not making every probe wait 30s on a dead database.
        val dbReachable = withTimeoutOrNull(DB_CHECK_TIMEOUT_MILLIS) {
            withContext(Dispatchers.IO) {
                runCatching {
                    dataSource.connection.use { connection ->
                        connection.createStatement().use { it.execute("SELECT 1") }
                    }
                }.isSuccess
            }
        } ?: false

        if (dbReachable) {
            call.respond(mapOf("status" to "ok", "db" to "ok"))
        } else {
            logger.warn("readiness failed: database unreachable")
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                mapOf("status" to "degraded", "db" to "unreachable"),
            )
        }
    }
}
