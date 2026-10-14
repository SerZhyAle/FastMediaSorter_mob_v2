---
layout: default
title: "Build and Verification"
permalink: /docs/BUILD_AND_VERIFICATION.html
lang: en
---

<sub class="doc-stamp">26.10.06 14:18</sub>

# Build and Verification

[Technical specification](V2_Specification.html) | [Technology stack](TECH_STACK.html) | [Requirements](TECHNICAL_REQUIREMENTS.html)

This page describes the public build and verification boundaries. It does not contain local credentials, signing material or internal agent procedures.

## Build prerequisites

The project uses its checked-in Gradle wrapper, Android SDK components required by each module, Java and the declared dependency repositories. The phone and Wear modules compile against SDK 37; the resource-only watch face uses SDK 36. Android CI runs Gradle on JDK 21, while phone/Wear bytecode targets Java 17.

Some native media libraries are retrieved as prebuilt AARs by the [CI fetch script](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/scripts/ci/fetch-prebuilt-libs.sh). A source checkout alone is not a promise that every flavor can build without additional artifacts or configuration.

## What GitHub checks

| Workflow | What it checks | What it does not prove |
|---|---|---|
| [Static Gates](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/.github/workflows/static-gates.yml) | Repository text, configuration and script contracts | Device behavior or a signed release |
| [Android CI Quality Gate](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/.github/workflows/android-ci.yml) | Phone standard-debug lint, unit tests and assembly; separate Wear/lint checks and manifest risk sweep | Every edition, every device or store publication |
| [Dependency scan](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/.github/workflows/dependency-scan.yml) | PR dependency/license review; dependency-graph submission; per-module CycloneDX SBOM production | An exhaustive security audit of application behavior |
| [GitHub Pages deployment](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/.github/workflows/jekyll-gh-pages.yml) | Jekyll site build and deployment | APK/AAB release or Play review approval |

Triggers differ by workflow. An absent or skipped check is not a passing check; inspect the [Actions results](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/actions) for the relevant commit.

## Dependency transparency

Dependency review compares the pull request against its base and applies the repository's advisory/licence policy. SBOMs for `app_v2` and `wear` are uploaded as workflow artifacts; the workflow keeps them for 90 days. They describe resolved components, not the signing or publication state of an app.

## Debug builds, signed releases and downloads

A debug APK, a signed release APK and a Play app bundle serve different purposes. Release signing material is intentionally not included in the public source checkout. A build that skips signing for analysis is not a distributable signed release.

The Wear application and phone must have compatible package/signing identities for their Data Layer integration. The watch face is a different package and app bundle.

Only a published [GitHub release asset](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases) or a store listing establishes a downloadable version. The [product download cards](../index.html#download) expose verified assets and explicitly say when an APK is not published. Updating these documentation pages does not create a new application release.
