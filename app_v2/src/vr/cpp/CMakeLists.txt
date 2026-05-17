cmake_minimum_required(VERSION 3.22.1)
project(fms_native_stub)

# VR native bridge was removed in S0241. This file is kept as a stub so that
# `android { externalNativeBuild { cmake { path = ... } } }` in build.gradle.kts
# does not fail configuration for the standard flavor (whose targets list is
# cleared via disableNativeBuild()).
#
# The noLegal flavor's `cmake.targets += listOf("openxr_native")` config is
# defunct after VR removal and will be cleaned up in a follow-up commit; for
# the standard release AAB this stub is enough.
