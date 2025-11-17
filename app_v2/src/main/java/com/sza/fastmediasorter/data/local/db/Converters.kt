package com.sza.fastmediasorter.data.local.db

import androidx.room.TypeConverter
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode

class Converters {
    
    @TypeConverter
    fun fromResourceType(value: ResourceType): String = value.name
    
    @TypeConverter
    fun toResourceType(value: String): ResourceType = ResourceType.valueOf(value)
    
    @TypeConverter
    fun fromSortMode(value: SortMode): String = value.name
    
    @TypeConverter
    fun toSortMode(value: String): SortMode = SortMode.valueOf(value)
    
    @TypeConverter
    fun fromDisplayMode(value: DisplayMode): String = value.name
    
    @TypeConverter
    fun toDisplayMode(value: String): DisplayMode = DisplayMode.valueOf(value)
    
    @TypeConverter
    fun fromCloudProvider(value: CloudProvider?): String? = value?.name
    
    @TypeConverter
    fun toCloudProvider(value: String?): CloudProvider? = value?.let { CloudProvider.valueOf(it) }
}
