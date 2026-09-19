package com.xbertz.onsite.backend.plugins

import com.xbertz.onsite.backend.connections.ConnectionAccessDenied
import com.xbertz.onsite.backend.identity.AccountAccessDenied
import com.xbertz.onsite.backend.identity.EmailAlreadyRegistered
import com.xbertz.onsite.backend.identity.OwnerRequired
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory

fun Application.configureStatusPages() {
    val logger = LoggerFactory.getLogger("StatusPages")
    install(StatusPages) {
        exception<NotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, mapOf("error" to (cause.message ?: "not found")))
        }
        exception<AccountAccessDenied> { call, _ ->
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "no membership in the requested account"))
        }
        exception<OwnerRequired> { call, _ ->
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "only the account owner can do this"))
        }
        exception<ConnectionAccessDenied> { call, _ ->
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "caller is not party to this connection"))
        }
        exception<EmailAlreadyRegistered> { call, cause ->
            call.respond(HttpStatusCode.Conflict, mapOf("error" to "email already registered under a different identity: ${cause.email}"))
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (cause.message ?: "bad request")))
        }
        exception<Throwable> { call, cause ->
            logger.error("Unhandled error", cause)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "internal error"))
        }
    }
}
