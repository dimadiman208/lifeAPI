package com.lifeapi.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Bank-sync queries (Enable Banking). */
@Dao
interface BankDao {
    @Query("SELECT * FROM account WHERE extId = :extId LIMIT 1")
    suspend fun accountByExt(extId: String): Account?

    @Query("SELECT * FROM account WHERE source = 'Bank' ORDER BY sort, id")
    fun bankAccounts(): Flow<List<Account>>

    @Query("SELECT * FROM account WHERE source = 'Bank' ORDER BY sort, id")
    suspend fun bankAccountsNow(): List<Account>

    @Insert
    suspend fun insertAccount(a: Account): Long

    @Update
    suspend fun updateAccount(a: Account)

    @Query("SELECT * FROM txn WHERE extId = :extId LIMIT 1")
    suspend fun txnByExt(extId: String): Txn?

    @Insert
    suspend fun insertTxn(t: Txn): Long

    @Update
    suspend fun updateTxn(t: Txn)

    /** Latest booked bank-synced day for an account (null = never synced). */
    @Query("SELECT MAX(day) FROM txn WHERE accountId = :accountId AND extId IS NOT NULL AND pending = 0")
    suspend fun lastBookedDay(accountId: Long): Long?

    @Query("DELETE FROM txn WHERE accountId = :accountId AND extId IS NOT NULL AND pending = 1 AND day >= :from")
    suspend fun deletePending(accountId: Long, from: Long)

    @Query("SELECT COUNT(*) FROM txn WHERE extId LIKE 'eb:%'")
    suspend fun bankTxnCount(): Int

    @Query("DELETE FROM txn WHERE extId LIKE 'eb:%'")
    suspend fun deleteAllBankTxns()

    /** Turn linked accounts back into manual ones (keeps their history and balance). */
    @Query("UPDATE account SET source = 'Manual', extId = NULL WHERE source = 'Bank'")
    suspend fun unlinkAll()
}
