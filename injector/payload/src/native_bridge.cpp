// JNI implementations exported with the mangled names the JVM looks up when
// the matching Java class calls System.load() on this DLL. We do NOT use
// RegisterNatives anymore — symbol-based resolution works cleanly across
// classloaders and survives the agent-attach path.

#ifndef WIN32_LEAN_AND_MEAN
#  define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <psapi.h>
#include <jni.h>
#include <jvmti.h>
#include <string>
#include <cstring>

#pragma comment(lib, "psapi.lib")
#pragma comment(lib, "user32.lib")

#include "jvm_loader.h"

// Make sure these are exported even when the link flags strip undecorated symbols.
#ifdef _MSC_VER
#  pragma comment(linker, "/EXPORT:Java_lion_client_NativeBridge_isKeyDown")
#  pragma comment(linker, "/EXPORT:Java_lion_client_NativeBridge_sendClick")
#  pragma comment(linker, "/EXPORT:Java_lion_client_NativeBridge_isMinecraftFocused")
#  pragma comment(linker, "/EXPORT:Java_lion_client_NativeBridge_getFocusedWindowTitle")
#  pragma comment(linker, "/EXPORT:Java_lion_client_NativeBridge_getForegroundPid")
#  pragma comment(linker, "/EXPORT:Java_lion_client_NativeBridge_findMinecraftHwnd")
#  pragma comment(linker, "/EXPORT:Java_lion_client_NativeBridge_getWindowRect")
#  pragma comment(linker, "/EXPORT:Java_lion_client_NativeBridge_setClickThrough")
#  pragma comment(linker, "/EXPORT:Java_lion_client_NativeBridge_findOwnWindowByTitle")
#  pragma comment(linker, "/EXPORT:Java_lion_client_NativeBridge_attachAgent")
#  pragma comment(linker, "/EXPORT:Java_lion_client_NativeBridge_findClassClassLoader")
#endif

#include "agent_attach.h"

// (WH_MOUSE_LL hook was removed: it filtered injected events correctly but
// every system mouse event — including aim movement — had to pass through
// our DLL, causing visible aim stutter. AutoClicker now enqueues clicks for
// the Forge side to apply via KeyBinding.onTick on the game thread, so no
// synthetic OS-level events are sent and GetAsyncKeyState(VK_LBUTTON) again
// reports the user's real state.)

extern "C" JNIEXPORT jboolean JNICALL
Java_lion_client_NativeBridge_isKeyDown(JNIEnv*, jclass, jint vk) {
    return (GetAsyncKeyState(vk) & 0x8000) ? JNI_TRUE : JNI_FALSE;
}

static void sendMouseButton(int button, bool down) {
    INPUT in{};
    in.type = INPUT_MOUSE;
    switch (button) {
        case 0: in.mi.dwFlags = down ? MOUSEEVENTF_LEFTDOWN  : MOUSEEVENTF_LEFTUP;  break;
        case 1: in.mi.dwFlags = down ? MOUSEEVENTF_RIGHTDOWN : MOUSEEVENTF_RIGHTUP; break;
        case 2: in.mi.dwFlags = down ? MOUSEEVENTF_MIDDLEDOWN: MOUSEEVENTF_MIDDLEUP;break;
        default: return;
    }
    SendInput(1, &in, sizeof(in));
}

