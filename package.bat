@echo off
REM ===== XinLv client - one-click packaging =====
REM Output:
REM   1) portable : target\dist\XinLv\        (copy the folder anywhere, run XinLv.exe)
REM   2) installer: target\dist\XinLv-<ver>.exe  (needs WiX, added to PATH below)
REM
REM Version is READ FROM pom.xml (never hardcode it here). Hardcoding the jar name
REM once broke the build after a version bump: jpackage then failed with
REM "module com.moodtree.client not found" because the copied jar name no longer matched.
chcp 65001 >nul
cd /d %~dp0

set JAVA_HOME=C:\Program Files\Java\jdk-17
set JPACKAGE=%JAVA_HOME%\bin\jpackage.exe
set MVN=C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.0.1\plugins\maven-plugin\lib\maven3\bin\mvn.cmd
REM jpackage finds WiX candle/light on PATH when building the installer
set PATH=%PATH%;C:\Program Files (x86)\WiX Toolset v3.14\bin

echo [0/3] reading version from pom.xml ...
REM The project <version> is the only one indented by exactly 4 spaces
REM (dependency versions use 12/16). PowerShell cannot be used here:
REM it produces no stdout when invoked from a batch for /f loop.
set APP_VERSION=
for /f "tokens=3 delims=<>" %%v in ('findstr /r /c:"^    <version>" pom.xml') do if not defined APP_VERSION set APP_VERSION=%%v
if "%APP_VERSION%"=="" (
  echo FAILED: cannot read version from pom.xml
  pause
  exit /b 1
)
echo        version = %APP_VERSION%

echo [1/3] maven clean package ...
call "%MVN%" -q clean package
if errorlevel 1 (echo BUILD FAILED & pause & exit /b 1)

echo [2/3] staging module path (windows javafx jars only) ...
if exist target\pkg-lib rmdir /s /q target\pkg-lib
mkdir target\pkg-lib
copy /y target\moodtree-client-%APP_VERSION%.jar target\pkg-lib\ >nul
copy /y target\lib\javafx-*-win.jar target\pkg-lib\ >nul
copy /y target\lib\gson-*.jar target\pkg-lib\ >nul
copy /y target\lib\sqlite-jdbc-*.jar target\pkg-lib\ >nul
copy /y target\lib\slf4j-*.jar target\pkg-lib\ >nul
copy /y target\lib\error_prone_annotations-*.jar target\pkg-lib\ >nul

if exist target\dist rmdir /s /q target\dist

echo [3/3] jpackage app-image (portable) ...
"%JPACKAGE%" --type app-image ^
  --name XinLv ^
  --module-path target\pkg-lib ^
  --module com.moodtree.client/com.moodtree.client.Main ^
  --app-version %APP_VERSION% ^
  --vendor XinLv ^
  --icon src\main\resources\logo.ico ^
  --dest target\dist
if errorlevel 1 (echo APP-IMAGE FAILED & pause & exit /b 1)
echo portable OK: target\dist\XinLv\XinLv.exe
echo.

echo building exe installer (WiX) ...
"%JPACKAGE%" --type exe ^
  --name XinLv ^
  --module-path target\pkg-lib ^
  --module com.moodtree.client/com.moodtree.client.Main ^
  --app-version %APP_VERSION% ^
  --vendor XinLv ^
  --description "XinLv desktop client" ^
  --icon src\main\resources\logo.ico ^
  --win-menu --win-shortcut ^
  --win-dir-chooser ^
  --dest target\dist
if errorlevel 1 (echo INSTALLER FAILED - portable build is still fine & pause & exit /b 1)

echo.
echo ALL DONE:
echo   portable  target\dist\XinLv\
echo   installer target\dist\XinLv-%APP_VERSION%.exe
pause
