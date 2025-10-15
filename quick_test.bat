@echo off
echo ========================================
echo CPlink 快速测试脚本
echo ========================================

set PROJECT_DIR=C:\Users\zhudo\AndroidStudioProjects\CPlink
cd /d "%PROJECT_DIR%"

echo.
echo 1. 检查项目结构...
if not exist "app\src\main\java\com\example\carrotamap\MainActivity.kt" (
    echo 错误：MainActivity.kt 不存在
    pause
    exit /b 1
)

if not exist "app\src\test\java\com\example\carrotamap\SimpleUnitTest.kt" (
    echo 错误：测试文件不存在
    pause
    exit /b 1
)

echo 项目结构检查通过 ✓

echo.
echo 2. 编译项目...
call gradlew assembleDebug
if %ERRORLEVEL% neq 0 (
    echo 编译失败
    pause
    exit /b 1
)
echo 编译成功 ✓

echo.
echo 3. 运行单元测试...
call gradlew testDebugUnitTest --tests="SimpleUnitTest"
if %ERRORLEVEL% neq 0 (
    echo 单元测试失败
    pause
    exit /b 1
)
echo 单元测试通过 ✓

echo.
echo 4. 运行集成测试...
call gradlew testDebugUnitTest --tests="SimpleIntegrationTest"
if %ERRORLEVEL% neq 0 (
    echo 集成测试失败
    pause
    exit /b 1
)
echo 集成测试通过 ✓

echo.
echo 5. 运行性能测试...
call gradlew testDebugUnitTest --tests="SimplePerformanceTest"
if %ERRORLEVEL% neq 0 (
    echo 性能测试失败
    pause
    exit /b 1
)
echo 性能测试通过 ✓

echo.
echo 6. 检查APK文件...
if exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo APK文件存在 ✓
    for %%I in ("app\build\outputs\apk\debug\app-debug.apk") do echo APK大小: %%~zI 字节
) else (
    echo 警告：APK文件不存在
)

echo.
echo ========================================
echo 快速测试完成！
echo ========================================
echo.
echo 测试结果：
echo - 项目结构：✓
echo - 编译状态：✓
echo - 单元测试：✓
echo - 集成测试：✓
echo - 性能测试：✓
echo.
echo 应用已准备好进行用户测试！
echo.
echo 下一步：
echo 1. 安装APK到测试设备
echo 2. 按照USER_TESTING_GUIDE.md进行测试
echo 3. 运行 deploy_release.bat 生成发布版本
echo.

pause
