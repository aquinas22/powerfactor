@echo off
setlocal
rem Launches the NeoForge dev client straight into a flat creative world named FlatCreative.
rem First run: the dev server writes the world (the server starts, builds the world and is told to stop)
rem and it is copied into the client's saves. Later runs go straight to the game.
rem Builds the current PowerFactor checkout before launching, including newly pulled GitHub updates.
rem Needs Java 25 (JAVA_HOME, else Android Studio's bundled JBR) and internet access for new dependencies.

cd /d "%~dp0"
set "WORLD=FlatCreative"

if not defined JAVA_HOME if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo Java 25 was not found. Set JAVA_HOME to your Java 25 JDK folder and try again.
  pause
  exit /b 1
)

rem Gradle toolchains: Java 25 (the game) plus Java 21 (NeoForge's asset download task).
rem Set JAVA21_HOME to override the local Java 21 path; Gradle can also discover/download toolchains.
if not defined JAVA21_HOME if exist "D:\tmp\re\jdk\jdk-21.0.12.1+1\bin\java.exe" set "JAVA21_HOME=D:\tmp\re\jdk\jdk-21.0.12.1+1"
set "JOPT=-Porg.gradle.java.installations.paths=%JAVA_HOME%"
if defined JAVA21_HOME if exist "%JAVA21_HOME%\bin\java.exe" set "JOPT=%JOPT%,%JAVA21_HOME%"

if exist "runs\client\saves\%WORLD%\level.dat" goto :play

echo Creating flat creative world "%WORLD%"...
if not exist "runs\server" mkdir "runs\server"
(
  echo level-name=%WORLD%
  echo level-type=flat
  echo gamemode=creative
  echo force-gamemode=true
  echo difficulty=peaceful
  echo generate-structures=false
) > "runs\server\server.properties"
echo eula=true> "runs\server\eula.txt"
if exist "runs\server\logs\latest.log" del /q "runs\server\logs\latest.log"
rem Run the dev server just long enough to create and save the world: a watcher stops it once it reports Done.
start "" /b powershell -NoProfile -Command "$l='runs/server/logs/latest.log'; while(-not((Test-Path $l) -and (Select-String -Path $l -Pattern 'Done \(' -Quiet))){Start-Sleep 1}; Start-Sleep 5; Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | ?{$_.CommandLine -match 'serverRunProgramArgs' -and $_.CommandLine.Contains((Get-Location).Path.Replace('\','\\'))} | ForEach-Object{Stop-Process -Id $_.ProcessId -Force}"
call "%~dp0gradlew.bat" --daemon --parallel --build-cache "%JOPT%" runServer
if not exist "runs\server\%WORLD%\level.dat" (
  echo.
  echo Could not generate the world; starting the game anyway. Create a Flat / Creative world from the menu.
  goto :launch
)
if not exist "runs\client\saves" mkdir "runs\client\saves"
xcopy "runs\server\%WORLD%" "runs\client\saves\%WORLD%\" /E /I /Q /Y >nul

:play
rem Simple Voice Chat's metadata uses a deprecated logoFile property, which pops a load-warnings dialog. Hide it.
if not exist "runs\client\config" mkdir "runs\client\config"
if not exist "runs\client\config\neoforge-client.toml" echo showLoadWarnings = false> "runs\client\config\neoforge-client.toml"
echo Building and launching the current PowerFactor updates in "%WORLD%"...
call "%~dp0gradlew.bat" --daemon --parallel --build-cache "%JOPT%" runClient "-PquickPlayWorld=%WORLD%"
goto :done

:launch
echo Building and launching the current PowerFactor updates...
call "%~dp0gradlew.bat" --daemon --parallel --build-cache "%JOPT%" runClient

:done
set "LAUNCH_EXIT_CODE=%errorlevel%"
if not "%LAUNCH_EXIT_CODE%"=="0" pause
exit /b %LAUNCH_EXIT_CODE%
