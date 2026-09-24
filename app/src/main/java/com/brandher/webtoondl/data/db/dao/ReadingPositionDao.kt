package com.brandher.webtoondl.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.brandher.webtoondl.data.db.ReadingPositionEntity
import kotlinx.coroutines.flow.Flow

/** Última posición de lectura de una serie (por capítulo descargado o no). */
data class LastReadRow(
    @Embedded val position: ReadingPositionEntity,
    val seriesId: String,
    val chapterId: String,
    val downloaded: Boolean,
)

@Dao
interface ReadingPositionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(position: ReadingPositionEntity)

    @Query("SELECT * FROM reading_positions WHERE chapter_id = :chapterId")
    fun observeByChapter(chapterId: String): Flow<ReadingPositionEntity?>

    @Query("SELECT * FROM reading_positions WHERE chapter_id = :chapterId")
    suspend fun getByChapter(chapterId: String): ReadingPositionEntity?

    /** Última posición leída de cada serie (la más reciente). */
    @Query(
        """
        SELECT rp.*, c.series_id AS seriesId, c.id AS chapterId,
               (c.queue_status = 'COMPLETED') AS downloaded
        FROM reading_positions rp
        JOIN chapters c ON c.id = rp.chapter_id
        WHERE rp.updated_at = (
            SELECT MAX(rp2.updated_at)
            FROM reading_positions rp2
            JOIN chapters c2 ON c2.id = rp2.chapter_id
            WHERE c2.series_id = c.series_id
        )
        """,
    )
    fun observeLastReadAll(): Flow<List<LastReadRow>>
}