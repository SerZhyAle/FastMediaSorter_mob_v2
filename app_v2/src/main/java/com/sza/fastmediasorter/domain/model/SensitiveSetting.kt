package com.sza.fastmediasorter.domain.model

/**
 * S1254: marks an [AppSettings] field whose value must never appear in plain text in the
 * startup settings dump ([com.sza.fastmediasorter.FastMediaSorterApp]).
 *
 * The dump's name-based matcher (`*password*`, `*token*`..) silently dies when R8 renames
 * fields - which already leaked a real password into exported diagnostics twice. Annotation
 * presence is checked via class identity (`Field.isAnnotationPresent`), so it survives any
 * renaming; `-keepattributes *Annotation*` in proguard-rules.pro retains it at runtime.
 *
 * Apply as `@field:SensitiveSetting` so Java reflection over declared fields sees it.
 * The `assert-sensitive-settings-annotated` fast gate enforces that every hint-matching
 * field carries it.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class SensitiveSetting
