# Build-Anleitung (Windows)

Baut die drei Artefakte: `LionInjectable.exe`, `payload.dll`, `client.jar`.
Windows-only (der Injector nutzt MSVC/Win32).

## 1. Einmalige Installation

| Was | Zweck |
|-----|-------|
| **JDK** (8+), `JAVA_HOME` → dessen Wurzel | liefert `jni.h` für `payload.dll` |
| **JDK 17 oder 21** (Eclipse Adoptium) | Gradle braucht 17+ (findet `build.bat` selbst) |
| **CMake 3.20+** auf PATH | Injector-Build |
| **Visual Studio 2022 Build Tools** + Workload *Desktop development with C++* | `cl.exe` / `link.exe` |
| **Gradle 8+** auf PATH (kein Wrapper im Repo) | baut `client.jar` |
| **Ninja** (optional, `scoop install ninja`) | schnellerer Injector-Build, sonst NMake |
| ⚠️ **Forge 1.8.9 installieren + 1× über den Minecraft-Launcher starten** | **Pflicht.** Füllt `%APPDATA%\.minecraft\libraries\` und `~\.gradle\caches\minecraft\` mit der deobfuskierten Minecraft, LWJGL, authlib und MCP-Mappings, gegen die kompiliert wird. Ohne diesen Schritt bricht der Build mit vielen `cannot find symbol` / `package net.minecraft.* does not exist` ab. |

Benötigte Versionen (kommen mit **Forge 1.8.9-11.15.1.2318**): LWJGL `2.9.4-nightly-20150209`, authlib `1.5.21`, MCP stable `22`.

## 2. Bauen

1. **„x64 Native Tools Command Prompt for VS 2022"** öffnen (nicht die normale `cmd` — `cl`/`link` müssen auf PATH sein).
2. Im Repo-Wurzelverzeichnis:
   ```bat
   build.bat
   ```

`build.bat` macht: Sanity-Checks → `client.jar` via Gradle → `LionInjectable.exe` + `payload.dll` via CMake/MSVC → sammelt alles nach `dist\`.

## 3. Ergebnis

```
dist\LionInjectable.exe
dist\payload.dll
dist\client.jar
```

Benutzen: `LionInjectable.exe` starten → Spiel öffnen → *refresh* → *inject*. ClickGUI-Bind: **RShift**.

## Troubleshooting

| Fehler | Fix |
|--------|-----|
| `cmake not on PATH` | CMake 3.20+ installieren, auf PATH |
| `JAVA_HOME is not set` / `jni.h missing` | `JAVA_HOME` auf ein **JDK** (nicht JRE) zeigen lassen |
| `No JDK 17+ found` | Adoptium JDK 21 (oder 17) installieren |
| `forgeBin not found` / `LWJGL not found` (Gradle-Warnungen → Compile-Fehler) | Forge 1.8.9 installieren und **einmal** über den Launcher starten |
| `no gradlew.bat ... gradle not on PATH` | Gradle 8+ global installieren, oder `cd client && gradle wrapper` |

> Hinweis: Falls `forgeBin not found` trotz gestartetem Forge bleibt, ist ein ForgeGradle-Workspace (`setupDecompWorkspace`) nötig, um die deobfuskierte Minecraft + MCP-SRGs im Gradle-Cache zu erzeugen.

## CI

`.github/workflows/build.yml` baut auf einem Windows-Runner. Der `client.jar`-Schritt schlägt ohne den Forge-Cache fehl (siehe oben) — er dient aktuell als Compile-Check des Java-Codes; die vollständige Artefakt-Erzeugung erfordert die lokale Windows-Umgebung.
