package com.sza.fastmediasorter.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ResourceEntity::class,
        NetworkCredentialsEntity::class,
        ResourceFtsEntity::class,
        FavoritesEntity::class,
        PlaybackPositionEntity::class,
        ThumbnailCacheEntity::class,
        CachedFileListEntity::class,
        FileMetadataCacheEntity::class,
        PendingRevocationEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun resourceDao(): ResourceDao
    abstract fun networkCredentialsDao(): NetworkCredentialsDao
    abstract fun pendingRevocationDao(): PendingRevocationDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun playbackPositionDao(): PlaybackPositionDao
    abstract fun thumbnailCacheDao(): ThumbnailCacheDao
    abstract fun cachedFileListDao(): CachedFileListDao
    abstract fun fileMetadataCacheDao(): FileMetadataCacheDao
}
