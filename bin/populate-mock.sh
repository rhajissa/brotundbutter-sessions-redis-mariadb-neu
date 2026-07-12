#!/usr/bin/env bash

# Source configuration
if [ -f local/config.txt ]; then
  source local/config.txt
elif [ -f ~/brotundbutter-sessions-redis-mariadb/local/config.txt ]; then
  source ~/brotundbutter-sessions-redis-mariadb/local/config.txt
else
  echo "Configuration file local/config.txt not found!" >&2
  exit 1
fi

quantity="$1"
if [ -z "$quantity" ]; then
  quantity=10
fi

echo "Fetching $quantity mock books from fakerapi.it..."

# Perform API call, parse with jq, escape single quotes (replacing ' with ''), and pipe to MariaDB via SSH
curl -s "https://fakerapi.it/api/v1/books?_quantity=${quantity}" | jq -r '.data[] | "
START TRANSACTION;
INSERT INTO BIB_Autorin (name) SELECT '\''" + (.author | gsub("'\''"; "'\'''\''")) + "'\'' FROM dual WHERE NOT EXISTS (SELECT 1 FROM BIB_Autorin WHERE name = '\''" + (.author | gsub("'\''"; "'\'''\''")) + "'\'');
INSERT INTO BIB_Buch (titel) VALUES ('\''" + (.title | gsub("'\''"; "'\'''\''")) + "'\'');
SET @buch_id = LAST_INSERT_ID();
SET @autor_id = (SELECT id FROM BIB_Autorin WHERE name = '\''" + (.author | gsub("'\''"; "'\'''\''")) + "'\'' LIMIT 1);
INSERT INTO BIB_Autorin_Buch (autorin_id, buch_id) VALUES (@autor_id, @buch_id);
INSERT INTO BIB_Buchexemplar (code, buch_id) VALUES (" + (.isbn[-8:] | tonumber | tostring) + ", @buch_id);
COMMIT;
"' | ssh mydocker "mariadb -h ${dbserver} -u ${dbuser} -p'${dbpassword}' ${dbname}"

if [ $? -eq 0 ]; then
  echo "Successfully imported $quantity mock books to mydocker database!"
else
  echo "Failed to import books." >&2
  exit 1
fi
