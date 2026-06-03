package com.sza.fastmediasorter.core.systeminfo

/**
 * One field of an extended-diagnostics section. [sensitive] marks values that must be masked by
 * default in the on-screen text, on normal copy and on share (e.g. signing-cert hash, local
 * network addresses, mount paths). The real value is revealed only on an explicit user action.
 */
data class ExtendedDiagnosticsField(
    val label: String,
    val value: String,
    val sensitive: Boolean = false,
)

/** One labelled group of extended-diagnostics [fields]. Pure data, no Android dependencies. */
data class ExtendedDiagnosticsSection(
    val title: String,
    val fields: List<ExtendedDiagnosticsField>,
)

/**
 * Supplies extra diagnostic sections appended after the base System info report.
 *
 * Implementations live ONLY in a flavor source set (currently `src/noLegal/java`) and are bound
 * `@IntoSet`; non-contributing flavors inject an empty set, so the base report is unchanged.
 * Every field must degrade gracefully ("unknown" / "n/a") when an API or source is unavailable -
 * a contributor must never throw out of [sections].
 */
interface ExtendedDiagnosticsContributor {
    fun sections(): List<ExtendedDiagnosticsSection>
}

/** Placeholder shown in place of a [ExtendedDiagnosticsField.sensitive] value until revealed. */
const val REDACTED = "[REDACTED]"

/**
 * Map extended-diagnostics sections to the base [SystemInfoSection] model so they render through the
 * same grouped-text formatter. When [reveal] is false, sensitive values are replaced with [REDACTED].
 */
fun List<ExtendedDiagnosticsSection>.toSystemInfoSections(reveal: Boolean): List<SystemInfoSection> =
    map { section ->
        SystemInfoSection(
            title = section.title,
            fields = section.fields.map { field ->
                field.label to if (field.sensitive && !reveal) REDACTED else field.value
            },
        )
    }
