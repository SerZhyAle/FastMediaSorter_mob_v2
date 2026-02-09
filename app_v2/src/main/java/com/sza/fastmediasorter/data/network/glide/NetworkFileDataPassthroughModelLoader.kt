package com.sza.fastmediasorter.data.network.glide

import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey

/**
 * Passthrough ModelLoader that allows NetworkFileData to be used directly by ResourceDecoders.
 * This is required for NetworkVideoFrameDecoder which needs the full NetworkFileData object,
 * not just an InputStream.
 */
class NetworkFileDataPassthroughModelLoader : ModelLoader<NetworkFileData, NetworkFileData> {

    companion object {
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mov", "avi", "mkv", "webm", "3gp", "flv", "wmv", "m4v", "mpg", "mpeg"
        )
    }

    override fun buildLoadData(
        model: NetworkFileData,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<NetworkFileData> {
        return ModelLoader.LoadData(
            ObjectKey(model.getCacheKey()),
            NetworkFileDataFetcherPassthrough(model)
        )
    }

    override fun handles(model: NetworkFileData): Boolean {
        val extension = model.path.substringAfterLast('.', "").lowercase()
        return extension in VIDEO_EXTENSIONS
    }

    class Factory : ModelLoaderFactory<NetworkFileData, NetworkFileData> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<NetworkFileData, NetworkFileData> {
            return NetworkFileDataPassthroughModelLoader()
        }

        override fun teardown() {
            // Do nothing
        }
    }
}

class NetworkFileDataFetcherPassthrough(
    private val model: NetworkFileData
) : DataFetcher<NetworkFileData> {

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in NetworkFileData>) {
        callback.onDataReady(model)
    }

    override fun cleanup() {
        // Do nothing
    }

    override fun cancel() {
        // Do nothing
    }

    override fun getDataClass(): Class<NetworkFileData> {
        return NetworkFileData::class.java
    }

    override fun getDataSource(): DataSource {
        return DataSource.LOCAL
    }
}
