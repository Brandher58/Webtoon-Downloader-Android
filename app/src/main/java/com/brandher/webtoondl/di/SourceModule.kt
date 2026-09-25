package com.brandher.webtoondl.di

import com.brandher.webtoondl.data.source.SourceRegistry
import com.brandher.webtoondl.data.source.manhwa.ManhwawebSource
import com.brandher.webtoondl.data.source.webtoon.WebtoonSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SourceModule {

    @Provides
    @Singleton
    fun provideSourceRegistry(
        webtoonSource: WebtoonSource,
        manhwawebSource: ManhwawebSource,
    ): SourceRegistry =
        SourceRegistry(sources = listOf(webtoonSource, manhwawebSource))
}