package com.xbertz.onsite.backend

import com.xbertz.onsite.log.AppLog
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.statement.request
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * `adb reverse tcp:8080 tcp:8080` makes a device's own loopback address reach the backend
 * running on the development machine - see onsite-backend/README.md. Swap this for the real
 * deployment's HTTPS URL once one exists.
 */
private const val BASE_URL = "http://127.0.0.1:8080"

private const val TAG = "Api"

/**
 * Minted per request and sent to the backend, which logs it and echoes it back (see the
 * backend's plugins/Logging.kt). It is the only thing that lets a line in a phone's log
 * file be matched against the server's own line for the same call.
 */
private const val HEADER_REQUEST_ID = "X-Request-Id"

@Serializable
data class AccountMembershipDto(
    val accountId: String,
    val accountName: String,
    val accountKind: String,
    val role: String,
)

@Serializable
data class MeResponse(
    val userId: String,
    val email: String,
    val displayName: String?,
    val memberships: List<AccountMembershipDto>,
)

@Serializable
data class CreateInviteRequest(val email: String)

@Serializable
data class InviteDto(
    val id: String,
    val accountId: String,
    val accountName: String,
    val email: String,
    val role: String,
    val status: String,
)

@Serializable
data class CreateConnectionInviteRequest(val email: String)

@Serializable
data class ConnectionInviteDto(
    val id: String,
    val employerAccountId: String,
    val employerAccountName: String,
    val email: String,
    val status: String,
)

@Serializable
data class AcceptConnectionInviteRequest(val clientId: String)

@Serializable
data class ConnectionDto(
    val id: String,
    val employerAccountId: String,
    val employerAccountName: String,
    val workerAccountId: String,
    val workerClientId: String,
    val workerClientName: String,
    val status: String,
)

/** The invoice-header details of the signed-in user; mirrors the backend's ProfileDto (no photo). */
@Serializable
data class ProfileDto(
    val name: String,
    val role: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val abn: String? = null,
    val bankBsb: String? = null,
    val bankAccount: String? = null,
    val updatedAtMillis: Long,
)

@Serializable
data class MemberDto(
    val userId: String,
    val email: String,
    val displayName: String?,
    val role: String,
)

@Serializable
data class ConnectionPlannedJobRequest(
    val id: String,
    val dateEpochDay: Long,
    val startMinute: Int,
    val endMinute: Int? = null,
    val siteLabel: String? = null,
    val jobTypeLabel: String? = null,
    val notes: String? = null,
)

@Serializable
data class ConnectionSessionDto(
    val id: String,
    val siteLabel: String?,
    val jobTypeLabel: String?,
    val startTimestampMillis: Long,
    val stopTimestampMillis: Long?,
)

@Serializable
data class ClientRequest(
    val id: String,
    val name: String,
    val abn: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val createdAtMillis: Long,
    val hourlyRate: Double? = null,
)

@Serializable
data class SiteRequest(
    val id: String,
    val label: String,
    val address: String? = null,
    val latitude: Double,
    val longitude: Double,
    val createdAtMillis: Long,
)

@Serializable
data class JobTypeRequest(
    val id: String,
    val name: String,
    val createdAtMillis: Long,
)

@Serializable
data class InvoiceRequest(
    val id: String,
    val number: String,
    val clientName: String,
    val periodStartEpochDay: Long,
    val periodEndEpochDay: Long,
    val issueDateEpochDay: Long,
    val totalHours: Double,
    val totalAmount: Double? = null,
    val hourlyRate: Double? = null,
    val status: String = "DRAFT",
    val sentAtMillis: Long? = null,
    val paidAtMillis: Long? = null,
    val pdfPath: String? = null,
    val notes: String? = null,
    val createdAtMillis: Long,
)

@Serializable
data class TrackingSessionRequest(
    val id: String,
    val clientName: String? = null,
    val siteLabel: String? = null,
    val jobTypeLabel: String? = null,
    val startTimestampMillis: Long,
    val startLatitude: Double,
    val startLongitude: Double,
    val stopTimestampMillis: Long? = null,
    val stopLatitude: Double? = null,
    val stopLongitude: Double? = null,
    val hourlyRate: Double? = null,
    val invoiceId: String? = null,
)

@Serializable
data class PlannedJobRequest(
    val id: String,
    val dateEpochDay: Long,
    val startMinute: Int,
    val endMinute: Int? = null,
    val clientName: String? = null,
    val siteLabel: String? = null,
    val jobTypeLabel: String? = null,
    val notes: String? = null,
    val assignedUserId: String? = null,
)

object BackendApi {
    /**
     * Which account the caller is acting as, sent as X-Account-Id on every call once set.
     * Null means "my own account" - the backend defaults to that when the header is absent.
     */
    var activeAccountId: String? = null

