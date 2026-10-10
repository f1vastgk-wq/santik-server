@echo off
rem Для своего ПК (Windows). Положи paper.jar рядом и один раз прими EULA в eula.txt
java -Xms2G -Xmx4G -XX:+UseG1GC -jar paper.jar --nogui
pause
