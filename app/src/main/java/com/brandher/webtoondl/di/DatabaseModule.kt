package com.brandher.webtoondl.di

import android.content.Context
import androidx.room.Room
import com.brandher.webtoondl.data.db.AppDatabase
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
            // Temporal: se definirán migraciones reales al estabilizar el esquema en las primeras etapas.
            .fallbackToDestructiveMigration()
            .build()
}