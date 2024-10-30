@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

:: 檢查 Java 環境...
echo.檢查 Java 環境...
where java >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo.錯誤: 需要安裝 Java
    exit /b 1
)

:: 編譯 Java 文件
echo.編譯 Java 文件...
javac -encoding UTF-8 -d bin src/WebServer.java
if %ERRORLEVEL% NEQ 0 (
    echo.編譯失敗
    exit /b 1
)

:: 啟動服務
echo.按 Ctrl+C 停止服務器

:: 運行服務器
java -Dfile.encoding=UTF-8 -cp bin WebServer

endlocal
