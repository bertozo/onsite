package com.xbertz.onsite.backend

import com.xbertz.onsite.backend.auth.configureSupabaseJwt
import com.xbertz.onsite.backend.auth.devAuthRoutes
import com.xbertz.onsite.backend.config.AppConfig
import com.xbertz.onsite.backend.connections.ConnectionsRepository
import com.xbertz.onsite.backend.connections.connectionRoutes
import com.xbertz.onsite.backend.db.DatabaseFactory
import com.xbertz.onsite.backend.domain.CompaniesRepository
import com.xbertz.onsite.backend.domain.InvoicesRepository
import com.xbertz.onsite.backend.domain.JobTypesRepository
import com.xbertz.onsite.backend.domain.PlannedJobsRepository
import com.xbertz.onsite.backend.domain.SitesRepository
import com.xbertz.onsite.backend.domain.TrackingSessionsRepository
import com.xbertz.onsite.backend.domain.companyRoutes
import com.xbertz.onsite.backend.domain.invoiceRoutes
import com.xbertz.onsite.backend.domain.jobTypeRoutes
import com.xbertz.onsite.backend.domain.plannedJobRoutes
import com.xbertz.onsite.backend.domain.siteRoutes
import com.xbertz.onsite.backend.domain.trackingSessionRoutes
import com.xbertz.onsite.backend.identity.IdentityRepository
import com.xbertz.onsite.backend.identity.InviteRepository
import com.xbertz.onsite.backend.identity.identityRoutes
import com.xbertz.onsite.backend.identity.inviteRoutes
import com.xbertz.onsite.backend.plugins.configureStatusPages
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

fun main() {
    val config = AppConfig.fromEnv()
    DatabaseFactory.connect(config)

    embeddedServer(Netty, port = config.port) {
        module(config)
    }.start(wait = true)
}

fun Application.module(config: AppConfig) {
    install(CallLogging)

    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }

    install(CORS) {
        anyHost() // tightened once the web app's real origin is known (Phase 6)
        allowHeader("Authorization")
        allowHeader("Content-Type")
        allowHeader("X-Account-Id")
    }

    install(Authentication) {
        configureSupabaseJwt(config)
    }

    configureStatusPages()

    val identityRepository = IdentityRepository()
    val inviteRepository = InviteRepository()
    val connectionsRepository = ConnectionsRepository()
    val companiesRepository = CompaniesRepository()
    val sitesRepository = SitesRepository()
    val jobTypesRepository = JobTypesRepository()
    val invoicesRepository = InvoicesRepository()
    val trackingSessionsRepository = TrackingSessionsRepository()
    val plannedJobsRepository = PlannedJobsRepository()

    routing {
        devAuthRoutes(config)
        identityRoutes(identityRepository)
        inviteRoutes(inviteRepository)
        connectionRoutes(connectionsRepository)
        companyRoutes(companiesRepository)
        siteRoutes(sitesRepository)
        jobTypeRoutes(jobTypesRepository)
        invoiceRoutes(invoicesRepository)
        trackingSessionRoutes(trackingSessionsRepository)
        plannedJobRoutes(plannedJobsRepository)
    }
}
