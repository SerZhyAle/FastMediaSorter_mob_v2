package com.sza.fastmediasorter.data.resourceshare

import android.util.Xml
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceShareFormat
import org.xmlpull.v1.XmlSerializer
import java.io.StringWriter
import javax.inject.Inject

/**
 * Serializes resources into the share-file XML (S0422). Element and attribute names mirror the
 * bundled-config format, so a file produced here is read back by the existing importer unchanged.
 *
 * Credential values are written in plaintext by owner decision (strategic ADR-3); the UI warns the
 * user before producing or importing a file. A bundled-asset SSH key is not portable, so key-auth
 * resources are filtered out by the caller before reaching this serializer.
 */
class ResourceShareSerializer @Inject constructor() {

    fun serialize(
        resources: List<MediaResource>,
        credentialsById: Map<String, NetworkCredentialsEntity>,
    ): String {
        val writer = StringWriter()
        val serializer = Xml.newSerializer()
        serializer.setOutput(writer)
        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)
        serializer.startDocument("utf-8", true)
        serializer.startTag(null, ResourceShareFormat.ROOT_TAG)
        serializer.attribute(null, ResourceShareFormat.ATTR_VERSION, ResourceShareFormat.FORMAT_VERSION.toString())
        for (resource in resources) {
            writeResource(serializer, resource, credentialsById[resource.credentialsId])
        }
        serializer.endTag(null, ResourceShareFormat.ROOT_TAG)
        serializer.endDocument()
        return writer.toString()
    }

    private fun writeResource(
        serializer: XmlSerializer,
        resource: MediaResource,
        credential: NetworkCredentialsEntity?,
    ) {
        serializer.startTag(null, ResourceShareFormat.RESOURCE_TAG)
        serializer.attribute(null, "name", resource.name)
        serializer.attribute(null, "path", resource.path)
        serializer.attribute(null, "type", resource.type.name)
        serializer.attribute(null, "supportedMediaTypes", resource.supportedMediaTypes.joinToString(",") { it.name })
        serializer.attribute(null, "sortMode", resource.sortMode.name)
        serializer.attribute(null, "displayMode", resource.displayMode.name)
        serializer.attribute(null, "scanSubdirectories", resource.scanSubdirectories.toString())
        serializer.attribute(null, "disableThumbnails", resource.disableThumbnails.toString())
        serializer.attribute(null, "allFiles", resource.allFiles.toString())
        serializer.attribute(null, "showHiddenFiles", resource.showHiddenFiles.toString())
        serializer.attribute(null, "rememberTheFileList", resource.rememberFileList.toString())
        serializer.attribute(null, "addToDestinations", resource.isDestination.toString())
        resource.accessPin?.let { serializer.attribute(null, "pin", it) }
        resource.hostKeyFingerprint?.let { serializer.attribute(null, "hostKeyFingerprint", it) }
        if (credential != null && resource.type.isNetworkResource) {
            serializer.attribute(null, "username", credential.username)
            serializer.attribute(null, "password", credential.password)
        }
        serializer.endTag(null, ResourceShareFormat.RESOURCE_TAG)
    }
}
