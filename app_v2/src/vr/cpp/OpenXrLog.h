#pragma once

#include <android/log.h>

#include <cstddef>
#include <string>
#include <vector>

#include <openxr/openxr.h>

namespace xrnative
{
    constexpr size_t kLogBufferMaxEntries = 512;
    void nativeLogEmit(int androidPrio, const char *fmt, ...) __attribute__((format(printf, 2, 3)));
    // Snapshot of the in-memory ring buffer; returns a copy under lock.
    std::vector<std::string> nativeLogBufferSnapshot();
    // OpenXrNative.nativeDrainLog() needs a move-out API to preserve drain semantics after extraction.
    std::vector<std::string> nativeLogBufferDrain();
    const char *xrSessionStateName(XrSessionState s);
    const char *xrEventTypeName(XrStructureType t);
} // namespace xrnative