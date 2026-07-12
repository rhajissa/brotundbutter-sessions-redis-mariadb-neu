#!/usr/bin/env bash
# run-loadtest.sh
# Runs a concurrent load test on Hopper, generates a gnuplot chart, and copies it back.

source local/config.txt || exit 1
path="$baseurl/$webapp"

echo "=== 1. Starte Login-Prozess auf Hopper ==="
# Login über ssh auf Hopper, um Session-ID zu erhalten
jsessionid=$(ssh hopper "curl -s -c - '$path/login?user=admin&passwd=admin' | grep JSESSIONID | awk '{print \$7}'")

if [ -z "$jsessionid" ]; then
    echo "Fehler: Login auf Hopper fehlgeschlagen, keine JSESSIONID gefunden!"
    exit 1
fi
echo "Login erfolgreich. Session-ID: $jsessionid"

echo "=== 2. Führe Lasttest auf Hopper aus (2000 Requests, Concurrency=20) ==="
# Führe hey aus und schreibe die CSV-Daten temporär auf Hopper
ssh hopper "hey -n 2000 -c 20 -o csv -H 'Cookie: JSESSIONID=$jsessionid' '$path/borrow?action=checkExemplar&code=200002' > ~/latency.csv"

echo "=== 3. Erzeuge Diagramm mit Gnuplot auf Hopper ==="
# Schreibe Gnuplot-Skript temporär auf Hopper und führe es aus
ssh hopper "cat << 'EOF' > ~/plot.gp
set datafile separator ','
set title 'SWE II Antwortzeit-Latenz (2000 Requests, Concurrency=20)' textcolor rgb '#ffffff' font 'sans,12'
set xlabel 'Request Index' textcolor rgb '#ffffff'
set ylabel 'Antwortzeit (Sekunden)' textcolor rgb '#ffffff'
set border lw 1 lc rgb '#ffffff'
set key textcolor rgb '#ffffff'
set grid lc rgb '#333333'
set object 1 rectangle from graph 0,0 to graph 1,1 behind fillcolor rgb '#0b0c16' fillstyle solid 1.0 noborder
set term png size 900,500 background '#0b0c16'
set output 'latency_plot.png'
plot 'latency.csv' using 1 with lines lc rgb '#6366f1' lw 1.5 title 'Antwortzeit (s)'
EOF
gnuplot ~/plot.gp
"

echo "=== 4. Kopiere Diagramm auf den lokalen Mac ==="
# Kopiere das generierte Bild zurück
mkdir -p app
scp hopper:~/latency_plot.png app/latency_plot.png


echo "=== 5. Bereinige temporäre Dateien auf Hopper ==="
ssh hopper "rm -f ~/latency.csv ~/plot.gp ~/latency_plot.png"

echo "=== Lasttest abgeschlossen! ==="
echo "Diagramm gespeichert unter: app/latency_plot.png"
