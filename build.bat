@echo off
setlocal EnableDelayedExpansion
rem ===========================================================================
rem LionInjectable master build script
rem
rem  1. Builds client.jar via Gradle
rem  2. Builds LionInjectable.exe + payload.dll via CMake/MSBuild
rem  3. Collects all three artefacts into .\dist\
rem ===========================================================================

set ROOT=%~dp0
set DIST=%ROOT%dist
set STAGE=%ROOT%build\stage

echo.
echo === [1/3] Sanity checks ===================================================
where cmake >nul 2>&1   || (echo ERROR: cmake not on PATH. Install CMake 3.20+.& exit /b 1)
where javac >nul 2>&1   || (echo ERROR: javac not on PATH. Install a JDK 17+ and set JAVA_HOME.& exit /b 1)
if "%JAVA_HOME%"=="" (echo ERROR: JAVA_HOME is not set.& exit /b 1)
if not exist "%JAVA_HOME%\include\jni.h" (
    echo ERROR: %%JAVA_HOME%%\include\jni.h missing. Is JAVA_HOME pointing at a JRE instead of a JDK?
    exit /b 1
)
echo JAVA_HOME (for C++ headers) = %JAVA_HOME%

rem ---- Find a JDK 17+ to run Gradle with (Gradle 9 refuses JDK 8/11) -------
rem We only override JAVA_HOME for the Gradle step; the C++ build below still
rem uses the original (typically JDK 8) for jni.h sources.
set "GRADLE_JAVA="
for %%P in (
    "C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot"
    "C:\Program Files\Eclipse Adoptium\jdk-21"
    "C:\Program Files\Eclipse Adoptium\jdk-17"
    "C:\Program Files\Java\jdk-21"
    "C:\Program Files\Java\jdk-17"
) do (
    if not defined GRADLE_JAVA if exist "%%~P\bin\javac.exe" set "GRADLE_JAVA=%%~P"
)
if not defined GRADLE_JAVA (
    rem Wildcard probe for any jdk-21* / jdk-17* under standard install roots.
    for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-21*") do (
        if not defined GRADLE_JAVA if exist "%%~fD\bin\javac.exe" set "GRADLE_JAVA=%%~fD"
    )
    for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do (
        if not defined GRADLE_JAVA if exist "%%~fD\bin\javac.exe" set "GRADLE_JAVA=%%~fD"
    )
)
if not defined GRADLE_JAVA (
    echo ERROR: No JDK 17+ found. Install Eclipse Adoptium JDK 21 or set JAVA_HOME to one.
    exit /b 1
)
echo Gradle JDK              = %GRADLE_JAVA%

echo.
echo === [2/3] Building client.jar (Gradle) ====================================
pushd "%ROOT%client"
setlocal
set "JAVA_HOME=%GRADLE_JAVA%"
set "PATH=%GRADLE_JAVA%\bin;%PATH%"
if exist gradlew.bat (
    call gradlew.bat --no-daemon build || (endlocal & popd & goto :fail)
) else (
    where gradle >nul 2>&1 || (
        echo ERROR: no gradlew.bat in client\ and gradle not on PATH.
        echo        Either install Gradle 8+ or run: gradle wrapper  inside client\.
        endlocal & popd & exit /b 1
    )
    call gradle --no-daemon build || (endlocal & popd & goto :fail)
)
endlocal
popd

echo.
echo === [3/3] Building injector (CMake + cl) ==================================
if not exist "%ROOT%build" mkdir "%ROOT%build"
pushd "%ROOT%build"

rem We are inside a vcvars x64 prompt, so cl/link/nmake are on PATH. Prefer
rem Ninja if available (faster, parallel), otherwise fall back to NMake.
where ninja >nul 2>&1
if errorlevel 1 (
    set "CMAKE_GEN=NMake Makefiles"
) else (
    set "CMAKE_GEN=Ninja"
)
echo Using CMake generator: %CMAKE_GEN%
echo Using compiler:
where cl
cmake -G "%CMAKE_GEN%" -DCMAKE_BUILD_TYPE=Release ^
      -DCMAKE_C_COMPILER=cl -DCMAKE_CXX_COMPILER=cl ^
      "%ROOT%injector" || (popd & goto :fail)
cmake --build . || (popd & goto :fail)
popd

echo.
echo === Collecting artefacts into %DIST% ======================================
if not exist "%DIST%" mkdir "%DIST%"
rem Single-config generators put output directly in build\, not build\Release\.
copy /Y "%ROOT%build\LionInjectable.exe" "%DIST%\" >nul || goto :fail
copy /Y "%ROOT%build\payload.dll"        "%DIST%\" >nul || goto :fail
copy /Y "%STAGE%\client.jar"             "%DIST%\" >nul || goto :fail

echo.
echo BUILD SUCCEEDED.
echo   %DIST%\LionInjectable.exe
echo   %DIST%\payload.dll
echo   %DIST%\client.jar
echo.
exit /b 0

:fail
echo.
echo BUILD FAILED. See messages above.
exit /b 1
