package com.brandher.webtoondl.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SeriesEntity::class,
        ChapterEntity::class,
        PageEntity::class,
        ReadingPositionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase()