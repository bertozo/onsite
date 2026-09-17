package com.xbertz.onsite.backend

import com.xbertz.onsite.data.AppDatabase
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * One-time upload of everything already in Room to the caller's freshly-bootstrapped
 * personal account. Room stays the on-device source of truth (Phase 3 adds the continuous
 * sync); this only needs to run once per install, guarded by [SessionStore.hasMigratedLocalData].
 *
 * Local invoice ids are remapped to the server's UUIDs so tracking_sessions.invoiceId still
 * points at the right invoice; every other cross-reference in this app is already by name
 * (company/site/job type), so no other remapping is needed.
 */
suspend fun uploadLocalDataToBackend(db: AppDatabase, token: String) {
    val companies = db.companyDao().getAll().first()
    for (company in companies) {
        BackendApi.createCompany(
            token,
            CompanyRequest(
                id = UUID.randomUUID().toString(),
                name = company.name,
                abn = company.abn,
                phone = company.phone,
                email = company.email,
                createdAtMillis = company.createdAtMillis,
                hourlyRate = company.hourlyRate,
            )
        )
    }

    val sites = db.siteDao().getAll().first()
    for (site in sites) {
        BackendApi.createSite(
            token,
            SiteRequest(
                id = UUID.randomUUID().toString(),
                label = site.label,
                address = site.address,
                latitude = site.latitude,
                longitude = site.longitude,
                createdAtMillis = site.createdAtMillis,
            )
        )
    }

    val jobTypes = db.jobTypeDao().getAll().first()
    for (jobType in jobTypes) {
        BackendApi.createJobType(
            token,
            JobTypeRequest(
                id = UUID.randomUUID().toString(),
                name = jobType.name,
                createdAtMillis = jobType.createdAtMillis,
            )
        )
    }

    val invoices = db.invoiceDao().getAll().first()
    val invoiceIdMap = mutableMapOf<Long, String>()
    for (invoice in invoices) {
        val remoteId = UUID.randomUUID().toString()
        invoiceIdMap[invoice.id] = remoteId
        BackendApi.createInvoice(
            token,
            InvoiceRequest(
                id = remoteId,
                number = invoice.number,
                companyName = invoice.companyName,
                periodStartEpochDay = invoice.periodStartEpochDay,
                periodEndEpochDay = invoice.periodEndEpochDay,
                issueDateEpochDay = invoice.issueDateEpochDay,
                totalHours = invoice.totalHours,
                totalAmount = invoice.totalAmount,
                hourlyRate = invoice.hourlyRate,
                status = invoice.status,
                sentAtMillis = invoice.sentAtMillis,
                paidAtMillis = invoice.paidAtMillis,
                pdfPath = invoice.pdfPath,
                notes = invoice.notes,
                createdAtMillis = invoice.createdAtMillis,
            )
        )
    }

    val sessions = db.trackingSessionDao().getAll().first()
    for (session in sessions) {
        BackendApi.createTrackingSession(
            token,
            TrackingSessionRequest(
                id = UUID.randomUUID().toString(),
                companyName = session.companyName,
                siteLabel = session.siteLabel,
                jobTypeLabel = session.jobTypeLabel,
                startTimestampMillis = session.startTimestampMillis,
                startLatitude = session.startLatitude,
                startLongitude = session.startLongitude,
                stopTimestampMillis = session.stopTimestampMillis,
                stopLatitude = session.stopLatitude,
                stopLongitude = session.stopLongitude,
                hourlyRate = session.hourlyRate,
                invoiceId = session.invoiceId?.let { invoiceIdMap[it] },
            )
        )
    }

    val plannedJobs = db.plannedJobDao().getAll().first()
    for (job in plannedJobs) {
        BackendApi.createPlannedJob(
            token,
            PlannedJobRequest(
                id = UUID.randomUUID().toString(),
                dateEpochDay = job.dateEpochDay,
                startMinute = job.startMinute,
                endMinute = job.endMinute,
                companyName = job.companyName,
                siteLabel = job.siteLabel,
                jobTypeLabel = job.jobTypeLabel,
                notes = job.notes,
            )
        )
    }
}
