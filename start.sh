#!/bin/sh
# Для VPS/своего ПК (Linux/Mac). На хостинге с панелью запускает сама панель.
# Положи paper.jar рядом (скачай с https://papermc.io/downloads/paper) и один раз прими EULA в eula.txt
java -Xms2G -Xmx4G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+DisableExplicitGC -jar paper.jar --nogui
