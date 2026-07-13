#!/usr/bin/env bash
source local/config.txt || exit 1
path="$baseurl/$webapp"
mkdir -p tmp

cookie=tmp/cookie-$$.jar

echo "=== 1. Session-Status vor Login prüfen ==="
curl -s "$path/session-status"
echo -e "\n"

echo "=== 2. Authentifizierung durchführen ==="
curl -L -c "$cookie" -b "$cookie" "$path/login?user=admin&passwd=admin" > /dev/null
echo -e "\n"

echo "=== 3. Session-Status nach Login prüfen ==="
curl -L -c "$cookie" -b "$cookie" "$path/session-status"
echo -e "\n"

echo "=== 4. Funktionstest: Mitglied prüfen (checkMember) ==="
curl -L -c "$cookie" -b "$cookie" "$path/borrow?action=checkMember&code=M10001"
echo -e "\n"

echo "=== 5. Funktionstest: Exemplar für Ausleihe prüfen (checkExemplar) ==="
curl -L -c "$cookie" -b "$cookie" "$path/borrow?action=checkExemplar&code=200002"
echo -e "\n"

echo "=== 6. Funktionstest: Exemplar für Rückgabe prüfen (checkExemplar) ==="
curl -L -c "$cookie" -b "$cookie" "$path/return?action=checkExemplar&code=200002"
echo -e "\n"

echo "=== 7. Abmelden (Logout) ==="
curl -L -c "$cookie" -b "$cookie" "$path/logout"
echo -e "\n"

echo "=== 8. Session-Status nach Logout prüfen ==="
curl -s "$path/session-status"
echo ""

rm -f "$cookie"
