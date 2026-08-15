package com.sza.fastmediasorter.domain.identity

import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.sza.fastmediasorter.core.di.RepositoryModule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Modifier
import java.time.Instant

/**
 * S1657: [PrimaryGoogleAccount] is Gson field-reflection persisted into the `primary_google_account_v1`
 * encrypted preferences, which outlive an app update. Two minified builds can hold different R8 mappings, so
 * an unpinned field name is renamed between the write and the read and the stored binding stops parsing -
 * silently, because the store degrades a failed read to "no account bound".
 *
 * Asserting only that the expected keys appear would be worthless: on a non-obfuscated JVM the field name
 * already equals the wire name, so such a test passes with the annotations removed. The first guard therefore
 * reflects the annotation itself, which is the regression that reintroduces the desync.
 *
 * The remaining guards freeze the wire format of the two properties whose shape is not obvious from the
 * declaration - a boxed value class and a platform class. Their JSON is what is
 * already sitting in every user's preferences, so a change there costs users their account binding for no R8
 * benefit; these assertions exist to make such a change fail loudly instead of shipping.
 */
class PrimaryGoogleAccountSerializationTest {

    // S1668: the store writes with the injected Gson, which now carries an Instant adapter. A bare Gson() here
    // would keep asserting a format the app no longer produces, so dropping the registration would pass unseen.
    private val gson = RepositoryModule.provideGson()

    private val account = PrimaryGoogleAccount(
        email = "user@example.com",
        displayName = "Test User",
        photoUrl = "https://example.com/avatar.png",
        grantedScopes = setOf(GoogleScope.EMAIL, GoogleScope.DRIVE_READONLY),
        boundAt = Instant.ofEpochSecond(BOUND_AT_SECONDS, BOUND_AT_NANOS.toLong()),
    )

    @Test
    fun `every persisted field pins its wire name via SerializedName`() {
        listOf(PrimaryGoogleAccount::class.java, GoogleScope::class.java).forEach { type ->
            // Instance backing fields only: skip the Compose-compiler `$stable` static field and synthetics.
            type.declaredFields
                .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
                .forEach { field ->
                    val annotation = requireNotNull(field.getAnnotation(SerializedName::class.java)) {
                        "${type.simpleName}.${field.name} must carry @SerializedName (R8 renames unpinned fields)"
                    }
                    assertEquals(
                        "${type.simpleName}.${field.name} @SerializedName value must equal the field name",
                        field.name,
                        annotation.value,
                    )
                }
        }
    }

    @Test
    fun `account json carries exactly the pinned top level keys`() {
        val json = JsonParser.parseString(gson.toJson(account)).asJsonObject

        assertEquals(
            listOf("email", "displayName", "photoUrl", "grantedScopes", "boundAt"),
            json.keySet().toList(),
        )
    }

    @Test
    fun `granted scopes are written as boxed objects keyed by the pinned value field`() {
        val scopes = gson.toJson(setOf(GoogleScope.EMAIL), object : TypeToken<Set<GoogleScope>>() {}.type)

        assertEquals("""[{"value":"email"}]""", scopes)
    }

    @Test
    fun `bound instant is written as the seconds and nanos object of java time Instant`() {
        val json = JsonParser.parseString(gson.toJson(account)).asJsonObject

        assertEquals("""{"seconds":$BOUND_AT_SECONDS,"nanos":$BOUND_AT_NANOS}""", json.get("boundAt").toString())
    }

    @Test
    fun `account round trips through the injected gson`() {
        assertEquals(account, gson.fromJson(gson.toJson(account), PrimaryGoogleAccount::class.java))
    }

    @Test
    fun `account stored in the current format decodes with every field intact`() {
        val storedJson = """
            {"email":"user@example.com","displayName":"Test User",
             "photoUrl":"https://example.com/avatar.png",
             "grantedScopes":[{"value":"email"},{"value":"https://www.googleapis.com/auth/drive.readonly"}],
             "boundAt":{"seconds":$BOUND_AT_SECONDS,"nanos":$BOUND_AT_NANOS}}
        """.trimIndent()

        val decoded = gson.fromJson(storedJson, PrimaryGoogleAccount::class.java)

        assertEquals(account, decoded)
    }

    private companion object {
        const val BOUND_AT_SECONDS = 1700000000L
        const val BOUND_AT_NANOS = 123456789
    }
}
