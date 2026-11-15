package com.lifeapi.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Grow v3 · Growth: skills (directions), topics, learning journal, achievements. */
@Dao
interface GrowthDao {
    // ---------------- skills
    @Query("SELECT * FROM skill ORDER BY CASE status WHEN 'Active' THEN 0 WHEN 'Paused' THEN 1 ELSE 2 END, sort, id")
    fun skills(): Flow<List<Skill>>

    @Query("SELECT * FROM skill WHERE id = :id")
    fun skill(id: Long): Flow<Skill?>

    @Upsert suspend fun upsertSkill(s: Skill): Long

    /** Topics go with the skill; journal entries/achievements/materials keep their content but lose the link. */
    @Transaction
    suspend fun deleteSkillCascade(id: Long) {
        deleteTopicsOf(id); unlinkLearning(id); unlinkAchievements(id); unlinkMaterials(id); unlinkActivity(id); deleteSkillRow(id)
    }

    @Query("DELETE FROM skill WHERE id = :id") suspend fun deleteSkillRow(id: Long)
    @Query("DELETE FROM skill_topic WHERE skillId = :id") suspend fun deleteTopicsOf(id: Long)
    @Query("UPDATE learning_entry SET skillId = NULL WHERE skillId = :id") suspend fun unlinkLearning(id: Long)
    @Query("UPDATE achievement SET skillId = NULL WHERE skillId = :id") suspend fun unlinkAchievements(id: Long)
    @Query("UPDATE material SET skillId = NULL WHERE skillId = :id") suspend fun unlinkMaterials(id: Long)
    @Query("UPDATE activity SET skillId = NULL WHERE skillId = :id") suspend fun unlinkActivity(id: Long)

    // ---------------- topics
    @Query("SELECT * FROM skill_topic WHERE skillId = :skillId ORDER BY done, sort, id")
    fun topicsOf(skillId: Long): Flow<List<SkillTopic>>

    @Query("SELECT * FROM skill_topic")
    fun allTopics(): Flow<List<SkillTopic>>

    @Upsert suspend fun upsertTopic(t: SkillTopic): Long
    @Delete suspend fun deleteTopic(t: SkillTopic)

    // ---------------- learning journal
    @Query("SELECT * FROM learning_entry ORDER BY day DESC, ts DESC LIMIT :limit")
    fun recentLearning(limit: Int): Flow<List<LearningEntry>>

    @Query("SELECT * FROM learning_entry WHERE day BETWEEN :from AND :to ORDER BY day DESC, ts DESC")
    fun learningRange(from: Long, to: Long): Flow<List<LearningEntry>>

    @Query("SELECT * FROM learning_entry WHERE skillId = :skillId ORDER BY day DESC, ts DESC")
    fun learningOf(skillId: Long): Flow<List<LearningEntry>>

    @Query("SELECT * FROM learning_entry WHERE materialId = :materialId ORDER BY day DESC, ts DESC")
    fun learningForMaterial(materialId: Long): Flow<List<LearningEntry>>

    @Insert suspend fun insertLearning(e: LearningEntry): Long
    @Upsert suspend fun upsertLearning(e: LearningEntry): Long
    @Delete suspend fun deleteLearning(e: LearningEntry)
    @Query("DELETE FROM learning_entry WHERE id = :id") suspend fun deleteLearningById(id: Long)

    // ---------------- achievements
    @Query("SELECT * FROM achievement WHERE day BETWEEN :from AND :to ORDER BY day DESC, id DESC")
    fun achievementsRange(from: Long, to: Long): Flow<List<Achievement>>

    @Query("SELECT * FROM achievement WHERE skillId = :skillId ORDER BY day DESC, id DESC")
    fun achievementsOf(skillId: Long): Flow<List<Achievement>>

    @Upsert suspend fun upsertAchievement(a: Achievement): Long
    @Delete suspend fun deleteAchievement(a: Achievement)
}
