package com.brandher.webtoondl.di

import com.brandher.webtoondl.data.export.ExportRepositoryImpl
import com.brandher.webtoondl.data.repository.SeriesRepositoryImpl
import com.brandher.webtoondl.domain.repo.DownloadRepository
import com.brandher.webtoondl.domain.repo.ExportRepository
import com.brandher.webtoondl.domain.repo.SeriesRepository
import com.brandher.webtoondl.download.DownloadManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSeriesRepository(impl: SeriesRepositoryImpl): SeriesRepository

    @Binds
    @Singleton
    abstract fun bindDownloadRepository(impl: DownloadManager): DownloadRepository

    @Binds
    @Singleton
    abstract fun bindExportRepository(impl: ExportRepositoryImpl): ExportRepository
}