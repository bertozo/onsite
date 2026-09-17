package com.xbertz.onsite.backend

import com.xbertz.onsite.data.AppDatabase
import com.xbertz.onsite.data.Company
import com.xbertz.onsite.data.Invoice
import com.xbertz.onsite.data.JobType
import com.xbertz.onsite.data.PlannedJob
import com.xbertz.onsite.data.Site
import com.xbertz.onsite.data.SyncDao
import com.xbertz.onsite.data.SyncMapping
import com.xbertz.onsite.data.TrackingSession
import kotlinx.coroutines.flow.first
import java.util.UUID

private const val TYPE_COMPANY = "company"
private const val TYPE_SITE = "site"
private const val TYPE_JOB_TYPE = "job_type"
private const val TYPE_INVOICE = "invoice"
private const val TYPE_SESSION = "tracking_session"
private const val TYPE_PLANNED_JOB = "planned_job"

/**
 * Two-way sync against the backend, opportunistic and full-reconciliation (not delta/cursor
 * based - at one tradesperson's data volumes, listing everything every time is simpler and
 * cheap, and avoids a whole class of cursor bugs). Order matters: invoices before sessions,
 * both ways, since a session's invoiceId is remapped through the invoice's own mapping.
 *
 * A local row with no [SyncMapping] yet is "never synced" -> create remotely. A mapped row
 * missing from the *next* local read is "deleted locally since last sync" -> delete remotely
 * and drop the mapping. Symmetrically on pull: a mapped remoteId missing from the server's
 * list was deleted elsewhere -> delete locally. This one function is effectively also the
 * Phase 2 migration: a brand-new account has no mappings, so its first sync uploads
 * everything that was already in Room.
 */
suspend fun runSync(db: AppDatabase, token: String) {
    val sync = db.syncDao()

    pushCompanies(db, sync, token)
    pushSites(db, sync, token)
    pushJobTypes(db, sync, token)
    pushInvoices(db, sync, token)
    pushSessions(db, sync, token)
    pushPlannedJobs(db, sync, token)

    pullCompanies(db, sync, token)
    pullSites(db, sync, token)
    pullJobTypes(db, sync, token)
    pullInvoices(db, sync, token)
    pullSessions(db, sync, token)
    pullPlannedJobs(db, sync, token)
}

/**
 * Switching which account the app acts as (see `AuthViewModel.switchAccount`) means Room can
 * no longer hold both accounts' rows at once - there is no account_id column on the local
 * tables, this app has only ever been single-tenant on-device. So a switch pushes whatever is
 * pending against the account being left, wipes the local catalog, and re-syncs fresh against
 * the new one. Anything not yet synced at the moment of switching is pushed first and so isn't
 * lost - only local identity (sync_mapping) resets, not data on the server.
 */
suspend fun wipeLocalDomainData(db: AppDatabase) {
    db.companyDao().deleteAll()
    db.siteDao().deleteAll()
    db.jobTypeDao().deleteAll()
    db.invoiceDao().deleteAll()
    db.trackingSessionDao().deleteAll()
    db.plannedJobDao().deleteAll()
    db.syncDao().clearAll()
}

private suspend fun pushCompanies(db: AppDatabase, sync: SyncDao, token: String) {
    val dao = db.companyDao()
    val localRows = dao.getAll().first()
    val localIds = localRows.map { it.id }.toSet()
    val mappings = sync.mappingsFor(TYPE_COMPANY)

    for (mapping in mappings) {
        if (mapping.localId !in localIds) {
            runCatching { BackendApi.deleteCompany(token, mapping.remoteId) }
            sync.deleteMapping(TYPE_COMPANY, mapping.localId)
        }
    }

    val mappingByLocalId = mappings.associateBy { it.localId }
    for (row in localRows) {
        val mapping = mappingByLocalId[row.id]
        val remoteId = mapping?.remoteId ?: UUID.randomUUID().toString()
        val req = CompanyRequest(
            id = remoteId,
            name = row.name,
            abn = row.abn,
            phone = row.phone,
            email = row.email,
            createdAtMillis = row.createdAtMillis,
            hourlyRate = row.hourlyRate,
        )
        if (mapping == null) BackendApi.createCompany(token, req) else BackendApi.updateCompany(token, remoteId, req)
        sync.upsertMapping(SyncMapping(TYPE_COMPANY, row.id, remoteId, System.currentTimeMillis()))
    }
}

