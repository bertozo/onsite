package com.xbertz.onsite.backend.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.xbertz.onsite.backend.config.AppConfig
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import java.util.UUID

/**
 * Supabase Auth signs its access tokens with the project's JWT secret (HS256) and puts the
 * Supabase user id in the standard "sub" claim. This backend never issues or checks
 * passwords - it only verifies a token minted elsewhere and trusts its claims.
 */
const val AUTH_JWT = "auth-jwt"

fun AuthenticationConfig.configureSupabaseJwt(config: AppConfig) {
    jwt(AUTH_JWT) {
        verifier(
            JWT.require(Algorithm.HMAC256(config.supabaseJwtSecret))
                .withIssuer(config.supabaseJwtIssuer)
                .build()
        )
        validate { credential ->
            val subject = credential.payload.subject ?: return@validate null
            val hasEmail = credential.payload.getClaim("email")?.asString() != null
            val validSubject = runCatching { UUID.fromString(subject) }.isSuccess
            if (hasEmail && validSubject) JWTPrincipal(credential.payload) else null
        }
    }
}

data class AuthenticatedUser(val userId: UUID, val email: String)

fun JWTPrincipal.toAuthenticatedUser(): AuthenticatedUser =
    AuthenticatedUser(
        userId = UUID.fromString(payload.subject),
        email = payload.getClaim("email").asString(),
    )
