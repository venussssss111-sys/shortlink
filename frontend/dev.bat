@echo off
chcp 65001 >nul
cd /d "%~dp0"

if not exist "D:\dafeiyuwork\.tmp" mkdir "D:\dafeiyuwork\.tmp"
set TEMP=D:\dafeiyuwork\.tmp
set TMP=D:\dafeiyuwork\.tmp

if not exist "node_modules" (
    echo 第一次运行，先安装依赖...
    call npm install
)

echo.
echo   前端启动中... 浏览器打开 http://localhost:5173
echo   记住：后端（8080）要同时开着，否则列表会报「连不上服务」
echo   关闭窗口或按 Ctrl+C 停止
echo.
call npm run dev

pause
