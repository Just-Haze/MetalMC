@echo off
rem MetalMC launcher with tuned JVM flags (Java 25+). See start.sh for details.
rem Set MEMORY before running to change the heap size, e.g. "set MEMORY=8G".
if "%MEMORY%"=="" set MEMORY=4G
set JAR=%1
if "%JAR%"=="" for %%f in (metalmc-*.jar) do set JAR=%%f
if "%JAR%"=="" (
    echo No MetalMC jar found. Pass the jar path as the first argument.
    exit /b 1
)

java -Xms%MEMORY% -Xmx%MEMORY% -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -XX:+UseCompactObjectHeaders --enable-native-access=ALL-UNNAMED -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true -jar "%JAR%" --nogui
