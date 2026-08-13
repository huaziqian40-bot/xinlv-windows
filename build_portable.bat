@echo off
chcp 65001 >nul
cd /d D:\moodsite\windows
"C:\Program Files\Java\jdk-17\bin\jpackage.exe" --type app-image --name XinLv --module-path target\pkg-lib --module "com.moodtree.client/com.moodtree.client.Main" --app-version 1.0.5 --vendor XinLv --icon src\main\resources\logo.ico --dest target\dist
if errorlevel 1 (
  echo FAILED
  exit /b 1
)
echo OK
pause