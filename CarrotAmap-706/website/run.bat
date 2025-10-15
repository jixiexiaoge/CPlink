@echo off
echo 正在启动反馈管理系统...
echo.

REM 检查Python是否安装
python --version >nul 2>&1
if errorlevel 1 (
    echo 错误: 未找到Python，请先安装Python
    pause
    exit /b 1
)

REM 检查虚拟环境是否存在
if not exist "venv" (
    echo 创建虚拟环境...
    python -m venv venv
)

REM 激活虚拟环境
echo 激活虚拟环境...
call venv\Scripts\activate.bat

REM 安装依赖
echo 安装依赖包...
pip install -r requirements.txt

REM 创建uploads文件夹
if not exist "uploads" (
    echo 创建uploads文件夹...
    mkdir uploads
)

REM 启动应用
echo.
echo 启动Flask应用...
echo 访问地址: http://localhost:5000
echo 管理员密码: 1533
echo.
echo 按 Ctrl+C 停止服务器
echo.

python app.py

pause
