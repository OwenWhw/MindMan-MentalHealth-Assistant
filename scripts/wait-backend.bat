@echo off
:loop
grep -q "Started Mindman" "C:\Users\魏浩文\Documents\心理健康助手\code\backend\run.log" >nul 2>nul
if errorlevel 1 (
    timeout /t 2 /nobreak >nul
    goto loop
)
echo === BACKEND READY ===
netstat -ano | findstr ":8080.*LISTEN" 