private suspend fun pullCompanies(db: AppDatabase, sync: SyncDao, token: String) {
    val dao = db.companyDao()
    val remoteRows = BackendApi.listCompanies(token)
    val remoteIds = remoteRows.map { it.id }.toSet()
    val mappings = sync.mappingsFor(TYPE_COMPANY)
    val mappingByRemoteId = mappings.associateBy { it.remoteId }

    for (remote in remoteRows) {
        val mapping = mappingByRemoteId[remote.id]
        val company = Company(
            id = mapping?.localId ?: 0,
            name = remote.name,
            abn = remote.abn,
            phone = remote.phone,
            email = remote.email,
            createdAtMillis = remote.createdAtMillis,
            hourlyRate = remote.hourlyRate,
        )
        val localId = if (mapping == null) dao.insert(company) else { dao.update(company); mapping.localId }
        sync.upsertMapping(SyncMapping(TYPE_COMPANY, localId, remote.id, System.currentTimeMillis()))
    }

    for (mapping in mappings) {
        if (mapping.remoteId !in remoteIds) {
            dao.deleteById(mapping.localId)
            sync.deleteMapping(TYPE_COMPANY, mapping.localId)
        }
    }
}

private suspend fun pushSites(db: AppDatabase, sync: SyncDao, token: String) {
    val dao = db.siteDao()
    val localRows = dao.getAll().first()
    val localIds = localRows.map { it.id }.toSet()
    val mappings = sync.mappingsFor(TYPE_SITE)

    for (mapping in mappings) {
        if (mapping.localId !in localIds) {
            runCatching { BackendApi.deleteSite(token, mapping.remoteId) }
            sync.deleteMapping(TYPE_SITE, mapping.localId)
        }
    }

    val mappingByLocalId = mappings.associateBy { it.localId }
    for (row in localRows) {
        val mapping = mappingByLocalId[row.id]
        val remoteId = mapping?.remoteId ?: UUID.randomUUID().toString()
        val req = SiteRequest(
            id = remoteId,
            label = row.label,
            address = row.address,
            latitude = row.latitude,
            longitude = row.longitude,
            createdAtMillis = row.createdAtMillis,
        )
        if (mapping == null) BackendApi.createSite(token, req) else BackendApi.updateSite(token, remoteId, req)
        sync.upsertMapping(SyncMapping(TYPE_SITE, row.id, remoteId, System.currentTimeMillis()))
    }
}

private suspend fun pullSites(db: AppDatabase, sync: SyncDao, token: String) {
    val dao = db.siteDao()
    val remoteRows = BackendApi.listSites(token)
    val remoteIds = remoteRows.map { it.id }.toSet()
    val mappings = sync.mappingsFor(TYPE_SITE)
    val mappingByRemoteId = mappings.associateBy { it.remoteId }

    for (remote in remoteRows) {
        val mapping = mappingByRemoteId[remote.id]
        val site = Site(
            id = mapping?.localId ?: 0,
            label = remote.label,
            address = remote.address,
            latitude = remote.latitude,
            longitude = remote.longitude,
            createdAtMillis = remote.createdAtMillis,
        )
        val localId = if (mapping == null) dao.insert(site) else { dao.update(site); mapping.localId }
        sync.upsertMapping(SyncMapping(TYPE_SITE, localId, remote.id, System.currentTimeMillis()))
    }

    for (mapping in mappings) {
        if (mapping.remoteId !in remoteIds) {
            dao.deleteById(mapping.localId)
            sync.deleteMapping(TYPE_SITE, mapping.localId)
        }
    }
}

