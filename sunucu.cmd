@echo off
rem ------------------------------------------------------------------
rem  Eczane Otomasyon - web sunucusunu gelistirme ortaminda calistirir
rem  Kullanim: sunucu.cmd [--port 8080] [--host 0.0.0.0]
rem  Hazir paket icin: target\dist\EczaneOtomasyon\EczaneSunucu.exe
rem ------------------------------------------------------------------
setlocal EnableExtensions
cd /d "%~dp0"

if not defined JAVA_HOME (
    if exist "%USERPROFILE%\.jdks\openjdk-27\bin\java.exe" set "JAVA_HOME=%USERPROFILE%\.jdks\openjdk-27"
)
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [HATA] JDK 25 veya ustu bulunamadi. JAVA_HOME ortam degiskenini JDK klasorune ayarlayin.
    exit /b 1
)

if not exist "target\eczane-otomasyon.jar" (
    echo Derleniyor...
    call "%~dp0mvnw.cmd" -B -q -DskipTests package
    if errorlevel 1 exit /b 1
)

"%JAVA_HOME%\bin\java" -Dfile.encoding=UTF-8 --enable-native-access=ALL-UNNAMED ^
  -cp "target\eczane-otomasyon.jar;target\lib\*" com.eczane.web.ServerMain %*
