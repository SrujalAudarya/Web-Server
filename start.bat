@echo off

echo =========================
echo BUILDING SERVER...
echo =========================

javac -cp ".;lib/*" ^
src/MainServer.java ^
src/config/*.java ^
src/models/*.java ^
src/utils/*.java ^
src/handlers/*.java ^
src/server/*.java

if %errorlevel% neq 0 (
    echo.
    echo BUILD FAILED
    pause
    exit /b
)

echo.
echo =========================
echo STARTING SERVER...
echo =========================

java -cp ".;src;lib/*" MainServer