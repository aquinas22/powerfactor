@echo off
setlocal
rem Launches the NeoForge dev client straight into a flat creative world named PowerFactorFlatTest.
rem First run: a separate dev server creates a flat world, then shuts down normally and saves.
rem The copy has cheats enabled. Existing non-test worlds are left alone.
rem Builds the current PowerFactor checkout before launching, including newly pulled GitHub updates.
rem Needs Java 25 (JAVA_HOME, else Android Studio's bundled JBR) and internet access for new dependencies.

cd /d "%~dp0"
set "WORLD=PowerFactorFlatTest"
set "TEST_SERVER=runs\test-server"

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
if not exist "%TEST_SERVER%" mkdir "%TEST_SERVER%"
(
  echo level-name=%WORLD%
  echo level-type=minecraft:flat
  echo generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains","lakes":false,"features":false,"structure_overrides":[]}
  echo gamemode=creative
  echo force-gamemode=false
  echo difficulty=peaceful
  echo generate-structures=false
  echo online-mode=false
  echo server-ip=127.0.0.1
  echo server-port=0
) > "%TEST_SERVER%\server.properties"
echo eula=true> "%TEST_SERVER%\eula.txt"
if not exist "%TEST_SERVER%\config\voicechat" mkdir "%TEST_SERVER%\config\voicechat"
(echo port=0)> "%TEST_SERVER%\config\voicechat\voicechat-server.properties"
rem This flag shuts down the preparation server normally once the world is ready.
call "%~dp0gradlew.bat" --daemon --parallel --build-cache "%JOPT%" runServer "-PserverDir=%TEST_SERVER%" -PprepareTestWorld
if errorlevel 1 goto :failed
if not exist "%TEST_SERVER%\%WORLD%\level.dat" (
  echo Test world generation did not produce level.dat.
  goto :failed
)
if not exist "runs\client\saves" mkdir "runs\client\saves"
xcopy "%TEST_SERVER%\%WORLD%" "runs\client\saves\%WORLD%\" /E /I /Q /Y >nul
if errorlevel 1 goto :failed

:play
rem Make commands available in this dedicated flat test world, preserving a backup of level.dat.
"%JAVA_HOME%\bin\java.exe" "%~dp0tools\EnableTestCheats.java" "runs\client\saves\%WORLD%"
if errorlevel 1 goto :failed
echo Cheats enabled: /gamemode survival and /gamemode creative are available.
rem Simple Voice Chat's metadata uses a deprecated logoFile property, which pops a load-warnings dialog. Hide it.
if not exist "runs\client\config" mkdir "runs\client\config"
if not exist "runs\client\config\neoforge-client.toml" echo showLoadWarnings = false> "runs\client\config\neoforge-client.toml"
echo Building and launching the current PowerFactor updates in "%WORLD%"...
call "%~dp0gradlew.bat" --daemon --parallel --build-cache "%JOPT%" runClient "-PquickPlayWorld=%WORLD%"
set "LAUNCH_EXIT_CODE=%errorlevel%"
goto :done

:failed
echo Could not prepare the flat test world. Your existing worlds were not changed.
pause
exit /b 1

:done
if not "%LAUNCH_EXIT_CODE%"=="0" pause
exit /b %LAUNCH_EXIT_CODE%
