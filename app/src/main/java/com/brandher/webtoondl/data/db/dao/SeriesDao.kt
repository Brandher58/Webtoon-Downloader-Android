package com.brandher.webtoondl.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.brandher.webtoondl.data.db.SeriesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SeriesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(series: SeriesEntity)

    @Query("SELECT * FROM series WHERE id = :id")
    suspend fun getById(id: String): SeriesEntity?

    @Query("SELECT * FROM series WHERE id = :id")
    fun observeById(id: String): Flow<SeriesEntity?>

    @Query("SELECT * FROM series ORDER BY added_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series ORDER BY added_at DESC")
    fun observeAll(): Flow<List<SeriesEntity>>

    @Query("DELETE FROM series WHERE id = :id")
    suspend fun deleteById(id: String)
}