package com.lifeapi.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Grow v3 · Learn library: materials and their notes. Search/filter/sort happen in memory (small personal library). */
@Dao
interface LearnDao {
    @Query("SELECT * FROM material ORDER BY addedTs DESC, id DESC")
    fun materials(): Flow<List<Material>>

    @Query("SELECT * FROM material WHERE id = :id")
    fun material(id: Long): Flow<Material?>

    @Query("SELECT * FROM material WHERE id = :id")
    suspend fun materialNow(id: Long): Material?

    @Query("SELECT * FROM material WHERE skillId = :skillId ORDER BY addedTs DESC")
    fun materialsOfSkill(skillId: Long): Flow<List<Material>>

    @Query("SELECT * FROM material WHERE projectId = :projectId ORDER BY addedTs DESC")
    fun materialsOfProject(projectId: Long): Flow<List<Material>>

    /** Same URL or same file = same material (dedupe on add). */
    @Query("SELECT * FROM material WHERE (:url IS NOT NULL AND url = :url) OR (:fileUri IS NOT NULL AND fileUri = :fileUri) LIMIT 1")
    suspend fun findDuplicate(url: String?, fileUri: String?): Material?

    @Upsert suspend fun upsertMaterial(m: Material): Long

    @Query("UPDATE material SET progressPage = :page, pageCount = :count, lastOpenedTs = :ts WHERE id = :id")
    suspend fun saveProgress(id: Long, page: Int, count: Int, ts: Long)

    @Query("UPDATE material SET lastOpenedTs = :ts, status = CASE WHEN status = 'To Learn' THEN 'In Progress' ELSE status END WHERE id = :id")
    suspend fun markOpened(id: Long, ts: Long)

    @Transaction
    suspend fun deleteMaterialCascade(id: Long) {
        deleteNotesOf(id); unlinkLearning(id); unlinkAchievements(id); unlinkActivity(id); deleteMaterialRow(id)
    }

    @Query("DELETE FROM material WHERE id = :id") suspend fun deleteMaterialRow(id: Long)
    @Query("DELETE FROM material_note WHERE materialId = :id") suspend fun deleteNotesOf(id: Long)
    @Query("UPDATE learning_entry SET materialId = NULL WHERE materialId = :id") suspend fun unlinkLearning(id: Long)
    @Query("UPDATE achievement SET materialId = NULL WHERE materialId = :id") suspend fun unlinkAchievements(id: Long)
    @Query("UPDATE activity SET materialId = NULL WHERE materialId = :id") suspend fun unlinkActivity(id: Long)

    // ---------------- notes
    @Query("SELECT * FROM material_note WHERE materialId = :materialId ORDER BY ts DESC")
    fun notesOf(materialId: Long): Flow<List<MaterialNote>>

    @Upsert suspend fun upsertNote(n: MaterialNote): Long
    @Delete suspend fun deleteNote(n: MaterialNote)
}
