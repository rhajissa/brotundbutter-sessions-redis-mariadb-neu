#!/usr/bin/env bash

# Source local configuration
if [ -f local/config.txt ]; then
  source local/config.txt
elif [ -f ~/brotundbutter-sessions-redis-mariadb/local/config.txt ]; then
  source ~/brotundbutter-sessions-redis-mariadb/local/config.txt
else
  echo "Configuration file local/config.txt not found!" >&2
  exit 1
fi

titel="$1"
autor="$2"
code="$3"

if [ -z "$titel" ] || [ -z "$autor" ]; then
  echo "Usage: $0 <Buchtitel> <Autor/in> [Barcode]" >&2
  exit 1
fi

# Escape single quotes for SQL safety (e.g. Janson's -> Janson''s)
safe_titel=$(echo "$titel" | sed "s/'/''/g")
safe_autor=$(echo "$autor" | sed "s/'/''/g")

# Build the SQL script inside a transaction
SQL=$(cat <<EOF
START TRANSACTION;
INSERT INTO BIB_Autorin (name) SELECT '$safe_autor' FROM dual WHERE NOT EXISTS (SELECT 1 FROM BIB_Autorin WHERE name = '$safe_autor');
INSERT INTO BIB_Buch (titel) VALUES ('$safe_titel');
SET @buch_id = LAST_INSERT_ID();
SET @autor_id = (SELECT id FROM BIB_Autorin WHERE name = '$safe_autor' LIMIT 1);
INSERT INTO BIB_Autorin_Buch (autorin_id, buch_id) VALUES (@autor_id, @buch_id);
EOF
)

if [ -n "$code" ]; then
  SQL=$(cat <<EOF
$SQL
INSERT INTO BIB_Buchexemplar (code, buch_id) VALUES ($code, @buch_id);
EOF
)
fi

SQL="$SQL
COMMIT;"

echo "Adding book '$titel' by '$autor'..."

if [ "$(uname -n)" = hopper ] || [ -f /etc/debian_version ]; then
  # Running directly on Hopper or inside mydocker container
  echo "$SQL" | mariadb -h "${dbserver}" -u "${dbuser}" -p"${dbpassword}" "${dbname}"
else
  # Running locally on Mac -> execute remotely on mydocker
  echo "$SQL" | ssh mydocker "mariadb -h ${dbserver} -u ${dbuser} -p'${dbpassword}' ${dbname}"
fi

if [ $? -eq 0 ]; then
  echo "Book successfully added to database!"
else
  echo "Failed to add book." >&2
  exit 1
fi
