package com.xbertz.onsite.backend.auth

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.Payload
import com.xbertz.onsite.backend.config.AppConfig
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Supabase Auth puts the Supabase user id in the standard "sub" claim; this backend never
 * issues or checks passwords, it only verifies a token minted elsewhere and trusts its
 * claims. Real Supabase projects sign with an asymmetric ES256 key, published (by "kid") at
 * the project's JWKS endpoint - [JwkProviderBuilder] caches keys by kid and refetches on an
 * unrecognized one, so a Supabase-side key rotation is picked up automatically instead of
 * needing a backend restart (a hand-rolled fetch-once-at-startup verifier would otherwise
 * permanently 401 every real login once Supabase rotates its signing key). [devAuthRoutes]
 * mints HS256 tokens with a shared dev secret for local testing. Both verifiers are always
 * registered so either kind of token is accepted; only DEV_AUTH_ENABLED lets anyone
 * actually mint a dev one.
 */
private const val AUTH_JWT_SUPABASE = "auth-jwt-supabase"
private const val AUTH_JWT_DEV = "auth-jwt-dev"
val AUTH_JWT = arrayOf(AUTH_JWT_SUPABASE, AUTH_JWT_DEV)

private fun validatePrincipal(payload: Payload): JWTPrincipal? {
    val subject = payload.subject ?: return null
    val hasEmail = payload.getClaim("email")?.asString() != null
    val validSubject = runCatching { UUID.fromString(subject) }.isSuccess
    return if (hasEmail && validSubject) JWTPrincipal(payload) else null
}

fun AuthenticationConfig.configureSupabaseJwt(config: AppConfig) {
    val jwksUrl = URI("${config.supabaseProjectUrl}/auth/v1/.well-known/jwks.json").toURL()
    val jwkProvider = JwkProviderBuilder(jwksUrl)
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    jwt(AUTH_JWT_SUPABASE) {
        verifier(jwkProvider, "${config.supabaseProjectUrl}/auth/v1") {
            acceptLeeway(5)
        }
        validate { credential -> validatePrincipal(credential.payload) }
    }

    jwt(AUTH_JWT_DEV) {
        verifier(
            JWT.require(Algorithm.HMAC256(config.supabaseJwtSecret))
                .withIssuer(config.supabaseJwtIssuer)
                .build()
        )
        validate { credential -> validatePrincipal(credential.payload) }
    }
}

data class AuthenticatedUser(val userId: UUID, val email: String)

fun JWTPrincipal.toAuthenticatedUser(): AuthenticatedUser =
    AuthenticatedUser(
        userId = UUID.fromString(payload.subject),
        email = payload.getClaim("email").asString(),
    )