private suspend fun pushJobTypes(db: AppDatabase, sync: SyncDao, token: String) {
    val dao = db.jobTypeDao()
    val localRows = dao.getAll().first()
    val localIds = localRows.map { it.id }.toSet()
    val mappings = sync.mappingsFor(TYPE_JOB_TYPE)

    for (mapping in mappings) {
        if (mapping.localId !in localIds) {
            runCatching { BackendApi.deleteJobType(token, mapping.remoteId) }
            sync.deleteMapping(TYPE_JOB_TYPE, mapping.localId)
        }
    }

    val mappingByLocalId = mappings.associateBy { it.localId }
    for (row in localRows) {
        val mapping = mappingByLocalId[row.id]
        val remoteId = mapping?.remoteId ?: UUID.randomUUID().toString()
        val req = JobTypeRequest(id = remoteId, name = row.name, createdAtMillis = row.createdAtMillis)
        if (mapping == null) BackendApi.createJobType(token, req) else BackendApi.updateJobType(token, remoteId, req)
        sync.upsertMapping(SyncMapping(TYPE_JOB_TYPE, row.id, remoteId, System.currentTimeMillis()))
    }
}

private suspend fun pullJobTypes(db: AppDatabase, sync: SyncDao, token: String) {
    val dao = db.jobTypeDao()
    val remoteRows = BackendApi.listJobTypes(token)
    val remoteIds = remoteRows.map { it.id }.toSet()
    val mappings = sync.mappingsFor(TYPE_JOB_TYPE)
    val mappingByRemoteId = mappings.associateBy { it.remoteId }

    for (remote in remoteRows) {
        val mapping = mappingByRemoteId[remote.id]
        val jobType = JobType(id = mapping?.localId ?: 0, name = remote.name, createdAtMillis = remote.createdAtMillis)
        val localId = if (mapping == null) dao.insert(jobType) else { dao.update(jobType); mapping.localId }
        sync.upsertMapping(SyncMapping(TYPE_JOB_TYPE, localId, remote.id, System.currentTimeMillis()))
    }

    for (mapping in mappings) {
        if (mapping.remoteId !in remoteIds) {
            dao.deleteById(mapping.localId)
            sync.deleteMapping(TYPE_JOB_TYPE, mapping.localId)
        }
    }
}

private suspend fun pushInvoices(db: AppDatabase, sync: SyncDao, token: String) {
    val dao = db.invoiceDao()
    val localRows = dao.getAll().first()
    val localIds = localRows.map { it.id }.toSet()
    val mappings = sync.mappingsFor(TYPE_INVOICE)

    // Invoices are never hard-deleted locally (only voided), so this loop is normally a no-op;
    // kept for symmetry in case that ever changes.
    for (mapping in mappings) {
        if (mapping.localId !in localIds) {
            sync.deleteMapping(TYPE_INVOICE, mapping.localId)
        }
    }

    val mappingByLocalId = mappings.associateBy { it.localId }
    for (row in localRows) {
        val mapping = mappingByLocalId[row.id]
        val remoteId = mapping?.remoteId ?: UUID.randomUUID().toString()
        val req = InvoiceRequest(
            id = remoteId,
            number = row.number,
            companyName = row.companyName,
            periodStartEpochDay = row.periodStartEpochDay,
            periodEndEpochDay = row.periodEndEpochDay,
            issueDateEpochDay = row.issueDateEpochDay,
            totalHours = row.totalHours,
            totalAmount = row.totalAmount,
            hourlyRate = row.hourlyRate,
            status = row.status,
            sentAtMillis = row.sentAtMillis,
            paidAtMillis = row.paidAtMillis,
            pdfPath = row.pdfPath,
            notes = row.notes,
            createdAtMillis = row.createdAtMillis,
        )
        if (mapping == null) BackendApi.createInvoice(token, req) else BackendApi.updateInvoice(token, remoteId, req)
        sync.upsertMapping(SyncMapping(TYPE_INVOICE, row.id, remoteId, System.currentTimeMillis()))
    }
}

