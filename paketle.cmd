@echo off
rem ------------------------------------------------------------------
rem  Eczane Otomasyon - Windows paketi olusturur
rem  Cikti: target\dist\EczaneOtomasyon\EczaneOtomasyon.exe
rem  (Java dahil, hedef bilgisayara ayrica kurulum gerekmez)
rem ------------------------------------------------------------------
setlocal EnableExtensions
cd /d "%~dp0"

if not defined JAVA_HOME (
    if exist "%USERPROFILE%\.jdks\openjdk-27\bin\jpackage.exe" set "JAVA_HOME=%USERPROFILE%\.jdks\openjdk-27"
)
if not exist "%JAVA_HOME%\bin\jpackage.exe" (
    echo [HATA] JDK 25 veya ustu bulunamadi. JAVA_HOME ortam degiskenini JDK klasorune ayarlayin.
    exit /b 1
)

echo [1/3] Derleniyor ve testler calistiriliyor...
call "%~dp0mvnw.cmd" -B -q clean package
if errorlevel 1 (
    echo [HATA] Derleme ya da testler basarisiz.
    exit /b 1
)

echo [2/3] Dosyalar hazirlaniyor...
mkdir "target\input\lib" "target\jfx" 2>nul
copy /y "target\eczane-otomasyon.jar" "target\input\" >nul
for %%f in ("target\lib\*.jar") do call :sort "%%~f"

echo [3/3] Windows uygulamasi olusturuluyor...
"%JAVA_HOME%\bin\jpackage" --type app-image ^
  --name EczaneOtomasyon --app-version 2.0.0 --vendor "Eczane Otomasyon" ^
  --input target\input --main-jar eczane-otomasyon.jar --main-class com.eczane.Launcher ^
  --module-path target\jfx ^
  --add-modules java.base,java.desktop,java.prefs,java.sql,java.logging,javafx.controls,jdk.localedata,jdk.unsupported,jdk.httpserver ^
  --jlink-options "--strip-debug --no-man-pages --no-header-files --strip-native-commands --include-locales=tr,en" ^
  --icon packaging\app.ico ^
  --add-launcher EczaneSunucu=packaging\sunucu.properties ^
  --java-options "-Dfile.encoding=UTF-8" ^
  --java-options "--enable-native-access=ALL-UNNAMED,javafx.graphics" ^
  --dest target\dist
if errorlevel 1 (
    echo [HATA] Paketleme basarisiz.
    exit /b 1
)

echo.
echo Tamam: target\dist\EczaneOtomasyon\EczaneOtomasyon.exe
echo Web sunucusu: target\dist\EczaneOtomasyon\EczaneSunucu.exe
echo Klasorun tamamini (EczaneOtomasyon) istediginiz yere kopyalayabilirsiniz.
exit /b 0

rem JavaFX kutuphaneleri Java calisma ortamina modul olarak eklenir, digerleri uygulama klasorune.
:sort
set "n=%~nx1"
if /i "%n:~0,7%"=="javafx-" (
    if /i "%n:~-8%"=="-win.jar" copy /y "%~1" "target\jfx\" >nul
) else (
    copy /y "%~1" "target\input\lib\" >nul
)
exit /b 0
