package com.sza.fastmediasorter.data.remote.sftp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * S3415: the stored host-key pin is applied in [SftpClient], in front of the one pool it owns. That holds
 * only while no other code opens an SSH session of its own - a new `JSch()` or a second
 * `SftpConnectionPool()` would connect without the pin, which is the defect this ticket closed.
 * The connection tester keeps its own `JSch()`: it verifies a pin the add/edit form passes explicitly.
 */
class SftpSessionChokePointGuardTest {

    private val mainSources: List<File> by lazy {
        val root = listOf(File("src/main/java"), File("app_v2/src/main/java")).first { it.isDirectory }
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private fun filesConstructing(token: String): Set<String> = mainSources
        .filter { file -> file.readLines().any { line -> constructs(line, token) } }
        .map { it.name }
        .toSet()

    private fun constructs(line: String, token: String): Boolean {
        val code = line.substringBefore("//").trim()
        return !code.startsWith("*") && code.contains(token)
    }

    @Test
    fun `sources are found`() {
        assertTrue("no Kotlin sources found from ${File(".").absolutePath}", mainSources.isNotEmpty())
    }

    @Test
    fun `JSch sessions are created only by the pool and the connection tester`() {
        assertEquals(
            setOf("SftpConnectionPool.kt", "SftpConnectionTester.kt"),
            filesConstructing("JSch()"),
        )
    }

    @Test
    fun `the pool is created only by SftpClient, which applies the stored pin`() {
        assertEquals(setOf("SftpClient.kt"), filesConstructing("SftpConnectionPool()"))
    }
}
