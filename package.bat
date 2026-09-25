@echo off
REM ===== XinLv client - one-click packaging =====
REM Output:
REM   1) portable: target\dist\XinLv\  (copy the folder anywhere, run XinLv.exe)
REM   2) installer: target\dist\XinLv-1.1.2.exe  (needs WiX, already on PATH below)
chcp 65001 >nul
cd /d %~dp0

set JAVA_HOME=C:\Program Files\Java\jdk-17
set JPACKAGE=%JAVA_HOME%\bin\jpackage.exe
set MVN=C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.0.1\plugins\maven-plugin\lib\maven3\bin\mvn.cmd
REM jpackage finds WiX candle/light on PATH when building the installer
set PATH=%PATH%;C:\Program Files (x86)\WiX Toolset v3.14\bin

echo [1/3] maven clean package...
call "%MVN%" -q clean package
if errorlevel 1 (echo BUILD FAILED & pause & exit /b 1)

REM 版本号从 pom.xml 读（别再硬编码：改版本时漏改这里的 jar 名会让 jpackage
REM 报"无法从模块路径找到 com.moodtree.client 模块"）
for /f "delims=" %%v in ('powershell -NoProfile -Command "[xml](Get-Content pom.xml).project.version"') do set APP_VERSION=%%v
if "%APP_VERSION%"=="" (echo CANNOT READ VERSION FROM pom.xml & pause & exit /b 1)
echo       version = %APP_VERSION%

echo [2/3] staging module path (windows javafx jars only)...
if exist target\pkg-lib rmdir /s /q target\pkg-lib
mkdir target\pkg-lib
copy /y target\moodtree-client-%APP_VERSION%.jar target\pkg-lib\ >nul
copy /y target\lib\javafx-*-win.jar target\pkg-lib\ >nul
copy /y target\lib\gson-*.jar target\pkg-lib\ >nul
copy /y target\lib\sqlite-jdbc-*.jar target\pkg-lib\ >nul
copy /y target\lib\slf4j-*.jar target\pkg-lib\ >nul
copy /y target\lib\error_prone_annotations-*.jar target\pkg-lib\ >nul

if exist target\dist rmdir /s /q target\dist

echo [3/3] jpackage app-image (portable)...
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

echo building exe installer (WiX)...
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
if errorlevel 1 (echo INSTALLER FAILED (portable is fine) & pause & exit /b 1)

echo.
echo ALL DONE:
echo   portable  target\dist\XinLv\
echo   installer target\dist\XinLv-%APP_VERSION%.exe
pause
