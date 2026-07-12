#!/usr/bin/env bash
# Load test for checkExemplar in BorrowServlet using 'hey' on Hopper

source local/config.txt || exit 1
path="$baseurl/$webapp"
mkdir -p tmp

cookie=tmp/cookie-$$.jar

# 1. Login to get session
curl -s -c "$cookie" "$path/login?user=admin&passwd=admin" > /dev/null
jsessionid=$(grep -o "[A-Z0-9]*$" < "$cookie")

if [ -z "$jsessionid" ]; then
    echo "Login failed, no JSESSIONID cookie found!"
    exit 1
fi

echo "Login erfolgreich. Session-ID: $jsessionid"
echo "Starte Lasttest auf checkExemplar mit 2000 Requests (Concurrency = 20)..."

# 2. Run hey load test
hey -n 2000 -c 20 -H "Cookie: JSESSIONID=$jsessionid" "$path/borrow?action=checkExemplar&code=200005"

# 3. Logout
curl -s -b "$cookie" "$path/logout" > /dev/null
rm -f "$cookie"
echo "Lasttest beendet und abgemeldet."
