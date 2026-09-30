package com.sza.fastmediasorter.wear.di

import com.sza.fastmediasorter.wear.data.repository.PhoneCompanionRepositoryImpl
import com.sza.fastmediasorter.wear.domain.repository.PhoneCompanionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** S4011: kept out of [WearAppModule], which sits at detekt's TooManyFunctions ceiling. */
@Module
@InstallIn(SingletonComponent::class)
abstract class WearPhoneCompanionModule {

    @Binds
    abstract fun bindPhoneCompanionRepository(impl: PhoneCompanionRepositoryImpl): PhoneCompanionRepository
}
