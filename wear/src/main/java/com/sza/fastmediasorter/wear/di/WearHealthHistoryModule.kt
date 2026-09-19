package com.sza.fastmediasorter.wear.di

import android.content.Context
import androidx.room.Room
import com.sza.fastmediasorter.wear.data.db.BloodPressureCalibrationDao
import com.sza.fastmediasorter.wear.data.db.BloodPressureHistoryDao
import com.sza.fastmediasorter.wear.data.db.HeartRateHistoryDao
import com.sza.fastmediasorter.wear.data.db.WearBloodPressureDatabase
import com.sza.fastmediasorter.wear.data.db.WearBloodPressureMigrations
import com.sza.fastmediasorter.wear.data.db.WearHeartRateDatabase
import com.sza.fastmediasorter.wear.data.repository.BloodPressureCalibrationRepositoryImpl
import com.sza.fastmediasorter.wear.data.repository.BloodPressureHistoryRepositoryImpl
import com.sza.fastmediasorter.wear.data.repository.HeartRateHistoryRepositoryImpl
import com.sza.fastmediasorter.wear.data.repository.PpgWindowFileRepository
import com.sza.fastmediasorter.wear.domain.repository.BloodPressureCalibrationRepository
import com.sza.fastmediasorter.wear.domain.repository.BloodPressureHistoryRepository
import com.sza.fastmediasorter.wear.domain.repository.HeartRateHistoryRepository
import com.sza.fastmediasorter.wear.domain.repository.PpgWindowRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Singleton

/**
 * Hilt DI module providing databases, DAOs and repositories for Wear health history (Heart Rate & Blood Pressure).
 */
@Module
@InstallIn(SingletonComponent::class)
object WearHealthHistoryModule {

    @Provides
    @Singleton
    @Suppress("TooGenericExceptionCaught")
    fun provideWearHeartRateDatabase(
        @ApplicationContext context: Context
    ): WearHeartRateDatabase = try {
        buildWearHeartRateDatabase(context).also { it.openHelper.writableDatabase }
    } catch (e: RuntimeException) {
        Timber.e(e, "Wear heart-rate database failed to open - recreating it")
        context.deleteDatabase(WearHeartRateDatabase.DATABASE_NAME)
        buildWearHeartRateDatabase(context).also { it.openHelper.writableDatabase }
    }

    private fun buildWearHeartRateDatabase(context: Context): WearHeartRateDatabase =
        Room.databaseBuilder(
            context,
            WearHeartRateDatabase::class.java,
            WearHeartRateDatabase.DATABASE_NAME
        ).build()

    @Provides
    @Singleton
    fun provideHeartRateHistoryDao(database: WearHeartRateDatabase): HeartRateHistoryDao =
        database.heartRateHistoryDao()

    @Provides
    @Singleton
    fun provideHeartRateHistoryRepository(
        impl: HeartRateHistoryRepositoryImpl
    ): HeartRateHistoryRepository = impl

    @Provides
    @Singleton
    @Suppress("TooGenericExceptionCaught")
    fun provideWearBloodPressureDatabase(
        @ApplicationContext context: Context
    ): WearBloodPressureDatabase = try {
        buildWearBloodPressureDatabase(context).also { it.openHelper.writableDatabase }
    } catch (e: RuntimeException) {
        Timber.e(e, "Wear blood pressure database failed to open - recreating it")
        context.deleteDatabase(WearBloodPressureDatabase.DATABASE_NAME)
        buildWearBloodPressureDatabase(context).also { it.openHelper.writableDatabase }
    }

    /**
     * S3113: the migration is registered rather than left to the open-failure fallback above, which
     * deletes the file - the owner's diary rows must survive version 2.
     */
    private fun buildWearBloodPressureDatabase(context: Context): WearBloodPressureDatabase =
        Room.databaseBuilder(
            context,
            WearBloodPressureDatabase::class.java,
            WearBloodPressureDatabase.DATABASE_NAME
        ).addMigrations(
            WearBloodPressureMigrations.MIGRATION_1_2,
            WearBloodPressureMigrations.MIGRATION_2_3
        ).build()

    @Provides
    @Singleton
    fun provideBloodPressureHistoryDao(
        database: WearBloodPressureDatabase
    ): BloodPressureHistoryDao = database.bloodPressureHistoryDao()

    @Provides
    @Singleton
    fun provideBloodPressureHistoryRepository(
        impl: BloodPressureHistoryRepositoryImpl
    ): BloodPressureHistoryRepository = impl

    @Provides
    @Singleton
    fun provideBloodPressureCalibrationDao(
        database: WearBloodPressureDatabase
    ): BloodPressureCalibrationDao = database.bloodPressureCalibrationDao()

    @Provides
    @Singleton
    fun provideBloodPressureCalibrationRepository(
        impl: BloodPressureCalibrationRepositoryImpl
    ): BloodPressureCalibrationRepository = impl

    @Provides
    @Singleton
    fun providePpgWindowRepository(
        impl: PpgWindowFileRepository
    ): PpgWindowRepository = impl
}
