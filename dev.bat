@echo off
rem =====================================================================
rem  DEV CLIENT - the fast way to try a change.
rem
rem  Runs Minecraft straight from the compiled classes: no jar to build, no
rem  jar to copy, and no 80-mod launcher profile in the way. Once the test
rem  world exists in run\saves, the game jumps directly into it instead of
rem  stopping at the main menu.
rem
rem    dev.bat                       -> run\saves\exsanguinate-dev
rem    dev.bat -PquickPlayWorld=X    -> another save
rem    dev.bat --offline             -> skip dependency lookups (all cached)
rem
rem  While the game runs:
rem    F9       dev only: toggle the berserk visuals (no rage grinding)
rem    F3+T     reload textures, models, lang, sounds, shaders (no restart)
rem    /reload  reload recipes, loot tables, tags (data packs)
rem
rem  Code changes: close and run dev.bat again. Faster still: run this from
rem  IntelliJ in Debug and use "Reload Changed Classes" (mixin classes still
rem  need a restart).
rem =====================================================================

setlocal
cd /d "%~dp0"

rem Pick up the same JDK the build uses, if it is not already set.
if not defined JAVA_HOME (
	for /d %%d in ("%~dp0.tools\jdk25\jdk-*") do set "JAVA_HOME=%%~fd"
)

if defined JAVA_HOME (
	echo [dev.bat] Using JAVA_HOME=%JAVA_HOME%
) else (
	echo [dev.bat] No JAVA_HOME set - Gradle will use the Java on PATH.
)

call "%~dp0gradlew.bat" runClient %*
exit /b %ERRORLEVEL%
