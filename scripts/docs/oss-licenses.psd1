# S1495 - licence manifest for the OSS notice generator.
#
# Keyed by "group:artifact" - never by version, because a version bump must not require a
# manifest edit while a new dependency must.
#
# A shipping coordinate missing from this file is a FATAL error in
# scripts/docs/generate-oss-notices.ps1, not a skipped row. That refusal is the whole
# mechanism: it is what stops a newly added dependency from silently going unlisted, which
# is exactly how the published page came to name 2 libraries out of 97 (S1495 ADR-3).
#
# Adding a dependency to app_v2/build.gradle.kts or wear/build.gradle.kts means adding it
# here in the same change.
#
# Fields
#   Name        human-readable library name shown on the page
#   Spdx        SPDX identifier, or the exact proprietary licence name when not OSS
#   LicenseUrl  canonical licence text
#   SourceUrl   project source, or the vendor's terms page for a proprietary SDK
#   Oss         $false marks a component distributed under vendor terms rather than an
#               open source licence; the page groups those separately instead of calling
#               them open source
#   Transitive  $true when no declaration names it - it arrives through another artifact
#   Via         the coordinate that pulls a transitive entry in
#   Bundled     $true for a binary shipped from app_v2/libs rather than resolved from Maven
#   ShippedInOverride  flavor list for an entry the parser cannot see (bundled binaries)
#
# Licence values are taken from each project's own LICENSE file, POM or vendor terms page.

@{
}
