package com.xbertz.onsite.backend

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * `adb reverse tcp:8080 tcp:8080` makes a device's own loopback address reach the backend
 * running on the development machine - see onsite-backend/README.md. Swap this for the real
 * deployment's HTTPS URL once one exists.
 */
private const val BASE_URL = "http://127.0.0.1:8080"

@Serializable
data class DevLoginRequest(val email: String)

@Serializable
data class DevLoginResponse(val token: String)

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
data class CompanyRequest(
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
    val companyName: String,
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
    val companyName: String? = null,
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
    val companyName: String? = null,
    val siteLabel: String? = null,
    val jobTypeLabel: String? = null,
    val notes: String? = null,
)

object BackendApi {
    /**
     * Which account the caller is acting as, sent as X-Account-Id on every call once set.
     * Null means "my own account" - the backend defaults to that when the header is absent.
     */
    var activeAccountId: String? = null

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun devLogin(email: String): String {
        val response: DevLoginResponse = client.post("$BASE_URL/v1/dev/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(DevLoginRequest(email))
        }.body()
        return response.token
    }

    suspend fun bootstrap(token: String): MeResponse =
        client.post("$BASE_URL/v1/me/bootstrap") { bearerAuth(token) }.body()

    suspend fun me(token: String): MeResponse =
        client.get("$BASE_URL/v1/me") { bearerAuth(token) }.body()

    suspend fun createInvite(token: String, accountId: String, email: String): InviteDto =
        client.post("$BASE_URL/v1/accounts/$accountId/invites") {
            bearerAuth(token); jsonBody(CreateInviteRequest(email))
        }.body()

    suspend fun listMyInvites(token: String): List<InviteDto> =
        client.get("$BASE_URL/v1/me/invites") { bearerAuth(token) }.body()

    suspend fun acceptInvite(token: String, inviteId: String): InviteDto =
        client.post("$BASE_URL/v1/me/invites/$inviteId/accept") { bearerAuth(token) }.body()

    suspend fun createCompany(token: String, req: CompanyRequest) {
        client.post("$BASE_URL/v1/companies") { bearerAuth(token); jsonBody(req) }
    }

    suspend fun updateCompany(token: String, id: String, req: CompanyRequest) {
        client.put("$BASE_URL/v1/companies/$id") { bearerAuth(token); jsonBody(req) }
    }

    suspend fun deleteCompany(token: String, id: String) {
        client.delete("$BASE_URL/v1/companies/$id") { bearerAuth(token) }
    }

    suspend fun listCompanies(token: String): List<CompanyRequest> =
        client.get("$BASE_URL/v1/companies") { bearerAuth(token) }.body()

    suspend fun createSite(token: String, req: SiteRequest) {
        client.post("$BASE_URL/v1/sites") { bearerAuth(token); jsonBody(req) }
    }

    suspend fun updateSite(token: String, id: String, req: SiteRequest) {
        client.put("$BASE_URL/v1/sites/$id") { bearerAuth(token); jsonBody(req) }
    }

    suspend fun deleteSite(token: String, id: String) {
        client.delete("$BASE_URL/v1/sites/$id") { bearerAuth(token) }
    }

    suspend fun listSites(token: String): List<SiteRequest> =
        client.get("$BASE_URL/v1/sites") { bearerAuth(token) }.body()

    suspend fun createJobType(token: String, req: JobTypeRequest) {
        client.post("$BASE_URL/v1/job-types") { bearerAuth(token); jsonBody(req) }
    }

    suspend fun updateJobType(token: String, id: String, req: JobTypeRequest) {
        client.put("$BASE_URL/v1/job-types/$id") { bearerAuth(token); jsonBody(req) }
    }

    suspend fun deleteJobType(token: String, id: String) {
        client.delete("$BASE_URL/v1/job-types/$id") { bearerAuth(token) }
    }

    suspend fun listJobTypes(token: String): List<JobTypeRequest> =
        client.get("$BASE_URL/v1/job-types") { bearerAuth(token) }.body()

    suspend fun createInvoice(token: String, req: InvoiceRequest) {
        client.post("$BASE_URL/v1/invoices") { bearerAuth(token); jsonBody(req) }
    }

    suspend fun updateInvoice(token: String, id: String, req: InvoiceRequest) {
        client.put("$BASE_URL/v1/invoices/$id") { bearerAuth(token); jsonBody(req) }
    }

    suspend fun listInvoices(token: String): List<InvoiceRequest> =
        client.get("$BASE_URL/v1/invoices") { bearerAuth(token) }.body()

    suspend fun createTrackingSession(token: String, req: TrackingSessionRequest) {
        client.post("$BASE_URL/v1/sessions") { bearerAuth(token); jsonBody(req) }
    }

    suspend fun updateTrackingSession(token: String, id: String, req: TrackingSessionRequest) {
        client.put("$BASE_URL/v1/sessions/$id") { bearerAuth(token); jsonBody(req) }
    }

    suspend fun deleteTrackingSession(token: String, id: String) {
        client.delete("$BASE_URL/v1/sessions/$id") { bearerAuth(token) }
    }

    suspend fun listTrackingSessions(token: String): List<TrackingSessionRequest> =
        client.get("$BASE_URL/v1/sessions") { bearerAuth(token) }.body()

    suspend fun createPlannedJob(token: String, req: PlannedJobRequest) {
        client.post("$BASE_URL/v1/planned-jobs") { bearerAuth(token); jsonBody(req) }
    }

    suspend fun updatePlannedJob(token: String, id: String, req: PlannedJobRequest) {
        client.put("$BASE_URL/v1/planned-jobs/$id") { bearerAuth(token); jsonBody(req) }
    }

    suspend fun deletePlannedJob(token: String, id: String) {
        client.delete("$BASE_URL/v1/planned-jobs/$id") { bearerAuth(token) }
    }

    suspend fun listPlannedJobs(token: String): List<PlannedJobRequest> =
        client.get("$BASE_URL/v1/planned-jobs") { bearerAuth(token) }.body()
}

private fun io.ktor.client.request.HttpRequestBuilder.bearerAuth(token: String) {
    header("Authorization", "Bearer $token")
    BackendApi.activeAccountId?.let { header("X-Account-Id", it) }
}

private inline fun <reified T> io.ktor.client.request.HttpRequestBuilder.jsonBody(body: T) {
    contentType(ContentType.Application.Json)
    setBody(body)
}
