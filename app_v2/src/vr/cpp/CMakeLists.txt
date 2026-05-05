# Native OpenXR JNI bridge — vr flavor only.
# Builds `libopenxr_native.so` that Kotlin loads via System.loadLibrary("openxr_native").
# Links against the OpenXR loader shipped in the Khronos AAR (via Prefab).
#
# ENABLE_OPENXR is set to ON only by the vr Gradle flavor (-DENABLE_OPENXR=ON).
# Non-vr flavors leave it OFF so CMake configure succeeds without the OpenXR AAR.

cmake_minimum_required(VERSION 3.22.1)
project(openxr_native LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

option(ENABLE_OPENXR "Enable OpenXR native JNI bridge (vr flavor only)" OFF)

if(NOT ENABLE_OPENXR)
    # Non-VR flavor: no native targets to build.
    # AGP always injects these cache vars; reference them here so CMake does not
    # emit "Manually-specified variables were not used by the project" warnings.
    message(STATUS "[openxr_native] disabled — output=${CMAKE_LIBRARY_OUTPUT_DIRECTORY}, compile_commands=${CMAKE_EXPORT_COMPILE_COMMANDS}")
    set(_agp_runtime "${CMAKE_RUNTIME_OUTPUT_DIRECTORY}")
    unset(_agp_runtime)
    return()
endif()

if(ENABLE_OPENXR)
    # Prefab package exposed by org.khronos.openxr:openxr_loader_for_android AAR.
    find_package(OpenXR REQUIRED CONFIG)

    add_library(openxr_native SHARED
        OpenXrNative.cpp
        OpenXrFrame.cpp
        OpenXrHandTracking.cpp
        OpenXrInput.cpp
        OpenXrLifecycle.cpp
        OpenXrSwapchain.cpp
        OpenXrLog.cpp
        OpenXrRayDraw.cpp
    )

    target_include_directories(openxr_native PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
    )

    # The AAR ships with XR_USE_PLATFORM_ANDROID + XR_USE_GRAPHICS_API_OPENGL_ES enabled.
    target_compile_definitions(openxr_native PRIVATE
        XR_USE_PLATFORM_ANDROID
        XR_USE_GRAPHICS_API_OPENGL_ES
    )

    target_link_libraries(openxr_native
        OpenXR::openxr_loader
        android
        log
        EGL
        GLESv3
        # AndroidBitmap_lockPixels / unlockPixels / getInfo for HUD bitmap upload
        # (spec_vr-immersive-hud-gl Phase 03).
        jnigraphics
    )

    # 16 KB page size compliance (Google Play requirement for Android 15+ targets since Nov 2025).
    # Aligns LOAD segments of libopenxr_native.so at 16 KB boundaries.
    target_link_options(openxr_native PRIVATE "-Wl,-z,max-page-size=16384")
endif()
