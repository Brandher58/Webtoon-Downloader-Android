package com.brandher.webtoondl.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.brandher.webtoondl.data.db.ChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(chapters: List<ChapterEntity>)

    @Update
    suspend fun update(chapter: ChapterEntity)

    @Query("UPDATE chapters SET queue_status = :status WHERE id = :chapterId")
    suspend fun updateStatus(chapterId: String, status: String)

    @Query("UPDATE chapters SET queue_status = :status, error = :error WHERE id = :chapterId")
    suspend fun updateStatusError(chapterId: String, status: String, error: String?)

    @Query("UPDATE chapters SET queue_status = :status, error = NULL WHERE id = :chapterId")
    suspend fun configureEnqueue(chapterId: String, status: String)

    @Query("UPDATE chapters SET queue_status = :to, error = NULL WHERE queue_status IN (:fromStatuses)")
    suspend fun updateStatuses(fromStatuses: List<String>, to: String)

    @Query("UPDATE chapters SET queue_status = :status WHERE series_id = :seriesId")
    suspend fun updateStatusForSeries(seriesId: String, status: String)

    @Query("UPDATE chapters SET pages_total = :total, pages_done = :done WHERE id = :chapterId")
    suspend fun updateProgress(chapterId: String, total: Int, done: Int)

    @Query("SELECT * FROM chapters WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE series_id = :seriesId")
    suspend fun getForSeries(seriesId: String): List<ChapterEntity>

    @Query("DELETE FROM chapters WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("UPDATE chapters SET queue_status = :status, pages_total = NULL, pages_done = 0, error = NULL WHERE id = :chapterId")
    suspend fun resetDownload(chapterId: String, status: String)

    @Query("SELECT * FROM chapters WHERE queue_status IN (:statuses)")
    suspend fun getByStatuses(statuses: List<String>): List<ChapterEntity>

    @Query("DELETE FROM chapters WHERE series_id = :seriesId")
    suspend fun deleteForSeries(seriesId: String)

    @Query("SELECT * FROM chapters WHERE series_id = :seriesId ORDER BY number ASC")
    fun observeForSeries(seriesId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE id = :chapterId")
    suspend fun getById(chapterId: String): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE series_id = :seriesId AND queue_status = :status ORDER BY number ASC")
    suspend fun getForSeriesByStatus(seriesId: String, status: String): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :chapterId")
    fun observeById(chapterId: String): Flow<ChapterEntity?>

    @Query(
        """SELECT c.* FROM chapters c
           JOIN series s ON s.id = c.series_id
           WHERE c.queue_status IN (:statuses)
           ORDER BY c.number ASC""",
    )
    fun observeByStatuses(statuses: List<String>): Flow<List<ChapterEntity>>

    @Query(
        """SELECT c.* FROM chapters c
           JOIN series s ON s.id = c.series_id
           WHERE s.id = :seriesId AND queue_status IN (:statuses)
           ORDER BY c.number ASC""",
    )
    fun observeBySeriesAndStatuses(seriesId: String, statuses: List<String>): Flow<List<ChapterEntity>>

    @Query(
        """SELECT c.*, s.title AS seriesTitle
           FROM chapters c
           JOIN series s ON s.id = c.series_id
           WHERE c.queue_status IN (:statuses)
           ORDER BY c.number ASC""",
    )
    fun observeQueue(statuses: List<String>): Flow<List<ChapterQueueRow>>
}