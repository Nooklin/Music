@echo off
echo Setting up build environment...

REM Check if java is available
java -version >nul 2>&1
if %errorlevel% equ 0 (
    echo Java is already installed
) else (
    echo Java is not found. Please install JDK 17 or higher and set JAVA_HOME.
    echo Build cannot proceed without Java.
    exit /b 1
)

REM Try to build the project
echo Building Nooklin Music project...
call gradlew.bat build


if %errorlevel% equ 0 (
    echo Build completed successfully!
) else (
    echo Build failed!
    exit /b 1
)