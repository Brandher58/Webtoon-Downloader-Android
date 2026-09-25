package com.brandher.webtoondl.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.brandher.webtoondl.data.db.dao.ChapterDao
import com.brandher.webtoondl.data.db.dao.PageDao
import com.brandher.webtoondl.data.db.dao.ReadingPositionDao
import com.brandher.webtoondl.data.db.dao.SeriesDao

@Database(
    entities = [
        SeriesEntity::class,
        ChapterEntity::class,
        PageEntity::class,
        ReadingPositionEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun seriesDao(): SeriesDao
    abstract fun chapterDao(): ChapterDao
    abstract fun pageDao(): PageDao
    abstract fun readingPositionDao(): ReadingPositionDao
}