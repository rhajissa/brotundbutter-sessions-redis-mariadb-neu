#!/usr/bin/env bash
# set -x
source local/config.txt || exit 1

echo "Verbindung zur Hopper-Datenbank wird hergestellt..."
echo "1. Zurücksetzen des Schemas (createdb.sql)..."

if [ "$(uname -n)" = hopper ] || [ -f /etc/debian_version ]; then
  # Running directly on Hopper or inside container
  mariadb -h "${dbserver}" -u "${dbuser}" -p"${dbpassword}" "${dbname}" < createdb.sql
else
  # Running locally on Mac
  ssh hopper "mariadb -h ${dbserver} -u ${dbuser} -p'${dbpassword}' ${dbname}" < createdb.sql
fi

if [ $? -eq 0 ]; then
  echo "Schema erfolgreich zurückgesetzt."
else
  echo "Fehler beim Zurücksetzen des Schemas!" >&2
  exit 1
fi

echo "2. Importieren der 1000 Testbücher (mock_books.sql)..."

if [ "$(uname -n)" = hopper ] || [ -f /etc/debian_version ]; then
  # Running directly on Hopper or inside container
  mariadb -h "${dbserver}" -u "${dbuser}" -p"${dbpassword}" "${dbname}" < mock_books.sql
else
  # Running locally on Mac
  ssh hopper "mariadb -h ${dbserver} -u ${dbuser} -p'${dbpassword}' ${dbname}" < mock_books.sql
fi

if [ $? -eq 0 ]; then
  echo "Erfolgreich 1000 Bücher, Autoren und Exemplare importiert!"
else
  echo "Fehler beim Importieren der Testdaten!" >&2
  exit 1
fi
