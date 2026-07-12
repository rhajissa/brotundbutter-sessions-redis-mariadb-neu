#!/usr/bin/env bash
# set -x
source local/config.txt || exit 1
mkdir -p build target
rm -rf build/* target/*
cp -r app/* build

perl -pi -e "s/REDISSERVER/${redisserver}/g; s/REDISPASSWORD/${redispassword}/g" build/WEB-INF/web.xml
perl -pi -e "s/DBSERVER/${dbserver}/g; s/DBUSER/${dbuser}/g; s/DBPASSWORD/${dbpassword}/g; s/DBNAME/${dbname}/g" build/META-INF/context.xml

javac -Xlint:deprecation --release 21 -cp 'lib/*' -d build/WEB-INF/classes $(find src -name "*.java") &&
jar -cf 'target/webapp.war' -C build . &&
curl --location --netrc-file local/netrc --fail --upload-file target/webapp.war "$baseurl/$manager/text/deploy?path=/$webapp&update=true" &&
curl -s "$baseurl/$webapp/hello" || echo ups
