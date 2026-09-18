package com.xbertz.onsite.backend.connections

import com.xbertz.onsite.backend.auth.AUTH_JWT
import com.xbertz.onsite.backend.auth.toAuthenticatedUser
import com.xbertz.onsite.backend.identity.requestedAccountId
import com.xbertz.onsite.backend.identity.resolveActiveAccount
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

fun Route.connectionRoutes(repository: ConnectionsRepository) {
    authenticate(*AUTH_JWT) {
        post("/v1/accounts/{accountId}/connection-invites") {
            val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
            val accountId = UUID.fromString(call.parameters["accountId"])
            resolveActiveAccount(user.userId, accountId).requireOwner()
            val req = call.receive<CreateConnectionInviteRequest>()
            val invite = withContext(Dispatchers.IO) { repository.createInvite(accountId, req.email) }
            call.respond(HttpStatusCode.Created, invite)
        }

        route("/v1/me/connection-invites") {
            get {
                val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
                call.respond(withContext(Dispatchers.IO) { repository.pendingInvitesFor(user.email) })
            }
            post("/{inviteId}/accept") {
                val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
                val active = resolveActiveAccount(user.userId, call.requestedAccountId())
                val inviteId = UUID.fromString(call.parameters["inviteId"])
                val req = call.receive<AcceptConnectionInviteRequest>()
                val connection = withContext(Dispatchers.IO) {
                    repository.acceptInvite(inviteId, user.email, active.accountId, user.userId, UUID.fromString(req.companyId))
                }
                call.respond(connection)
            }
        }

        get("/v1/connections") {
            val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
            val active = resolveActiveAccount(user.userId, call.requestedAccountId())
            active.requireOwner()
            call.respond(withContext(Dispatchers.IO) { repository.connectionsForEmployer(active.accountId) })
        }

        get("/v1/me/connections") {
            val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
            call.respond(withContext(Dispatchers.IO) { repository.connectionsForWorker(user.userId) })
        }

        delete("/v1/connections/{id}") {
            val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
            val active = resolveActiveAccount(user.userId, call.requestedAccountId())
            val id = UUID.fromString(call.parameters["id"])
            val revoked = withContext(Dispatchers.IO) { repository.revoke(id, active.accountId, user.userId) }
            call.respond(if (revoked) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
        }

        post("/v1/connections/{id}/planned-jobs") {
            val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
            val active = resolveActiveAccount(user.userId, call.requestedAccountId())
            active.requireOwner()
            val id = UUID.fromString(call.parameters["id"])
            val req = call.receive<ConnectionPlannedJobRequest>()
            val job = withContext(Dispatchers.IO) { repository.createPlannedJobForConnection(id, active.accountId, user.userId, req) }
            call.respond(HttpStatusCode.Created, job)
        }

        get("/v1/connections/{id}/sessions") {
            val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
            val active = resolveActiveAccount(user.userId, call.requestedAccountId())
            active.requireOwner()
            val id = UUID.fromString(call.parameters["id"])
            call.respond(withContext(Dispatchers.IO) { repository.sessionsForConnection(id, active.accountId) })
        }
    }
}
