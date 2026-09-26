package com.sza.fastmediasorter

import java.io.File

object TestFixtures {

    const val DEFAULT_USER = "test-default-user"
    const val DEFAULT_SHARE_PATH = "/test-share"
    const val TEST_SMB_RESOURCE_NAME = "Test-SMB"
    const val TEST_SFTP_RESOURCE_NAME = "Test-SFTP"
    const val TEST_FTP_RESOURCE_NAME = "Test-FTP"
    const val TEST_LOCAL_FOLDER = "/storage/emulated/0/TestMedia"
    const val TEST_CLOUD_RESOURCE_NAME = "Test-Cloud"

    /** S3741: what scripts/devtest/selftest-provision.ps1 pushes into [TEST_LOCAL_FOLDER]. */
    val DEVICE_FIXTURE_FILES = listOf("tiny_avc_aac.ts", "tiny_opus.webm")

    fun createTempFile(dir: File, name: String, content: String = "test"): File {
        val file = File(dir, name)
        file.writeText(content)
        return file
    }
}
