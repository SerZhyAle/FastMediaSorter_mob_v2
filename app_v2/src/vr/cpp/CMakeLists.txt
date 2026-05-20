cmake_minimum_required(VERSION 3.22.1)
project(fms_diagnostic_xr LANGUAGES CXX)

# S0249 Phase 02: diagnostic XR runtime — minimal OpenXR bridge that hosts one static
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

    # S0249 Phase 02 step 02.6: split into JNI bridge (diagnostic_xr_runtime.cpp) and the
    # full OpenXR + EGL + GLES session host (xr_session.cpp). The split keeps each TU under
    # the project's preferred LOC budget and isolates JNI from the heavy OpenXR boilerplate.
    add_library(fms_diagnostic_xr SHARED
        diagnostic_xr_runtime.cpp
        xr_session.cpp
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
else()
    message(STATUS "S0249 Phase 02: fms_diagnostic_xr disabled (FMS_BUILD_XR_RUNTIME=OFF)")
endif()
