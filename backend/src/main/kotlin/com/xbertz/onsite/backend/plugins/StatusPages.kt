package com.xbertz.onsite.backend.plugins

import com.xbertz.onsite.backend.connections.ConnectionAccessDenied
import com.xbertz.onsite.backend.identity.AccountAccessDenied
import com.xbertz.onsite.backend.identity.EmailAlreadyRegistered
import com.xbertz.onsite.backend.identity.OwnerRequired
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.xbertz.onsite.backend.http")

/**
 * Every error response carries the request id, and every error response is logged: a
 * rejected call that leaves no trace is the reason "it just didn't save" is unanswerable.
 * The mapped 4xx are logged at WARN (the caller did something we refused - expected, but
 * worth seeing when a user swears they had permission) and only the unmapped ones at
 * ERROR with a stack trace, so ERROR keeps meaning "this backend is broken".
 */
private suspend fun ApplicationCall.respondError(
    status: HttpStatusCode,
    message: String,
    /** Defaults to [message]; passed separately when the response says more than the log should. */
    logReason: String = message,
) {
    logger.warn("rejected status={} reason={}", status.value, logReason)
    respond(status, mapOf("error" to message, "requestId" to requestId))
}

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<NotFoundException> { call, cause ->
            call.respondError(HttpStatusCode.NotFound, cause.message ?: "not found")
        }
        exception<AccountAccessDenied> { call, _ ->
            call.respondError(HttpStatusCode.Forbidden, "no membership in the requested account")
        }
        exception<OwnerRequired> { call, _ ->
            call.respondError(HttpStatusCode.Forbidden, "only the account owner can do this")
        }
        exception<ConnectionAccessDenied> { call, _ ->
            call.respondError(HttpStatusCode.Forbidden, "caller is not party to this connection")
        }
        exception<EmailAlreadyRegistered> { call, cause ->
            call.respondError(
                HttpStatusCode.Conflict,
                "email already registered under a different identity: ${cause.email}",
                // The caller already knows the address they sent; the log does not need a
                // copy of it (see docs/logging.md on what never goes in a log line).
                logReason = "email already registered under a different identity",
            )
        }
        // Thrown by Ktor itself (a malformed X-Account-Id, an unparseable JSON body).
        // Without this it fell through to the handler below and was logged - and reported
        // to the client - as a 500, which made a caller's own mistake look like an outage.
        exception<BadRequestException> { call, cause ->
            call.respondError(HttpStatusCode.BadRequest, cause.message ?: "bad request")
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respondError(HttpStatusCode.BadRequest, cause.message ?: "bad request")
        }
        exception<Throwable> { call, cause ->
            logger.error("unhandled error", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                // The message stays generic (it may quote SQL or a token); the id is what
                // the user can read back to us to find the stack trace above.
                mapOf("error" to "internal error", "requestId" to call.requestId),
            )
        }
    }
}
