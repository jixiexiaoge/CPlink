pause
@echo off
chcp 65001 >nul
echo ========================================
echo    生成加密防反编译APK
echo ========================================
echo.

:: 检查签名文件，如果没有则创建
if not exist "app\release.keystore" (
    echo 正在创建签名文件...
    "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkey -v -keystore app\release.keystore -alias cplink_key -keyalg RSA -keysize 2048 -validity 10000 -storepass cplink123456 -keypass cplink123456 -dname "CN=CPlink, OU=Development, O=CPlink, L=Beijing, S=Beijing, C=CN" >nul 2>&1
    if %errorlevel% neq 0 (
        echo ❌ 创建签名文件失败
        pause
        exit /b 1
    )
    echo ✅ 签名文件创建成功
)

:: 清理并构建加密APK
echo 正在构建加密APK...
call gradlew clean assembleRelease

if %errorlevel% neq 0 (
    echo ❌ 构建失败
    pause
    exit /b 1
)

:: 复制APK到根目录
copy "app\build\outputs\apk\release\app-release.apk" "CPlink-Encrypted.apk" >nul 2>&1

if %errorlevel% neq 0 (
    echo ❌ 复制APK失败
    pause
    exit /b 1
)

:: 获取APK文件大小
for %%F in ("CPlink-Encrypted.apk") do set "apk_size=%%~zF"
set /a apk_size_mb=%apk_size% / 1048576

echo ✅ 加密APK构建完成！
echo.
echo 📦 生成文件: CPlink-Encrypted.apk
echo 📊 文件大小: %apk_size_mb% MB
echo 🔒 保护措施: 代码混淆 + 资源压缩 + 调试信息移除 + 数字签名
echo.
echo 📱 安装方法:
echo    1. 将APK文件传输到Android设备
echo    2. 启用"未知来源"安装
echo    3. 点击APK文件安装
echo.
echo 按任意键退出...
pause >nul
