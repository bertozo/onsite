package com.xbertz.onsite.backend.identity

import com.xbertz.onsite.backend.auth.AUTH_JWT
import com.xbertz.onsite.backend.auth.toAuthenticatedUser
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Route.identityRoutes(repository: IdentityRepository) {
    authenticate(AUTH_JWT) {
        // Call right after Supabase sign-in/sign-up: provisions the personal account on
        // first login, no-op on every login after that.
        post("/v1/me/bootstrap") {
            val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
            val response = withContext(Dispatchers.IO) {
                repository.bootstrap(user.userId, user.email)
            }
            call.respond(response)
        }

        get("/v1/me") {
            val user = call.principal<JWTPrincipal>()!!.toAuthenticatedUser()
            val response = withContext(Dispatchers.IO) {
                repository.findMe(user.userId)
            }
            if (response == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "call /v1/me/bootstrap first"))
            } else {
                call.respond(response)
            }
        }
    }
}