extern "C" JNIEXPORT void JNICALL
Java_lion_client_NativeBridge_sendClick(JNIEnv*, jclass, jint button) {
    sendMouseButton(button, false);
    Sleep(1);
    sendMouseButton(button, true);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_lion_client_NativeBridge_isMinecraftFocused(JNIEnv*, jclass) {
    HWND fg = GetForegroundWindow();
    if (!fg) return JNI_FALSE;
    DWORD pid = 0;
    GetWindowThreadProcessId(fg, &pid);
    if (pid != GetCurrentProcessId()) return JNI_FALSE;
    wchar_t title[512] = {};
    GetWindowTextW(fg, title, 511);
    if (!title[0]) return JNI_FALSE;
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_lion_client_NativeBridge_getFocusedWindowTitle(JNIEnv* env, jclass) {
    HWND fg = GetForegroundWindow();
    if (!fg) return env->NewStringUTF("");
    char title[512] = {};
    GetWindowTextA(fg, title, 511);
    return env->NewStringUTF(title);
}

extern "C" JNIEXPORT jint JNICALL
Java_lion_client_NativeBridge_getForegroundPid(JNIEnv*, jclass) {
    HWND fg = GetForegroundWindow();
    if (!fg) return 0;
    DWORD pid = 0;
    GetWindowThreadProcessId(fg, &pid);
    return (jint)pid;
}

namespace {

struct FindMcCtx {
    HWND  best = nullptr;
    LONG  bestArea = 0;
    DWORD pid = 0;
};

BOOL CALLBACK findMcProc(HWND h, LPARAM lp) {
    auto* c = reinterpret_cast<FindMcCtx*>(lp);
    DWORD pid = 0; GetWindowThreadProcessId(h, &pid);
    if (pid != c->pid) return TRUE;
    if (!IsWindowVisible(h)) return TRUE;
    // Minecraft 1.8.9 ships LWJGL2 — its Win32 class name is "LWJGL".
    // Newer LWJGL3 builds (and some launchers' splash overlays) use "GLFW30".
    // Accept either, but also fall back to the largest visible window with a
    // non-empty title in our process so we cope with launchers that wrap the
    // game window in something we don't recognise.
    wchar_t cls[64] = {};
    GetClassNameW(h, cls, 63);
    bool knownClass = wcsstr(cls, L"LWJGL") || wcsstr(cls, L"GLFW");
    wchar_t title[256] = {};
    GetWindowTextW(h, title, 255);
    if (!knownClass && !title[0]) return TRUE;
    RECT r; if (!GetClientRect(h, &r)) return TRUE;
    LONG area = (r.right - r.left) * (r.bottom - r.top);
    if (area <= 0) return TRUE;
    // Prefer a known LWJGL/GLFW window; if we only see titled fallbacks, pick the largest.
    if (knownClass) area *= 4;
    if (area > c->bestArea) { c->bestArea = area; c->best = h; }
    return TRUE;
}

struct FindOwnCtx {
    HWND        hwnd = nullptr;
    std::string title;
    DWORD       pid = 0;
};

BOOL CALLBACK findOwnProc(HWND h, LPARAM lp) {
    auto* c = reinterpret_cast<FindOwnCtx*>(lp);
    DWORD pid = 0; GetWindowThreadProcessId(h, &pid);
    if (pid != c->pid) return TRUE;
    char buf[256] = {};
    GetWindowTextA(h, buf, 255);
    if (buf[0] && strstr(buf, c->title.c_str()) != nullptr) {
        c->hwnd = h;
        return FALSE;
    }
    return TRUE;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_lion_client_NativeBridge_findMinecraftHwnd(JNIEnv*, jclass) {
    FindMcCtx ctx;
    ctx.pid = GetCurrentProcessId();
    EnumWindows(findMcProc, reinterpret_cast<LPARAM>(&ctx));
    return (jlong)(uintptr_t)ctx.best;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_lion_client_NativeBridge_getWindowRect(JNIEnv* env, jclass, jlong hwndLong) {
    HWND hwnd = (HWND)(uintptr_t)hwndLong;
    jint vals[4] = { 0, 0, 0, 0 };
    if (hwnd && IsWindow(hwnd)) {
        RECT cr{};
        if (GetClientRect(hwnd, &cr)) {
            POINT tl{ 0, 0 };
            ClientToScreen(hwnd, &tl);
            vals[0] = tl.x;
            vals[1] = tl.y;
            vals[2] = cr.right - cr.left;
            vals[3] = cr.bottom - cr.top;
        }
    }
    jintArray ret = env->NewIntArray(4);
    env->SetIntArrayRegion(ret, 0, 4, vals);
    return ret;
}

extern "C" JNIEXPORT void JNICALL
Java_lion_client_NativeBridge_setClickThrough(JNIEnv*, jclass, jlong hwndLong) {
    HWND hwnd = (HWND)(uintptr_t)hwndLong;
    if (!hwnd || !IsWindow(hwnd)) return;
    LONG_PTR ex = GetWindowLongPtrW(hwnd, GWL_EXSTYLE);
    SetWindowLongPtrW(hwnd, GWL_EXSTYLE, ex | WS_EX_TRANSPARENT | WS_EX_NOACTIVATE);
}

extern "C" JNIEXPORT jlong JNICALL
Java_lion_client_NativeBridge_findOwnWindowByTitle(JNIEnv* env, jclass, jstring titleJ) {
    if (!titleJ) return 0;
    const char* t = env->GetStringUTFChars(titleJ, nullptr);
    FindOwnCtx ctx;
    ctx.pid   = GetCurrentProcessId();
    ctx.title = t ? t : "";
    if (t) env->ReleaseStringUTFChars(titleJ, t);
    if (ctx.title.empty()) return 0;
    EnumWindows(findOwnProc, reinterpret_cast<LPARAM>(&ctx));
    return (jlong)(uintptr_t)ctx.hwnd;
}

// ---- JVMTI agent attach bridge ------------------------------------------
//
// On non-Forge launchers (Lunar / Badlion / vanilla) we don't have the Forge
// event bus, so we splice render-side callbacks into Minecraft via a JVMTI
// agent: this entry locates the JVM's own instrument.dll, calls its
// Agent_OnAttach with our jar path, and the JVM in turn invokes
// lion.client.LionAgent.agentmain — same mechanism the Java Attach API uses
// internally, just done from inside the target process so it survives
// -XX:+DisableAttachMechanism.

extern "C" JNIEXPORT jboolean JNICALL
Java_lion_client_NativeBridge_attachAgent(JNIEnv* env, jclass, jstring jarPathJ) {
    if (!jarPathJ) return JNI_FALSE;
    const char* p = env->GetStringUTFChars(jarPathJ, nullptr);
    bool ok = false;
    if (p) {
        ok = lion::payload::attach_agent(p);
        env->ReleaseStringUTFChars(jarPathJ, p);
    }
    return ok ? JNI_TRUE : JNI_FALSE;
}

// ---- JVMTI-based class lookup -------------------------------------------
//
// Locates whichever ClassLoader defines the named class and returns it to
// Java. Used by lion.client.Agent on launchers that segregate the MC entry
// class (net.minecraft.client.main.Main, on AppClassLoader for Prism /
// MultiMC) from the actual game classes (net.minecraft.client.Minecraft,
// in a child URLClassLoader that no thread CCL ever references). Plain
// Class.forName delegation can't reach that child loader; JVMTI's
// GetLoadedClasses sees every defined class regardless of loader.
//
// Returns null if the class isn't loaded yet or JVMTI is unavailable.
extern "C" JNIEXPORT jobject JNICALL
Java_lion_client_NativeBridge_findClassClassLoader(JNIEnv* env, jclass, jstring classNameJ) {
    if (!classNameJ) return nullptr;
    const char* utf = env->GetStringUTFChars(classNameJ, nullptr);
    if (!utf) return nullptr;
    std::string target = "L";
    for (const char* p = utf; *p; ++p) target += (*p == '.' ? '/' : *p);
    target += ';';
    env->ReleaseStringUTFChars(classNameJ, utf);

    JavaVM* vm = nullptr;
    if (env->GetJavaVM(&vm) != JNI_OK || !vm) {
        lion::payload::payloadLog("[findClassClassLoader] GetJavaVM failed");
        return nullptr;
    }
    jvmtiEnv* jvmti = nullptr;
    jint rc = vm->GetEnv(reinterpret_cast<void**>(&jvmti), JVMTI_VERSION_1_0);
    if (rc != JNI_OK || !jvmti) {
        lion::payload::payloadLog("[findClassClassLoader] GetEnv(JVMTI) failed rc=%d", (int)rc);
        return nullptr;
    }

    jint count = 0;
    jclass* classes = nullptr;
    jvmtiError jerr = jvmti->GetLoadedClasses(&count, &classes);
    if (jerr != JVMTI_ERROR_NONE) {
        lion::payload::payloadLog("[findClassClassLoader] GetLoadedClasses err=%d", (int)jerr);
        jvmti->DisposeEnvironment();
        return nullptr;
    }

    jobject result = nullptr;
    for (jint i = 0; i < count && !result; ++i) {
        char* classSig = nullptr;
        if (jvmti->GetClassSignature(classes[i], &classSig, nullptr) != JVMTI_ERROR_NONE) continue;
        bool match = (classSig && target == classSig);
        if (classSig) jvmti->Deallocate(reinterpret_cast<unsigned char*>(classSig));
        if (!match) continue;
        jobject loader = nullptr;
        if (jvmti->GetClassLoader(classes[i], &loader) == JVMTI_ERROR_NONE && loader) {
            // Return as-is — JVMTI hands us a local JNI reference that the
            // returning native frame will hold for us.
            result = loader;
            lion::payload::payloadLog("[findClassClassLoader] %s -> loader found", target.c_str());
        }
    }

    jvmti->Deallocate(reinterpret_cast<unsigned char*>(classes));
    jvmti->DisposeEnvironment();
    if (!result) {
        lion::payload::payloadLog("[findClassClassLoader] %s not found among %d loaded classes",
                                   target.c_str(), (int)count);
    }
    return result;
}
