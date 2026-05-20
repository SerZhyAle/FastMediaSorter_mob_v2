package com.sza.fastmediasorter.core.di

import com.sza.fastmediasorter.core.path.CanonicalPathNormalizer
import com.sza.fastmediasorter.domain.path.PathNormalizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PathNormalizerModule {

    @Binds
    @Singleton
    abstract fun bindPathNormalizer(
        impl: CanonicalPathNormalizer
    ): PathNormalizer
}
