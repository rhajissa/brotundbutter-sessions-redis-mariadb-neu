#!/usr/bin/env bash
# Start-Skript für den SWE II Print Daemon
# Führt den Daemon als eigenständigen Java-Prozess aus.

cd "$(dirname "$0")/.." || exit 1

if [ ! -d "build/WEB-INF/classes" ]; then
    echo "Das Projekt muss zuerst kompiliert werden (z. B. via bin/build.sh)."
    exit 1
fi

echo "Starte SWE II Print Daemon..."
java -cp "build/WEB-INF/classes:lib/*" hbv.web.PrintDaemon
