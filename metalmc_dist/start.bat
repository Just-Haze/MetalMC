@echo off
java -Xms4G -Xmx4G -XX:+UseZGC -XX:+ZGenerational -jar metalmc.jar --nogui
pause
