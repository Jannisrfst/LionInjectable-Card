#include "agent_attach.h"
#include "jvm_loader.h"

#include <windows.h>
#include <jni.h>
#include <cstring>
#include <cstdio>

namespace lion::payload {

namespace {

using GetCreatedJavaVMs_t = jint (JNICALL*)(JavaVM**, jsize, jsize*);
using AgentOnAttach_t     = jint (JNICALL*)(JavaVM*, char*, void*);

// Given the full path to jvm.dll, return the path to its sibling
// instrument.dll. Typical layouts:
//   <jre>/bin/server/jvm.dll  →  <jre>/bin/instrument.dll
//   <jdk>/bin/server/jvm.dll  →  <jdk>/bin/instrument.dll
// Some embedded JREs put instrument.dll directly next to jvm.dll instead of
// up one directory — we try both.
bool find_instrument_dll(const char* jvm_path, char* out, size_t out_size) {
    char buf[MAX_PATH];
    if (strlen(jvm_path) >= sizeof(buf)) return false;
    strcpy(buf, jvm_path);

    // Strip "\jvm.dll" → buf is now ".../server" (or ".../client").
    char* slash = strrchr(buf, '\\');
    if (!slash) return false;
    *slash = '\0';

    // Try side-by-side first: .../server/instrument.dll
    char candidate[MAX_PATH];
    snprintf(candidate, sizeof(candidate), "%s\\instrument.dll", buf);
    if (GetFileAttributesA(candidate) != INVALID_FILE_ATTRIBUTES) {
        if (strlen(candidate) >= out_size) return false;
        strcpy(out, candidate);
        return true;
    }

    // Step up one more level: strip "/server" → ".../bin/instrument.dll"
    slash = strrchr(buf, '\\');
    if (!slash) return false;
    *slash = '\0';

    snprintf(candidate, sizeof(candidate), "%s\\instrument.dll", buf);
    if (GetFileAttributesA(candidate) != INVALID_FILE_ATTRIBUTES) {
        if (strlen(candidate) >= out_size) return false;
        strcpy(out, candidate);
        return true;
    }
    return false;
}

} // anonymous namespace

bool attach_agent(const char* jar_path) {
    if (!jar_path || !jar_path[0]) {
        payloadLog("[agent_attach] empty jar path");
        return false;
    }
    payloadLog("[agent_attach] starting, jar=%s", jar_path);

    HMODULE jvm = GetModuleHandleA("jvm.dll");
    if (!jvm) {
        payloadLog("[agent_attach] jvm.dll not loaded — refusing to attach.");
        return false;
    }

    auto getVMs = reinterpret_cast<GetCreatedJavaVMs_t>(
            GetProcAddress(jvm, "JNI_GetCreatedJavaVMs"));
    if (!getVMs) {
        payloadLog("[agent_attach] JNI_GetCreatedJavaVMs missing from jvm.dll");
        return false;
    }

    JavaVM* vm = nullptr;
    jsize count = 0;
    if (getVMs(&vm, 1, &count) != JNI_OK || count == 0 || !vm) {
        payloadLog("[agent_attach] No live Java VM (count=%d)", (int)count);
        return false;
    }

    char jvm_path[MAX_PATH];
    if (!GetModuleFileNameA(jvm, jvm_path, sizeof(jvm_path))) {
        payloadLog("[agent_attach] GetModuleFileName(jvm.dll) failed: %lu", GetLastError());
        return false;
    }
    payloadLog("[agent_attach] jvm.dll = %s", jvm_path);

    char instrument_path[MAX_PATH];
    if (!find_instrument_dll(jvm_path, instrument_path, sizeof(instrument_path))) {
        payloadLog("[agent_attach] instrument.dll not found near %s", jvm_path);
        return false;
    }
    payloadLog("[agent_attach] instrument.dll = %s", instrument_path);

    HMODULE instr = LoadLibraryA(instrument_path);
    if (!instr) {
        payloadLog("[agent_attach] LoadLibrary(%s) failed: %lu",
                   instrument_path, GetLastError());
        return false;
    }

    auto onAttach = reinterpret_cast<AgentOnAttach_t>(
            GetProcAddress(instr, "Agent_OnAttach"));
    if (!onAttach) {
        payloadLog("[agent_attach] Agent_OnAttach missing from instrument.dll");
        return false;
    }

    // Agent_OnAttach's args is the JAR path itself for instrument.dll. The
    // jar's manifest declares Agent-Class, which the JVM then invokes.
    // The string buffer must be writable — Agent_OnAttach may mutate it.
    char arg[MAX_PATH];
    strncpy(arg, jar_path, sizeof(arg));
    arg[sizeof(arg) - 1] = '\0';

    payloadLog("[agent_attach] calling Agent_OnAttach with %s", arg);
    jint rc = onAttach(vm, arg, nullptr);
    payloadLog("[agent_attach] Agent_OnAttach returned %d", (int)rc);
    return rc == JNI_OK;
}

} // namespace lion::payload
