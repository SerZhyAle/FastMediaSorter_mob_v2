cmake_minimum_required(VERSION 3.22.1)
project(fms_diagnostic_xr LANGUAGES CXX)

# S0249 Phase 02: diagnostic XR runtime - minimal OpenXR bridge that hosts one static
# stereoscopic top-bottom equirectangular image and exits on any input.
#
# Build gating: this CMakeLists.txt is referenced by AGP for every flavor (so the
# externalNativeBuild configuration is consistent), but the native target is only emitted
# when `FMS_BUILD_XR_RUNTIME=ON`. Pass it via `cmake.arguments` in the `vr` and `noLegal`
# productFlavor blocks in `app_v2/build.gradle.kts`. Other flavors skip the gate and
# generate no native artifacts, so they do not need the OpenXR loader AAR on the classpath.

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

option(FMS_BUILD_XR_RUNTIME "Build the diagnostic XR native runtime (S0249)" OFF)

if(FMS_BUILD_XR_RUNTIME)
    message(STATUS "S0249 Phase 02: building fms_diagnostic_xr (OpenXR loader linkage active)")

    # Khronos OpenXR loader is supplied as an AAR (org.khronos.openxr:openxr_loader_for_android)
    # with prefab packaging. AGP must expose it via `buildFeatures { prefab = true }` in
    # build.gradle.kts. The package name is `OpenXR` and the imported target is
    # `OpenXR::openxr_loader`.
    find_package(OpenXR REQUIRED CONFIG)

    # Source convention for this module (S0249 step 02.6, extended by S1270): one translation
    # unit per responsibility, each with its own header when it has a public surface, so every
    # unit stays under the project's per-file line ceiling. State that is shared between units
    # but is not part of the module's public surface goes into an INTERNAL header -
    # xr_session_internal.h declares the `State g` the four xr_session_* units share, while
    # xr_session.h remains the only surface diagnostic_xr_runtime.cpp is allowed to include.
    # Sources are an explicit list rather than a glob, so a new unit costs exactly one line.
    add_library(fms_diagnostic_xr SHARED
        diagnostic_xr_runtime.cpp
        xr_session.cpp
        xr_session_setup.cpp
        xr_session_render.cpp
        xr_session_jni.cpp
        xr_input.cpp
        xr_raycast.cpp
        xr_hud_world.cpp
    )

    target_include_directories(fms_diagnostic_xr PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
    )

    target_link_libraries(fms_diagnostic_xr PRIVATE
        OpenXR::openxr_loader
        android
        log
        EGL
        GLESv3
    )

    target_compile_options(fms_diagnostic_xr PRIVATE
        -Wall
        -Wno-unused-parameter
    )

    target_link_options(fms_diagnostic_xr PRIVATE
        -Wl,-z,max-page-size=16384
    )
else()
    message(STATUS "S0249 Phase 02: fms_diagnostic_xr disabled (FMS_BUILD_XR_RUNTIME=OFF)")
endif()
