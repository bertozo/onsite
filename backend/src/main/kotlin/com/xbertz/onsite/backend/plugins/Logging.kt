package com.xbertz.onsite.backend.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.callloging.processingTimeMillis
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.util.AttributeKey
import org.slf4j.event.Level
import java.util.UUID

/**
 * One log line per request, carrying what it takes to answer the only question that
 * matters in support: "the app showed me an error at 14:02" - which needs the id the
 * client saw, who called, and which account the call acted against.
 *
 * The id is the client's own X-Request-Id when it sends a sane one (the Android and web
 * clients both do), and is minted here otherwise. It goes back out on the response header
 * and into every error body (see StatusPages), which is what makes a user-reported
 * failure findable at all.
 *
 * userId/accountId are recorded mid-call - by the JWT validator and by
 * [com.xbertz.onsite.backend.identity.activeAccount] - and read back when CallLogging
 * formats its line, after the handler is done. They are call attributes rather than MDC
 * entries on purpose: the routes hand their work to Dispatchers.IO and SLF4J's MDC is
 * thread local, so an MDC value written inside a route would not survive the hop back.
 */
private val UserIdKey = AttributeKey<String>("logUserId")
private val AccountIdKey = AttributeKey<String>("logAccountId")

fun ApplicationCall.rememberUserForLog(userId: UUID) {
    attributes.put(UserIdKey, userId.toString())
}

fun ApplicationCall.rememberAccountForLog(accountId: UUID) {
    attributes.put(AccountIdKey, accountId.toString())
}

/** The request id as the client sees it, for error bodies and log messages. */
val ApplicationCall.requestId: String
    get() = callId ?: "-"

/**
 * Endpoints called on a timer by machines: a probe every ten seconds and a scrape every
 * fifteen would bury every line that is actually about a user. A failing readiness check
 * logs itself instead (see plugins/Health.kt).
 */
private val UNLOGGED_PATHS = setOf("/health", "/health/ready", "/metrics")

/** A client-supplied id lands in every line about this call, so keep it short and boring. */
private const val MAX_REQUEST_ID_LENGTH = 64
private val REQUEST_ID_SHAPE = Regex("[A-Za-z0-9_.:-]+")

fun Application.configureLogging() {
    // Installed before CallLogging on purpose: the id has to exist by the time the MDC
    // entry below is evaluated, and plugins run in installation order.
    install(CallId) {
        // Deliberately not `header(...)`, which would 400 a request whose id fails
        // verification: an odd id from a client is worth ignoring, never worth failing a
        // tradesperson's sync over. An unusable one falls through to `generate`.
        retrieve { call ->
            call.request.headers[HttpHeaders.XRequestId]
                ?.trim()
                ?.takeIf { it.length <= MAX_REQUEST_ID_LENGTH && REQUEST_ID_SHAPE.matches(it) }
        }
        generate { UUID.randomUUID().toString() }
        replyToHeader(HttpHeaders.XRequestId)
    }

    install(CallLogging) {
        level = Level.INFO
        mdc("requestId") { it.callId }
        // CORS preflights would double every logged request and say nothing about the app.
        filter { it.request.httpMethod != HttpMethod.Options && it.request.path() !in UNLOGGED_PATHS }
        format { call ->
            // Null when nothing in the pipeline responded - a route that threw past
            // StatusPages, which is worth seeing as such rather than as a blank status.
            val status = call.response.status()?.value?.toString() ?: "unhandled"
            buildString {
                append(call.request.httpMethod.value).append(' ').append(call.request.path())
                append(" status=").append(status)
                append(" durationMs=").append(call.processingTimeMillis())
                call.attributes.getOrNull(UserIdKey)?.let { append(" userId=").append(it) }
                call.attributes.getOrNull(AccountIdKey)?.let { append(" accountId=").append(it) }
            }
        }
    }
}