private suspend fun pullInvoices(db: AppDatabase, sync: SyncDao, token: String) {
    val dao = db.invoiceDao()
    val remoteRows = BackendApi.listInvoices(token)
    val mappings = sync.mappingsFor(TYPE_INVOICE)
    val mappingByRemoteId = mappings.associateBy { it.remoteId }

    for (remote in remoteRows) {
        val mapping = mappingByRemoteId[remote.id]
        val invoice = Invoice(
            id = mapping?.localId ?: 0,
            number = remote.number,
            companyName = remote.companyName,
            periodStartEpochDay = remote.periodStartEpochDay,
            periodEndEpochDay = remote.periodEndEpochDay,
            issueDateEpochDay = remote.issueDateEpochDay,
            totalHours = remote.totalHours,
            totalAmount = remote.totalAmount,
            hourlyRate = remote.hourlyRate,
            status = remote.status,
            sentAtMillis = remote.sentAtMillis,
            paidAtMillis = remote.paidAtMillis,
            pdfPath = remote.pdfPath ?: "",
            notes = remote.notes,
            createdAtMillis = remote.createdAtMillis,
        )
        val localId = if (mapping == null) dao.insert(invoice) else { dao.update(invoice); mapping.localId }
        sync.upsertMapping(SyncMapping(TYPE_INVOICE, localId, remote.id, System.currentTimeMillis()))
    }
    // Invoices have no deleteById (never removed locally): a remote row disappearing here
    // would be unexpected, so it's deliberately left alone rather than guessing how to react.
}

private suspend fun pushSessions(db: AppDatabase, sync: SyncDao, token: String) {
    val dao = db.trackingSessionDao()
    val localRows = dao.getAll().first()
    val localIds = localRows.map { it.id }.toSet()
    val mappings = sync.mappingsFor(TYPE_SESSION)

    for (mapping in mappings) {
        if (mapping.localId !in localIds) {
            runCatching { BackendApi.deleteTrackingSession(token, mapping.remoteId) }
            sync.deleteMapping(TYPE_SESSION, mapping.localId)
        }
    }

    val mappingByLocalId = mappings.associateBy { it.localId }
    val invoiceMappingByLocalId = sync.mappingsFor(TYPE_INVOICE).associateBy { it.localId }
    for (row in localRows) {
        val mapping = mappingByLocalId[row.id]
        val remoteId = mapping?.remoteId ?: UUID.randomUUID().toString()
        val req = TrackingSessionRequest(
            id = remoteId,
            companyName = row.companyName,
            siteLabel = row.siteLabel,
            jobTypeLabel = row.jobTypeLabel,
            startTimestampMillis = row.startTimestampMillis,
            startLatitude = row.startLatitude,
            startLongitude = row.startLongitude,
            stopTimestampMillis = row.stopTimestampMillis,
            stopLatitude = row.stopLatitude,
            stopLongitude = row.stopLongitude,
            hourlyRate = row.hourlyRate,
            invoiceId = row.invoiceId?.let { invoiceMappingByLocalId[it]?.remoteId },
        )
        if (mapping == null) BackendApi.createTrackingSession(token, req) else BackendApi.updateTrackingSession(token, remoteId, req)
        sync.upsertMapping(SyncMapping(TYPE_SESSION, row.id, remoteId, System.currentTimeMillis()))
    }
}

