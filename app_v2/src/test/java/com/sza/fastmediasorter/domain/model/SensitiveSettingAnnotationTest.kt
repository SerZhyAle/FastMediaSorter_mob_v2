package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1254: the settings-dump mask consults [SensitiveSetting] via Java reflection over declared
 * fields. The annotation must therefore be FIELD-targeted and runtime-retained - a silent
 * `@property:` placement would compile fine and mask nothing. This pins the wiring.
 */
class SensitiveSettingAnnotationTest {

    @Test
    fun defaultPasswordFieldCarriesRuntimeVisibleSensitiveSetting() {
        val field = AppSettings::class.java.getDeclaredField("defaultPassword")
        assertTrue(
            "defaultPassword must carry @field:SensitiveSetting visible to Java reflection",
            field.isAnnotationPresent(SensitiveSetting::class.java)
        )
    }
}
