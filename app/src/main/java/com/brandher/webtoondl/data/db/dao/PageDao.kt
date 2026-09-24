package com.brandher.webtoondl.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.brandher.webtoondl.data.db.PageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(pages: List<PageEntity>)

    @Update
    suspend fun update(page: PageEntity)

    @Query("SELECT * FROM pages WHERE chapter_id = :chapterId ORDER BY page_no ASC")
    fun observeForChapter(chapterId: String): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE chapter_id = :chapterId")
    suspend fun getForChapter(chapterId: String): List<PageEntity>

    @Query(
        """SELECT COUNT(*) FROM pages
           WHERE chapter_id = :chapterId AND status = :status""",
    )
    suspend fun countByStatus(chapterId: String, status: String): Int

    @Query("DELETE FROM pages WHERE chapter_id = :chapterId")
    suspend fun deleteForChapter(chapterId: String)
}