package com.lifeapi.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MoneyDao {
    @Query("SELECT * FROM category ORDER BY income, sort, name")
    fun categories(): Flow<List<Category>>

    @Upsert
    suspend fun upsertCategory(c: Category)

    @Query("DELETE FROM category WHERE name = :name")
    suspend fun deleteCategory(name: String)

    @Query("SELECT * FROM recurring ORDER BY kind, nextDay")
    fun recurring(): Flow<List<Recurring>>

    @Query("SELECT * FROM recurring WHERE autoLog = 1 AND nextDay <= :day")
    suspend fun dueRecurring(day: Long): List<Recurring>

    @Upsert
    suspend fun upsertRecurring(r: Recurring): Long

    @Delete
    suspend fun deleteRecurring(r: Recurring)

    @Query("SELECT * FROM savings_goal ORDER BY sort, id")
    fun savingsGoals(): Flow<List<SavingsGoal>>

    @Query("SELECT * FROM savings_goal")
    suspend fun savingsGoalsNow(): List<SavingsGoal>

    @Upsert
    suspend fun upsertSavingsGoal(g: SavingsGoal): Long

    @Delete
    suspend fun deleteSavingsGoal(g: SavingsGoal)

    @Query("SELECT * FROM savings_move ORDER BY day DESC, id DESC")
    fun savingsMoves(): Flow<List<SavingsMove>>

    @Insert
    suspend fun insertSavingsMove(m: SavingsMove): Long

    @Query("DELETE FROM savings_move WHERE id = :id")
    suspend fun deleteSavingsMove(id: Long)

    @Query("DELETE FROM savings_move WHERE goalId = :goalId")
    suspend fun deleteMovesFor(goalId: Long)

    @Query("SELECT * FROM crypto ORDER BY id")
    fun crypto(): Flow<List<CryptoHolding>>

    @Upsert
    suspend fun upsertCrypto(c: CryptoHolding): Long

    @Delete
    suspend fun deleteCrypto(c: CryptoHolding)

    @Query("SELECT * FROM txn WHERE day BETWEEN :from AND :to")
    suspend fun txnsRangeNow(from: Long, to: Long): List<Txn>

    @Query("SELECT * FROM recurring WHERE id = :id")
    suspend fun recurringNow(id: Long): Recurring?

    @Query("SELECT COUNT(*) FROM category")
    suspend fun countCategories(): Int

    @Query("SELECT * FROM txn WHERE day BETWEEN :from AND :to ORDER BY ts DESC")
    suspend fun txnsBetween(from: Long, to: Long): List<Txn>

    @Query("SELECT * FROM txn WHERE recurringId = :rid ORDER BY day DESC LIMIT 1")
    suspend fun lastForRecurring(rid: Long): Txn?

    // ---------------- categorisation (v6)
    @Query("SELECT * FROM cat_rule")
    suspend fun catRules(): List<CatRule>

    @Upsert
    suspend fun upsertCatRule(r: CatRule)

    @Query("DELETE FROM cat_rule WHERE merchant = :merchant")
    suspend fun deleteCatRule(merchant: String)

    @Query("SELECT * FROM txn WHERE userCategory = 0 AND toAccountId IS NULL AND category != 'Transfer'")
    suspend fun autoCategorised(): List<Txn>

    @Query("SELECT * FROM txn WHERE merchant = :merchant")
    suspend fun txnsByMerchant(merchant: String): List<Txn>

    @Query("SELECT * FROM txn")
    suspend fun allTxnsNow(): List<Txn>

    /** Oldest transaction day — how far back the month switcher may go. */
    @Query("SELECT MIN(day) FROM txn")
    fun oldestTxnDay(): Flow<Long?>

    @Query("SELECT COUNT(*) FROM txn WHERE merchant = :merchant AND merchant != ''")
    fun countByMerchant(merchant: String): Flow<Int>

    @Update
    suspend fun updateTxn(t: Txn)

    @Update
    suspend fun updateTxns(t: List<Txn>)

    @Query("SELECT name FROM category")
    suspend fun categoryNames(): List<String>
}
