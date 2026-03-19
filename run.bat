@echo off
chcp 65001 > nul
cd /d "%~dp0"

if not exist "out\production\java" mkdir "out\production\java"

echo 正在编译...
javac -encoding UTF-8 -d out\production\java java\src\CampusNavSystem.java
if errorlevel 1 (
    echo.
    echo 编译失败！请确认已安装 JDK（需要 JDK 8 或以上版本）
    echo 下载地址: https://www.oracle.com/java/technologies/downloads/
    pause
    exit /b 1
)

echo 编译成功，启动服务器...
java -cp out\production\java CampusNavSystem
pause
