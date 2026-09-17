package com.xbertz.onsite.backend.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.xbertz.onsite.backend.config.AppConfig
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Stand-in for Supabase Auth during local development, before a real Supabase project
 * exists: mints a token in the exact shape Supabase issues (same claims, same HS256
 * secret), so every other route is oblivious to which one signed it. Gated by
 * DEV_AUTH_ENABLED and must be off (the default) anywhere but a dev machine, since it
 * grants a session for any email with no password at all.
 */
@Serializable
data class DevLoginRequest(val email: String)

@Serializable
data class DevLoginResponse(val token: String)

fun Route.devAuthRoutes(config: AppConfig) {
    if (!config.devAuthEnabled) return

    post("/v1/dev/auth/login") {
        val req = call.receive<DevLoginRequest>()
        val email = req.email.trim().lowercase()
        if (email.isBlank() || !email.contains("@")) {
            throw IllegalArgumentException("a valid email is required")
        }
        val userId = UUID.nameUUIDFromBytes(email.toByteArray(StandardCharsets.UTF_8))
        val now = Instant.now()
        val token = JWT.create()
            .withSubject(userId.toString())
            .withClaim("email", email)
            .withClaim("role", "authenticated")
            .withIssuer(config.supabaseJwtIssuer)
            .withIssuedAt(now)
            .withExpiresAt(now.plus(1, ChronoUnit.DAYS))
            .sign(Algorithm.HMAC256(config.supabaseJwtSecret))
        call.respond(DevLoginResponse(token))
    }
}
