#pragma once

namespace lion::payload {

// Loads our jar into the running JVM as a Java agent — without going through
// the official Attach API. Mechanism: locate the JVM's bundled
// instrument.dll (sibling of jvm.dll), LoadLibrary it, GetProcAddress its
// Agent_OnAttach export, call it with the jar path.
//
// This bypasses -XX:+DisableAttachMechanism (which only gates the Java-side
// AttachListener / IPC handshake — the C export is always exposed) so it
// works on Lunar Client, Badlion, and similar locked-down launchers.
//
// jar_path: absolute path to the agent jar, ASCII / system-codepage. The
// jar's MANIFEST.MF must declare Agent-Class (we set this in build.gradle).
//
// Returns true if Agent_OnAttach returned JNI_OK. False on any failure;
// failure reasons are written to payload.log.
bool attach_agent(const char* jar_path);

} // namespace lion::payload
