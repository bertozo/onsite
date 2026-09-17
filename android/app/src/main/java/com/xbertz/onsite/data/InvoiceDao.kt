package com.xbertz.onsite.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InvoiceDao {
    @Insert
    suspend fun insert(invoice: Invoice): Long

    @Update
    suspend fun update(invoice: Invoice)

    @Query("SELECT * FROM invoices ORDER BY issueDateEpochDay DESC, id DESC")
    fun getAll(): Flow<List<Invoice>>

    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun getById(id: Long): Invoice?

    /** Invoices keep the client by name, so a company rename must be propagated like for sessions. */
    @Query("UPDATE invoices SET companyName = :newName WHERE companyName = :oldName")
    suspend fun renameCompany(oldName: String, newName: String)
}
