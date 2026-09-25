package com.brandher.webtoondl.di

import android.content.Context
import androidx.room.Room
import com.brandher.webtoondl.data.db.AppDatabase
import com.brandher.webtoondl.data.db.MIGRATION_1_2
import com.brandher.webtoondl.data.db.MIGRATION_2_3
import com.brandher.webtoondl.data.db.dao.ChapterDao
import com.brandher.webtoondl.data.db.dao.PageDao
import com.brandher.webtoondl.data.db.dao.ReadingPositionDao
import com.brandher.webtoondl.data.db.dao.SeriesDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "webtoondl.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides
    fun provideSeriesDao(db: AppDatabase): SeriesDao = db.seriesDao()

    @Provides
    fun provideChapterDao(db: AppDatabase): ChapterDao = db.chapterDao()

    @Provides
    fun providePageDao(db: AppDatabase): PageDao = db.pageDao()

    @Provides
    fun provideReadingPositionDao(db: AppDatabase): ReadingPositionDao = db.readingPositionDao()
}