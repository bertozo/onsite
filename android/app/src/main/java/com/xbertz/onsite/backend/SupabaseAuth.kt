package com.xbertz.onsite.backend

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Talks directly to Supabase Auth's REST API (GoTrue) for email+password sign-up/sign-in
 * and password recovery - no Supabase SDK dependency, since this app only ever needs the
 * resulting access token, which it then hands to [BackendApi] exactly like a dev-login
 * token. The backend verifies that token's signature itself (see onsite-backend's
 * SupabaseJwks.kt); this object never talks to onsite-backend.
 */
private const val SUPABASE_URL = "https://bbxciadhznsdpdnwdynw.supabase.co"
private const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_gNu70Q44fANX4LmU-1gThQ_a1u4-pAg"

@Serializable
private data class SupabaseCredentialsRequest(val email: String, val password: String)

@Serializable
private data class SupabaseEmailRequest(val email: String)

@Serializable
private data class SupabaseVerifyRequest(val type: String, val email: String, val token: String)

@Serializable
private data class SupabasePasswordUpdateRequest(val password: String)

@Serializable
private data class SupabaseUserDto(
    val id: String? = null,
    /**
     * Supabase's anti-enumeration behaviour: signing up with an email that's already
     * registered returns HTTP 200 with a look-alike user instead of an error, but its
     * "identities" come back empty (a real new user's does not) - that's the only signal
     * that this "success" was actually a no-op.
     */
    val identities: List<JsonElement>? = null,
)

@Serializable
private data class SupabaseSessionDto(
    val access_token: String? = null,
    val user: SupabaseUserDto? = null,
)

@Serializable
private data class SupabaseErrorDto(
    val error_code: String? = null,
    val error: String? = null,
    val msg: String? = null,
    val error_description: String? = null,
)

sealed interface SupabaseAuthResult {
    data class SignedIn(val accessToken: String) : SupabaseAuthResult
    data object ConfirmationEmailSent : SupabaseAuthResult
}

class SupabaseAuthException(val code: String?) : Exception(code)

object SupabaseAuth {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun signUp(email: String, password: String): SupabaseAuthResult {
        val response = client.post("$SUPABASE_URL/auth/v1/signup") {
            apiKey()
            jsonBody(SupabaseCredentialsRequest(email, password))
        }
        if (!response.status.isSuccess()) throw response.toAuthException()
        val session: SupabaseSessionDto = response.body()
        return when {
            session.access_token != null -> SupabaseAuthResult.SignedIn(session.access_token)
            session.user?.identities?.isEmpty() == true -> throw SupabaseAuthException("user_already_exists")
            else -> SupabaseAuthResult.ConfirmationEmailSent
        }
    }

    suspend fun signIn(email: String, password: String): SupabaseAuthResult.SignedIn {
        val response = client.post("$SUPABASE_URL/auth/v1/token?grant_type=password") {
            apiKey()
            jsonBody(SupabaseCredentialsRequest(email, password))
        }
        if (!response.status.isSuccess()) throw response.toAuthException()
        val session: SupabaseSessionDto = response.body()
        val token = session.access_token ?: throw SupabaseAuthException(null)
        return SupabaseAuthResult.SignedIn(token)
    }

    /**
     * Sends the recovery email. The project's "Reset password" template is customized to
     * show the raw {{ .Token }} as a 6-digit code instead of a link, so the rest of the flow
     * ([verifyRecovery] + [updatePassword]) never needs a deep link or redirect URL - the
     * user just types the code back into the app.
     */
    suspend fun recover(email: String) {
        val response = client.post("$SUPABASE_URL/auth/v1/recover") {
            apiKey()
            jsonBody(SupabaseEmailRequest(email))
        }
        if (!response.status.isSuccess()) throw response.toAuthException()
    }

    /** Exchanges a recovery code for a session, proving the user controls that inbox. */
    suspend fun verifyRecovery(email: String, code: String): String {
        val response = client.post("$SUPABASE_URL/auth/v1/verify") {
            apiKey()
            jsonBody(SupabaseVerifyRequest(type = "recovery", email = email, token = code))
        }
        if (!response.status.isSuccess()) throw response.toAuthException()
        val session: SupabaseSessionDto = response.body()
        return session.access_token ?: throw SupabaseAuthException(null)
    }

    /** Sets a new password on the session obtained from [verifyRecovery]. */
    suspend fun updatePassword(accessToken: String, newPassword: String) {
        val response = client.put("$SUPABASE_URL/auth/v1/user") {
            apiKey()
            header("Authorization", "Bearer $accessToken")
            jsonBody(SupabasePasswordUpdateRequest(newPassword))
        }
        if (!response.status.isSuccess()) throw response.toAuthException()
    }

    private fun io.ktor.client.request.HttpRequestBuilder.apiKey() {
        header("apikey", SUPABASE_PUBLISHABLE_KEY)
    }

    private inline fun <reified T> io.ktor.client.request.HttpRequestBuilder.jsonBody(body: T) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun HttpResponse.toAuthException(): SupabaseAuthException {
        val error = runCatching { body<SupabaseErrorDto>() }.getOrNull()
        return SupabaseAuthException(error?.error_code ?: error?.error)
    }
}
