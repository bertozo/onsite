package com.xbertz.onsite.backend

import com.xbertz.onsite.backend.auth.configureSupabaseJwt
import com.xbertz.onsite.backend.config.AppConfig
import com.xbertz.onsite.backend.db.DatabaseFactory
import com.xbertz.onsite.backend.identity.IdentityRepository
import com.xbertz.onsite.backend.identity.identityRoutes
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
    }

    install(Authentication) {
        configureSupabaseJwt(config)
    }

    configureStatusPages()

    val identityRepository = IdentityRepository()

    routing {
        identityRoutes(identityRepository)
    }
}
