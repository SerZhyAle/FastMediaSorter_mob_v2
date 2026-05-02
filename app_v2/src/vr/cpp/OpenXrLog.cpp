#include "OpenXrLog.h"

#include <android/log.h>

#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

namespace
{

    std::mutex g_logBufferMutex;
    std::vector<std::string> g_logBuffer;

    static void nativeLogBufferAppend(char prioChar, const char *msg)
    {
        std::lock_guard<std::mutex> lock(g_logBufferMutex);
        if (g_logBuffer.size() >= xrnative::kLogBufferMaxEntries)
        {
            g_logBuffer.erase(g_logBuffer.begin(),
                              g_logBuffer.begin() + (g_logBuffer.size() - xrnative::kLogBufferMaxEntries + 1));
        }
        std::string entry;
        entry.reserve(2 + std::strlen(msg));
        entry.push_back(prioChar);
        entry.push_back('|');
        entry.append(msg);
        g_logBuffer.emplace_back(std::move(entry));
    }

} // namespace

void xrnative::nativeLogEmit(int androidPrio, const char *fmt, ...)
{
    char buf[1024];
    va_list args;
    va_start(args, fmt);
    const int written = vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);
    if (written < 0)
    {
        return;
    }
    __android_log_write(androidPrio, "OpenXrNative", buf);
    char prioChar;
    switch (androidPrio)
    {
    case ANDROID_LOG_ERROR:
        prioChar = 'E';
        break;
    case ANDROID_LOG_WARN:
        prioChar = 'W';
        break;
    case ANDROID_LOG_INFO:
        prioChar = 'I';
        break;
    case ANDROID_LOG_DEBUG:
        prioChar = 'D';
        break;
    default:
        prioChar = 'V';
        break;
    }
    nativeLogBufferAppend(prioChar, buf);
}

std::vector<std::string> xrnative::nativeLogBufferSnapshot()
{
    std::lock_guard<std::mutex> lock(g_logBufferMutex);
    return std::vector<std::string>(g_logBuffer);
}

std::vector<std::string> xrnative::nativeLogBufferDrain()
{
    std::vector<std::string> drained;
    std::lock_guard<std::mutex> lock(g_logBufferMutex);
    drained.swap(g_logBuffer);
    return drained;
}

const char *xrnative::xrSessionStateName(XrSessionState s)
{
    switch (s)
    {
    case XR_SESSION_STATE_UNKNOWN:
        return "UNKNOWN";
    case XR_SESSION_STATE_IDLE:
        return "IDLE";
    case XR_SESSION_STATE_READY:
        return "READY";
    case XR_SESSION_STATE_SYNCHRONIZED:
        return "SYNCHRONIZED";
    case XR_SESSION_STATE_VISIBLE:
        return "VISIBLE";
    case XR_SESSION_STATE_FOCUSED:
        return "FOCUSED";
    case XR_SESSION_STATE_STOPPING:
        return "STOPPING";
    case XR_SESSION_STATE_LOSS_PENDING:
        return "LOSS_PENDING";
    case XR_SESSION_STATE_EXITING:
        return "EXITING";
    default:
        return "?UNKNOWN_STATE";
    }
}

const char *xrnative::xrEventTypeName(XrStructureType t)
{
    switch (t)
    {
    case XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED:
        return "SESSION_STATE_CHANGED";
    case XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING:
        return "INSTANCE_LOSS_PENDING";
    case XR_TYPE_EVENT_DATA_INTERACTION_PROFILE_CHANGED:
        return "INTERACTION_PROFILE_CHANGED";
    case XR_TYPE_EVENT_DATA_REFERENCE_SPACE_CHANGE_PENDING:
        return "REFERENCE_SPACE_CHANGE_PENDING";
    default:
        return "OTHER";
    }
}