    private val http = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }

        // A rejected call has to *fail*. Without this, the calls that don't parse a body
        // (every create/update/delete) treated a 403 or a 500 as success, and SyncEngine
        // then wrote a SyncMapping saying the row was synced - the local row and the
        // server disagreeing from then on, silently and permanently.
        expectSuccess = true

        HttpResponseValidator {
            validateResponse { response ->
                val request = response.request
                AppLog.d(
                    TAG,
                    "${request.method.value} ${request.url.encodedPath} status=${response.status.value} " +
                        "requestId=${request.headers[HEADER_REQUEST_ID] ?: "-"}",
                )
            }
            // Where a non-2xx, and an unreachable backend, become a line someone can read.
            handleResponseExceptionWithRequest { cause, request ->
                val status = (cause as? ResponseException)?.response?.status?.value
                AppLog.w(
                    TAG,
                    "${request.method.value} ${request.url.encodedPath} failed status=${status ?: "-"} " +
                        "requestId=${request.headers[HEADER_REQUEST_ID] ?: "-"}",
                    cause,
                )
            }
        }
    }

    suspend fun bootstrap(token: String): MeResponse =
        http.post("$BASE_URL/v1/me/bootstrap") { bearerAuth(token) }.body()

    suspend fun me(token: String): MeResponse =
        http.get("$BASE_URL/v1/me") { bearerAuth(token) }.body()

    suspend fun createInvite(token: String, accountId: String, email: String): InviteDto =
        http.post("$BASE_URL/v1/accounts/$accountId/invites") {
            bearerAuth(token); jsonBody(CreateInviteRequest(email))
        }.body()

    suspend fun listMyInvites(token: String): List<InviteDto> =
        http.get("$BASE_URL/v1/me/invites") { bearerAuth(token) }.body()

    suspend fun acceptInvite(token: String, inviteId: String): InviteDto =
        http.post("$BASE_URL/v1/me/invites/$inviteId/accept") { bearerAuth(token) }.body()

    suspend fun createConnectionInvite(token: String, accountId: String, email: String): ConnectionInviteDto =
        http.post("$BASE_URL/v1/accounts/$accountId/connection-invites") {
            bearerAuth(token); jsonBody(CreateConnectionInviteRequest(email))
        }.body()

    suspend fun listMyConnectionInvites(token: String): List<ConnectionInviteDto> =
        http.get("$BASE_URL/v1/me/connection-invites") { bearerAuth(token) }.body()

    suspend fun acceptConnectionInvite(token: String, inviteId: String, clientId: String): ConnectionDto =
        http.post("$BASE_URL/v1/me/connection-invites/$inviteId/accept") {
            bearerAuth(token); jsonBody(AcceptConnectionInviteRequest(clientId))
        }.body()

    suspend fun listMyConnections(token: String): List<ConnectionDto> =
        http.get("$BASE_URL/v1/me/connections") { bearerAuth(token) }.body()

    /** OWNER-only: the contractors connected *to* the active account (the employer side, unlike [listMyConnections]). */
    suspend fun listEmployerConnections(token: String): List<ConnectionDto> =
        http.get("$BASE_URL/v1/connections") { bearerAuth(token) }.body()

    suspend fun revokeConnection(token: String, connectionId: String) {
        deleteIdempotent("$BASE_URL/v1/connections/$connectionId", token)
    }

    /** OWNER-only: every member of the active account, for the "assign to" picker. */
    suspend fun listAccountMembers(token: String): List<MemberDto> =
        http.get("$BASE_URL/v1/me/account-members") { bearerAuth(token) }.body()

    /** Null until the user has saved a profile on any device. */
    suspend fun getProfile(token: String): ProfileDto? = try {
        http.get("$BASE_URL/v1/me/profile") { bearerAuth(token) }.body()
    } catch (e: ClientRequestException) {
        // The documented "nothing saved yet" answer, not a failure - and with
        // expectSuccess on it arrives as an exception rather than as a status to read.
        if (e.response.status == HttpStatusCode.NotFound) null else throw e
    }

    /** Returns whichever version won last-write-wins server side (see IdentityRepository.saveProfile). */
    suspend fun putProfile(token: String, req: ProfileDto): ProfileDto =
        http.put("$BASE_URL/v1/me/profile") { bearerAuth(token); jsonBody(req) }.body()

    /** Employer schedules a job straight onto the connected worker's own calendar. */
    suspend fun createConnectionPlannedJob(token: String, connectionId: String, req: ConnectionPlannedJobRequest) {
        http.post("$BASE_URL/v1/connections/$connectionId/planned-jobs") { bearerAuth(token); jsonBody(req) }
    }

    /** Employer reads the hours the connected worker has logged against this connection's client. */
    suspend fun listConnectionSessions(token: String, connectionId: String): List<ConnectionSessionDto> =
        http.get("$BASE_URL/v1/connections/$connectionId/sessions") { bearerAuth(token) }.body()

    suspend fun createClient(token: String, req: ClientRequest) {
        http.post("$BASE_URL/v1/clients") { bearerAuth(token); jsonBody(req) }
    }

    suspend fun updateClient(token: String, id: String, req: ClientRequest) {
        http.put("$BASE_URL/v1/clients/$id") { bearerAuth(token); jsonBody(req) }
    }

    suspend fun deleteClient(token: String, id: String) {
        deleteIdempotent("$BASE_URL/v1/clients/$id", token)
    }

    suspend fun listClients(token: String): List<ClientRequest> =
        http.get("$BASE_URL/v1/clients") { bearerAuth(token) }.body()

    suspend fun createSite(token: String, req: SiteRequest) {
        http.post("$BASE_URL/v1/sites") { bearerAuth(token); jsonBody(req) }
    }

    suspend fun updateSite(token: String, id: String, req: SiteRequest) {
        http.put("$BASE_URL/v1/sites/$id") { bearerAuth(token); jsonBody(req) }
    }

    suspend fun deleteSite(token: String, id: String) {
        deleteIdempotent("$BASE_URL/v1/sites/$id", token)
    }

    suspend fun listSites(token: String): List<SiteRequest> =
        http.get("$BASE_URL/v1/sites") { bearerAuth(token) }.body()

    suspend fun createJobType(token: String, req: JobTypeRequest) {
        http.post("$BASE_URL/v1/job-types") { bearerAuth(token); jsonBody(req) }
    }

    suspend fun updateJobType(token: String, id: String, req: JobTypeRequest) {
        http.put("$BASE_URL/v1/job-types/$id") { bearerAuth(token); jsonBody(req) }
    }

    suspend fun deleteJobType(token: String, id: String) {
        deleteIdempotent("$BASE_URL/v1/job-types/$id", token)
    }

    suspend fun listJobTypes(token: String): List<JobTypeRequest> =
        http.get("$BASE_URL/v1/job-types") { bearerAuth(token) }.body()

    suspend fun createInvoice(token: String, req: InvoiceRequest) {
        http.post("$BASE_URL/v1/invoices") { bearerAuth(token); jsonBody(req) }
    }

    suspend fun updateInvoice(token: String, id: String, req: InvoiceRequest) {
        http.put("$BASE_URL/v1/invoices/$id") { bearerAuth(token); jsonBody(req) }
    }

    suspend fun listInvoices(token: String): List<InvoiceRequest> =
        http.get("$BASE_URL/v1/invoices") { bearerAuth(token) }.body()

    suspend fun createTrackingSession(token: String, req: TrackingSessionRequest) {
        http.post("$BASE_URL/v1/sessions") { bearerAuth(token); jsonBody(req) }
    }

    suspend fun updateTrackingSession(token: String, id: String, req: TrackingSessionRequest) {
        http.put("$BASE_URL/v1/sessions/$id") { bearerAuth(token); jsonBody(req) }
    }

    suspend fun deleteTrackingSession(token: String, id: String) {
        deleteIdempotent("$BASE_URL/v1/sessions/$id", token)
    }

    suspend fun listTrackingSessions(token: String): List<TrackingSessionRequest> =
        http.get("$BASE_URL/v1/sessions") { bearerAuth(token) }.body()

    suspend fun createPlannedJob(token: String, req: PlannedJobRequest) {
        http.post("$BASE_URL/v1/planned-jobs") { bearerAuth(token); jsonBody(req) }
    }

    suspend fun updatePlannedJob(token: String, id: String, req: PlannedJobRequest) {
        http.put("$BASE_URL/v1/planned-jobs/$id") { bearerAuth(token); jsonBody(req) }
    }

    suspend fun deletePlannedJob(token: String, id: String) {
        deleteIdempotent("$BASE_URL/v1/planned-jobs/$id", token)
    }

    suspend fun listPlannedJobs(token: String): List<PlannedJobRequest> =
        http.get("$BASE_URL/v1/planned-jobs") { bearerAuth(token) }.body()

    /**
     * A delete of a row the server no longer has is a delete that got what it wanted, so
     * 404 counts as success; everything else throws, which is what lets [SyncEngine] keep
     * the mapping and try the same delete again on the next sync.
     */
    private suspend fun deleteIdempotent(url: String, token: String) {
        try {
            http.delete(url) { bearerAuth(token) }
        } catch (e: ClientRequestException) {
            if (e.response.status != HttpStatusCode.NotFound) throw e
        }
    }
}

private fun io.ktor.client.request.HttpRequestBuilder.bearerAuth(token: String) {
    header("Authorization", "Bearer $token")
    header(HEADER_REQUEST_ID, UUID.randomUUID().toString())
    BackendApi.activeAccountId?.let { header("X-Account-Id", it) }
}

private inline fun <reified T> io.ktor.client.request.HttpRequestBuilder.jsonBody(body: T) {
    contentType(ContentType.Application.Json)
    setBody(body)
}
