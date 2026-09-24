package com.sza.fastmediasorter.data.remote.sftp.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.AccessDeniedException
import java.nio.file.NoSuchFileException

/** The chroot boundary of the embedded SFTP server: path canonicalisation and mount mapping. */
class SafPathResolverTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun resolverOver(vararg dirs: File) = SafPathResolver(dirs.map(::FileServerNode))

    @Test
    fun `normalize drops empty and dot segments and applies parent steps`() {
        assertEquals(listOf("a", "c"), SafPathResolver.normalize("//a/./b/../c/"))
    }

    @Test
    fun `normalize refuses a parent step above the root`() {
        assertThrows(AccessDeniedException::class.java) { SafPathResolver.normalize("/..") }
        assertThrows(AccessDeniedException::class.java) { SafPathResolver.normalize("/a/../../etc/passwd") }
        assertThrows(AccessDeniedException::class.java) { SafPathResolver.normalize("../x") }
    }

    @Test
    fun `normalize treats percent-encoded traversal as a literal name`() {
        assertEquals(listOf("a", "%2e%2e", "b"), SafPathResolver.normalize("/a/%2e%2e/b"))
        assertEquals(listOf("..%2f"), SafPathResolver.normalize("/..%2f"))
    }

    @Test
    fun `normalize refuses NUL`() {
        assertThrows(AccessDeniedException::class.java) { SafPathResolver.normalize("/a\u0000b") }
    }

    @Test
    fun `normalize is stable across consecutive runs`() {
        val once = SafPathResolver.normalize("/a/b/../c/./d")
        val twice = SafPathResolver.normalize(once.joinToString("/", prefix = "/"))
        assertEquals(once, twice)
    }

    @Test
    fun `mount names keep pick order and suffix duplicates`() {
        assertEquals(
            listOf("DCIM", "Music", "DCIM (2)", "folder", "DCIM (3)"),
            SafPathResolver.mountNames(listOf("DCIM", "Music", "DCIM", " ", "DCIM")),
        )
    }

    @Test
    fun `root resolves to the virtual directory`() {
        val resolver = resolverOver(temp.newFolder("Photos"))
        assertSame(SafPathResolver.Target.VirtualRoot, resolver.resolve("/"))
        assertSame(SafPathResolver.Target.VirtualRoot, resolver.resolve(""))
        assertSame(SafPathResolver.Target.VirtualRoot, resolver.resolve("/Photos/.."))
    }

    @Test
    fun `a path inside a root resolves to its node`() {
        val photos = temp.newFolder("Photos")
        File(photos, "2026").mkdir()
        File(photos, "2026/cat.jpg").writeText("x")
        val target = resolverOver(photos).resolve("/Photos/2026/cat.jpg")
        target as SafPathResolver.Target.Existing
        assertEquals("cat.jpg", target.node.name)
        assertEquals(false, target.isMountRoot)
    }

    @Test
    fun `the mount itself is flagged as a mount root`() {
        val target = resolverOver(temp.newFolder("Photos")).resolve("/Photos") as SafPathResolver.Target.Existing
        assertTrue(target.isMountRoot)
    }

    @Test
    fun `a missing leaf resolves to Absent under its existing parent`() {
        val photos = temp.newFolder("Photos")
        val target = resolverOver(photos).resolve("/Photos/new.jpg") as SafPathResolver.Target.Absent
        assertEquals("new.jpg", target.name)
        assertEquals("Photos", target.parent.name)
    }

    @Test
    fun `a missing intermediate directory or an unknown mount is NoSuchFile`() {
        val resolver = resolverOver(temp.newFolder("Photos"))
        assertThrows(NoSuchFileException::class.java) { resolver.resolve("/Photos/missing/new.jpg") }
        assertThrows(NoSuchFileException::class.java) { resolver.resolve("/Elsewhere/x") }
    }

    @Test
    fun `a traversal out of a root is refused rather than clamped`() {
        val resolver = resolverOver(temp.newFolder("Photos"))
        assertThrows(AccessDeniedException::class.java) { resolver.resolve("/Photos/../../secret") }
    }

    @Test
    fun `a sibling directory of a root is unreachable by name`() {
        val photos = temp.newFolder("Photos")
        temp.newFolder("Private")
        val resolver = resolverOver(photos)
        assertThrows(NoSuchFileException::class.java) { resolver.resolve("/Private") }
        assertSame(SafPathResolver.Target.VirtualRoot, resolver.resolve("/Photos/../Private/.."))
    }

    @Test
    fun `several roots map to their mounts in pick order`() {
        val first = File(temp.newFolder("a"), "Media").apply { mkdir() }
        val second = File(temp.newFolder("b"), "Media").apply { mkdir() }
        File(second, "only-in-second.txt").writeText("x")
        val resolver = resolverOver(first, second)
        assertEquals(listOf("Media", "Media (2)"), resolver.mounts.map { it.name })
        val target = resolver.resolve("/Media (2)/only-in-second.txt")
        assertTrue(target is SafPathResolver.Target.Existing)
        assertThrows(NoSuchFileException::class.java) { resolver.resolve("/Media/nope/x") }
    }
}
