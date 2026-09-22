package com.xbertz.onsite.backend

import com.xbertz.onsite.backend.auth.configureSupabaseJwt
import com.xbertz.onsite.backend.auth.devAuthRoutes
import com.xbertz.onsite.backend.config.AppConfig
import com.xbertz.onsite.backend.connections.ConnectionsRepository
import com.xbertz.onsite.backend.connections.connectionRoutes
import com.xbertz.onsite.backend.db.DatabaseFactory
import com.xbertz.onsite.backend.domain.ClientsRepository
import com.xbertz.onsite.backend.domain.InvoicesRepository
import com.xbertz.onsite.backend.domain.JobTypesRepository
import com.xbertz.onsite.backend.domain.PlannedJobsRepository
import com.xbertz.onsite.backend.domain.SitesRepository
import com.xbertz.onsite.backend.domain.TrackingSessionsRepository
import com.xbertz.onsite.backend.domain.clientRoutes
import com.xbertz.onsite.backend.domain.invoiceRoutes
import com.xbertz.onsite.backend.domain.jobTypeRoutes
import com.xbertz.onsite.backend.domain.plannedJobRoutes
import com.xbertz.onsite.backend.domain.siteRoutes
import com.xbertz.onsite.backend.domain.trackingSessionRoutes
import com.xbertz.onsite.backend.identity.IdentityRepository
import com.xbertz.onsite.backend.identity.InviteRepository
import com.xbertz.onsite.backend.identity.identityRoutes
import com.xbertz.onsite.backend.identity.inviteRoutes
import com.xbertz.onsite.backend.plugins.configureLogging
import com.xbertz.onsite.backend.plugins.configureMetrics
import com.xbertz.onsite.backend.plugins.configureStatusPages
import com.xbertz.onsite.backend.plugins.healthRoutes
import com.xbertz.onsite.backend.plugins.newMeterRegistry
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import javax.sql.DataSource

private val logger = LoggerFactory.getLogger("com.xbertz.onsite.backend.Application")

fun main() {
    val config = AppConfig.fromEnv()
    // Nothing secret here, and it answers the questions a deploy actually raises: which
    // port, which database, is dev login open, and why the log looks the way it does.
    logger.info(
        "starting onsite-backend version={} env={} port={} db={} devAuth={} logLevel={} logFormat={} metrics={}",
        config.appVersion,
        config.appEnv,
        config.port,
        config.dbUrl.substringBefore('?'),
        config.devAuthEnabled,
        config.logLevel,
        config.logFormat,
        if (config.metricsToken == null) "collected" else "exposed",
    )
    // Created here rather than inside the module: the connection pool has to be handed the
    // same registry at construction time for its own metrics to exist at all.
    val meterRegistry = newMeterRegistry()
    val dataSource = DatabaseFactory.connect(config, meterRegistry)

    embeddedServer(Netty, port = config.port) {
        module(config, dataSource, meterRegistry)
    }.start(wait = true)
}

fun Application.module(
    config: AppConfig,
    dataSource: DataSource,
    meterRegistry: PrometheusMeterRegistry = newMeterRegistry(),
) {
    configureLogging()
    configureMetrics(config, meterRegistry)

    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }

    install(CORS) {
        anyHost() // tightened once the web app's real origin is known (Phase 6)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader("Authorization")
        allowHeader("Content-Type")
        allowHeader("X-Account-Id")
        allowHeader(HttpHeaders.XRequestId)
        // Without the expose, the browser hides the echoed id from our own fetch() - and
        // the web client would have nothing to log next to a failed call.
        exposeHeader(HttpHeaders.XRequestId)
    }

    install(Authentication) {
        configureSupabaseJwt(config)
    }

    configureStatusPages()

    val identityRepository = IdentityRepository()
    val inviteRepository = InviteRepository()
    val connectionsRepository = ConnectionsRepository()
    val clientsRepository = ClientsRepository()
    val sitesRepository = SitesRepository()
    val jobTypesRepository = JobTypesRepository()
    val invoicesRepository = InvoicesRepository()
    val trackingSessionsRepository = TrackingSessionsRepository()
    val plannedJobsRepository = PlannedJobsRepository()

    routing {
        healthRoutes(dataSource, config)
        devAuthRoutes(config)
        identityRoutes(identityRepository)
        inviteRoutes(inviteRepository)
        connectionRoutes(connectionsRepository)
        clientRoutes(clientsRepository)
        siteRoutes(sitesRepository)
        jobTypeRoutes(jobTypesRepository)
        invoiceRoutes(invoicesRepository)
        trackingSessionRoutes(trackingSessionsRepository)
        plannedJobRoutes(plannedJobsRepository)
    }
}
