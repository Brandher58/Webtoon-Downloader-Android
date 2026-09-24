package com.brandher.webtoondl.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.brandher.webtoondl.data.db.ReadingPositionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingPositionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(position: ReadingPositionEntity)

    @Query("SELECT * FROM reading_positions WHERE chapter_id = :chapterId")
    fun observeByChapter(chapterId: String): Flow<ReadingPositionEntity?>

    @Query("SELECT * FROM reading_positions WHERE chapter_id = :chapterId")
    suspend fun getByChapter(chapterId: String): ReadingPositionEntity?
}