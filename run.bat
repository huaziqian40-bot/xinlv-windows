@echo off
REM ===== 心情树洞桌面客户端 · 一键运行（开发用） =====
REM 在 IDEA 里直接点 Main.java 的绿色三角也可以，效果一样。
chcp 65001 >nul
cd /d %~dp0

set JAVA_HOME=C:\Program Files\Java\jdk-17
set MVN=C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.0.1\plugins\maven-plugin\lib\maven3\bin\mvn.cmd

"%MVN%" -q compile javafx:run
pause
