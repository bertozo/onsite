package com.xbertz.onsite.backend

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
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

    suspend fun createCompany(token: String, req: CompanyRequest) {
        client.post("$BASE_URL/v1/companies") { bearerAuth(token); jsonBody(req) }
    }

    suspend fun createSite(token: String, req: SiteRequest) {
        client.post("$BASE_URL/v1/sites") { bearerAuth(token); jsonBody(req) }
    }

    suspend fun createJobType(token: String, req: JobTypeRequest) {
        client.post("$BASE_URL/v1/job-types") { bearerAuth(token); jsonBody(req) }
    }

    suspend fun createInvoice(token: String, req: InvoiceRequest) {
        client.post("$BASE_URL/v1/invoices") { bearerAuth(token); jsonBody(req) }
    }

    suspend fun createTrackingSession(token: String, req: TrackingSessionRequest) {
        client.post("$BASE_URL/v1/sessions") { bearerAuth(token); jsonBody(req) }
    }

    suspend fun createPlannedJob(token: String, req: PlannedJobRequest) {
        client.post("$BASE_URL/v1/planned-jobs") { bearerAuth(token); jsonBody(req) }
    }
}

private fun io.ktor.client.request.HttpRequestBuilder.bearerAuth(token: String) {
    header("Authorization", "Bearer $token")
}

private inline fun <reified T> io.ktor.client.request.HttpRequestBuilder.jsonBody(body: T) {
    contentType(ContentType.Application.Json)
    setBody(body)
}
