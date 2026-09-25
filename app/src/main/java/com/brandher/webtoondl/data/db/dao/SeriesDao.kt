package com.brandher.webtoondl.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.brandher.webtoondl.data.db.SeriesEntity
import kotlinx.coroutines.flow.Flow

/** Serie junto con un resumen agregado de descarga para la biblioteca. */
data class SeriesWithStats(
    @Embedded val series: SeriesEntity,
    val totalChapters: Int,
    val downloadedChapters: Int,
)

@Dao
interface SeriesDao {

    /** Inserta la serie solo si no existe; nunca borra la existente (evita cascade sobre capítulos). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(series: SeriesEntity)

    /** Actualiza solo metadatos de la serie, sin tocar la fila ni sus hijos. */
    @Query(
        """UPDATE series SET url = :url, title = :title, cover_url = :coverUrl,
           author = :author, genre = :genre, summary = :summary WHERE id = :id""",
    )
    suspend fun updateSeriesMetadata(
        id: String,
        url: String,
        title: String,
        coverUrl: String?,
        author: String?,
        genre: String?,
        summary: String?,
    )

    @Query("SELECT * FROM series WHERE id = :id")
    suspend fun getById(id: String): SeriesEntity?

    @Query("SELECT * FROM series WHERE id = :id")
    fun observeById(id: String): Flow<SeriesEntity?>

    @Query("SELECT * FROM series ORDER BY added_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series ORDER BY added_at DESC")
    fun observeAll(): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series")
    suspend fun getAll(): List<SeriesEntity>

    @Query(
        """
        SELECT s.*,
               COUNT(c.id) AS totalChapters,
               COALESCE(SUM(CASE WHEN c.queue_status = 'COMPLETED' THEN 1 ELSE 0 END), 0) AS downloadedChapters
        FROM series s
        LEFT JOIN chapters c ON c.series_id = s.id
        GROUP BY s.id
        ORDER BY s.added_at DESC
        """,
    )
    fun observeAllWithStats(): Flow<List<SeriesWithStats>>

    @Query("DELETE FROM series WHERE id = :id")
    suspend fun deleteById(id: String)
}