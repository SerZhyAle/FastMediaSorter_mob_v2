package com.sza.fastmediasorter.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SecretMaskerTest {

    @Test
    fun `sanitize masks the bearer token and keeps the scheme`() {
        val out = SecretMasker.sanitize("Authorization: Bearer abc.def.ghi")
        assertEquals("Authorization: Bearer [REDACTED]", out)
        assertFalse(out.contains("abc.def.ghi"))
    }

    @Test
    fun `sanitize masks a basic credential`() {
        assertEquals("authorization=Basic [REDACTED]", SecretMasker.sanitize("authorization=Basic dXNlcjpw"))
    }

    @Test
    fun `sanitize masks a plain key value pair`() {
        assertEquals("connect password=[REDACTED] ok", SecretMasker.sanitize("connect password=hunter ok"))
    }

    @Test
    fun `sanitize leaves text without secrets untouched`() {
        assertEquals("nothing to hide here", SecretMasker.sanitize("nothing to hide here"))
    }

    @Test
    fun `sanitize leaves look-alike key names untouched`() {
        val text = "bypass=true cachePolicy=LRU tokens=5 spinner=on"
        assertEquals(text, SecretMasker.sanitize(text))
    }

    // DIAGNOSTIC-REPORT 0.12, reference/redaction-structured-value.txt, vector 1.
    @Test
    fun `catalog vector - a json blob under a benign key loses its access pin`() {
        assertEquals(
            """folderParams={"folders":[{"label":"Family","accessPin":"[REDACTED]","slideshowInterval":15}]}""",
            SecretMasker.sanitize(
                """folderParams={"folders":[{"label":"Family","accessPin":"4821","slideshowInterval":15}]}""",
            ),
        )
    }

    // DIAGNOSTIC-REPORT 0.12, reference/redaction-structured-value.txt, vector 2.
    @Test
    fun `catalog vector - a query string under a benign key loses its token`() {
        assertEquals(
            "lastOpened=https://media.example.test/play?id=17&token=[REDACTED]&lang=en",
            SecretMasker.sanitize("lastOpened=https://media.example.test/play?id=17&token=Zk93mQ&lang=en"),
        )
    }

    // DIAGNOSTIC-REPORT 0.12, reference/redaction-structured-value.txt, vector 3.
    @Test
    fun `catalog vector - a connection string under a benign key loses its password`() {
        assertEquals(
            "source=Host=nas.example.test;Port=22;User=guest;Password=[REDACTED];Timeout=15",
            SecretMasker.sanitize("source=Host=nas.example.test;Port=22;User=guest;Password=Hq7-pL2x;Timeout=15"),
        )
    }

    @Test
    fun `sanitize masks signed cdn parameters and keeps their names`() {
        val out = SecretMasker.sanitize(
            "GET https://cdn.example.test/v.m3u8?X-Amz-Credential=AKIA1%2F2&X-Amz-Signature=ab12" +
                "&amp;wmsAuthSign=c2Vy&hdnts=exp=1~acl=/*~hmac=ff&Policy=eyJ&Key-Pair-Id=K2&client_secret=s1" +
                "&refresh_token=r1&pass=p1&auth=a1&n=7",
        )
        assertEquals(
            "GET https://cdn.example.test/v.m3u8?X-Amz-Credential=[REDACTED]&X-Amz-Signature=[REDACTED]" +
                "&amp;wmsAuthSign=[REDACTED]&hdnts=[REDACTED]&Policy=[REDACTED]&Key-Pair-Id=[REDACTED]" +
                "&client_secret=[REDACTED]&refresh_token=[REDACTED]&pass=[REDACTED]&auth=[REDACTED]&n=7",
            out,
        )
    }

    @Test
    fun `sanitize masks a quoted json password`() {
        assertEquals(
            """{"password":"[REDACTED]","user":"bob"}""",
            SecretMasker.sanitize("""{"password":"x y","user":"bob"}"""),
        )
    }

    @Test
    fun `sanitize masks the account segments of an xtream path`() {
        assertEquals(
            "open http://panel.example.test:8080/live/[REDACTED]/[REDACTED]/1234.ts",
            SecretMasker.sanitize("open http://panel.example.test:8080/live/alice/s3cr3t/1234.ts"),
        )
    }

    @Test
    fun `sanitize strips url userinfo whose password holds a slash`() {
        assertEquals(
            "play http://[REDACTED]@host.example.test/stream",
            SecretMasker.sanitize("play http://user:pa/ss?#x@host.example.test/stream"),
        )
    }

    @Test
    fun `sanitize substitutes the app data directory`() {
        assertEquals(
            "mirroring to <APP_DATA>/files/logs/a.log and <APP_DATA>/cache",
            SecretMasker.sanitize(
                "mirroring to /data/user/0/com.sza.fastmediasorter/files/logs/a.log and /data/data/com.sza.x/cache",
            ),
        )
    }

    @Test
    fun `sanitize is idempotent`() {
        val once = SecretMasker.sanitize("token=abc smb://u:p@h/s /data/user/0/com.a.b/x")
        assertEquals(once, SecretMasker.sanitize(once))
    }

    @Test
    fun `maskPath strips userinfo whose password contains at signs`() {
        assertEquals("smb://[REDACTED]@host/share", SecretMasker.maskPath("smb://user:p@ss@host/share"))
    }

    @Test
    fun `maskPath strips simple userinfo`() {
        assertEquals("smb://[REDACTED]@host/share", SecretMasker.maskPath("smb://user:pass@host/share"))
    }

    @Test
    fun `maskPath leaves a port without credentials untouched`() {
        assertEquals("smb://host:445/share", SecretMasker.maskPath("smb://host:445/share"))
    }

    @Test
    fun `maskFull does not disclose the length`() {
        assertEquals("[REDACTED]", SecretMasker.maskFull("mySecret123"))
    }

    @Test
    fun `empty inputs report empty`() {
        assertEquals("(empty)", SecretMasker.maskPath(""))
        assertEquals("(empty)", SecretMasker.maskFull(null))
    }
}
