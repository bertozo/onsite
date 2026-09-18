package com.xbertz.onsite.backend.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWTVerifier
import com.auth0.jwt.interfaces.Payload
import com.xbertz.onsite.backend.config.AppConfig
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import java.util.UUID

/**
 * Supabase Auth puts the Supabase user id in the standard "sub" claim; this backend never
 * issues or checks passwords, it only verifies a token minted elsewhere and trusts its
 * claims. Real Supabase projects sign with an asymmetric ES256 key (fetched from the
 * project's public JWKS endpoint - see [fetchSupabaseEcPublicKey]), while [devAuthRoutes]
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
    val supabasePublicKey = fetchSupabaseEcPublicKey(config.supabaseProjectUrl)
    val supabaseVerifier: JWTVerifier = JWT.require(Algorithm.ECDSA256(supabasePublicKey, null))
        .withIssuer("${config.supabaseProjectUrl}/auth/v1")
        .build()

    jwt(AUTH_JWT_SUPABASE) {
        verifier(supabaseVerifier)
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