private suspend fun pullSessions(db: AppDatabase, sync: SyncDao, token: String) {
    val dao = db.trackingSessionDao()
    val remoteRows = BackendApi.listTrackingSessions(token)
    val remoteIds = remoteRows.map { it.id }.toSet()
    val mappings = sync.mappingsFor(TYPE_SESSION)
    val mappingByRemoteId = mappings.associateBy { it.remoteId }
    val invoiceMappingByRemoteId = sync.mappingsFor(TYPE_INVOICE).associateBy { it.remoteId }

    for (remote in remoteRows) {
        val mapping = mappingByRemoteId[remote.id]
        val session = TrackingSession(
            id = mapping?.localId ?: 0,
            companyName = remote.companyName,
            siteLabel = remote.siteLabel,
            jobTypeLabel = remote.jobTypeLabel,
            startTimestampMillis = remote.startTimestampMillis,
            startLatitude = remote.startLatitude,
            startLongitude = remote.startLongitude,
            stopTimestampMillis = remote.stopTimestampMillis,
            stopLatitude = remote.stopLatitude,
            stopLongitude = remote.stopLongitude,
            hourlyRate = remote.hourlyRate,
            invoiceId = remote.invoiceId?.let { invoiceMappingByRemoteId[it]?.localId },
        )
        val localId = if (mapping == null) dao.insert(session) else { dao.update(session); mapping.localId }
        sync.upsertMapping(SyncMapping(TYPE_SESSION, localId, remote.id, System.currentTimeMillis()))
    }

    for (mapping in mappings) {
        if (mapping.remoteId !in remoteIds) {
            dao.deleteById(mapping.localId)
            sync.deleteMapping(TYPE_SESSION, mapping.localId)
        }
    }
}

private suspend fun pushPlannedJobs(db: AppDatabase, sync: SyncDao, token: String) {
    val dao = db.plannedJobDao()
    val localRows = dao.getAll().first()
    val localIds = localRows.map { it.id }.toSet()
    val mappings = sync.mappingsFor(TYPE_PLANNED_JOB)

    for (mapping in mappings) {
        if (mapping.localId !in localIds) {
            runCatching { BackendApi.deletePlannedJob(token, mapping.remoteId) }
            sync.deleteMapping(TYPE_PLANNED_JOB, mapping.localId)
        }
    }

    val mappingByLocalId = mappings.associateBy { it.localId }
    for (row in localRows) {
        val mapping = mappingByLocalId[row.id]
        val remoteId = mapping?.remoteId ?: UUID.randomUUID().toString()
        val req = PlannedJobRequest(
            id = remoteId,
            dateEpochDay = row.dateEpochDay,
            startMinute = row.startMinute,
            endMinute = row.endMinute,
            companyName = row.companyName,
            siteLabel = row.siteLabel,
            jobTypeLabel = row.jobTypeLabel,
            notes = row.notes,
        )
        if (mapping == null) BackendApi.createPlannedJob(token, req) else BackendApi.updatePlannedJob(token, remoteId, req)
        sync.upsertMapping(SyncMapping(TYPE_PLANNED_JOB, row.id, remoteId, System.currentTimeMillis()))
    }
}

private suspend fun pullPlannedJobs(db: AppDatabase, sync: SyncDao, token: String) {
    val dao = db.plannedJobDao()
    val remoteRows = BackendApi.listPlannedJobs(token)
    val remoteIds = remoteRows.map { it.id }.toSet()
    val mappings = sync.mappingsFor(TYPE_PLANNED_JOB)
    val mappingByRemoteId = mappings.associateBy { it.remoteId }

    for (remote in remoteRows) {
        val mapping = mappingByRemoteId[remote.id]
        val job = PlannedJob(
            id = mapping?.localId ?: 0,
            dateEpochDay = remote.dateEpochDay,
            startMinute = remote.startMinute,
            endMinute = remote.endMinute,
            companyName = remote.companyName,
            siteLabel = remote.siteLabel,
            jobTypeLabel = remote.jobTypeLabel,
            notes = remote.notes,
        )
        val localId = if (mapping == null) dao.insert(job) else { dao.update(job); mapping.localId }
        sync.upsertMapping(SyncMapping(TYPE_PLANNED_JOB, localId, remote.id, System.currentTimeMillis()))
    }

    for (mapping in mappings) {
        if (mapping.remoteId !in remoteIds) {
            dao.deleteById(mapping.localId)
            sync.deleteMapping(TYPE_PLANNED_JOB, mapping.localId)
        }
    }
}
