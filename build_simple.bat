@echo off
chcp 65001 >nul
cd /d D:\moodsite\windows
if exist target\pkg-lib rmdir /s /q target\pkg-lib
mkdir target\pkg-lib
copy /y target\moodtree-client-1.0.5.jar target\pkg-lib\
copy /y target\lib\javafx-*-win.jar target\pkg-lib\
copy /y target\lib\gson-*.jar target\pkg-lib\
copy /y target\lib\sqlite-jdbc-*.jar target\pkg-lib\
copy /y target\lib\slf4j-*.jar target\pkg-lib\
copy /y target\lib\error_prone_annotations-*.jar target\pkg-lib\
if exist target\dist rmdir /s /q target\dist

echo --- jpackage app-image ---
"C:\Program Files\Java\jdk-17\bin\jpackage.exe" --type app-image ^
  --name XinLv ^
  --module-path target\pkg-lib ^
  --module com.moodtree.client/com.moodtree.client.Main ^
  --app-version 1.0.5 ^
  --vendor XinLv ^
  --icon src\main\resources\logo.ico ^
  --dest target\dist
if errorlevel 1 (echo APP-IMAGE FAILED & pause & exit /b 1)
echo portable OK: target\dist\XinLv\XinLv.exe
echo.

echo --- jpackage exe installer ---
"C:\Program Files\Java\jdk-17\bin\jpackage.exe" --type exe ^
  --name XinLv ^
  --module-path target\pkg-lib ^
  --module com.moodtree.client/com.moodtree.client.Main ^
  --app-version 1.0.5 ^
  --vendor XinLv ^
  --description "XinLv desktop client" ^
  --icon src\main\resources\logo.ico ^
  --win-menu --win-shortcut ^
  --win-dir-chooser ^
  --dest target\dist
if errorlevel 1 (echo INSTALLER FAILED & pause & exit /b 1)

echo ALL DONE