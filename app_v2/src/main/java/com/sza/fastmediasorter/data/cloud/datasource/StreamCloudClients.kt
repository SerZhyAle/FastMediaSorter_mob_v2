package com.sza.fastmediasorter.data.cloud.datasource

import androidx.media3.datasource.DataSource
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.cloud.OneDriveRestClient
import javax.inject.Inject
import javax.inject.Singleton

/** Builds the `cloud://` [DataSource.Factory] the background audio service streams from. */
@Singleton
class StreamCloudClients @Inject constructor(
    private val googleDriveClient: GoogleDriveRestClient,
    private val oneDriveClient: OneDriveRestClient,
    private val dropboxClient: DropboxClient,
) {
    fun dataSourceFactory(): DataSource.Factory = CloudDataSourceFactory(
        mapOf(
            "googledrive" to googleDriveClient,
            "onedrive" to oneDriveClient,
            "dropbox" to dropboxClient,
        )
    )
}
