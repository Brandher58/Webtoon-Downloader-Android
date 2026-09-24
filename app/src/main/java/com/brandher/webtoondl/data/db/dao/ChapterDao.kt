package com.brandher.webtoondl.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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

    @Query("DELETE FROM chapters WHERE series_id = :seriesId")
    suspend fun deleteForSeries(seriesId: String)

    @Query("SELECT * FROM chapters WHERE series_id = :seriesId ORDER BY number ASC")
    fun observeForSeries(seriesId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE id = :chapterId")
    suspend fun getById(chapterId: String): ChapterEntity?

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
}