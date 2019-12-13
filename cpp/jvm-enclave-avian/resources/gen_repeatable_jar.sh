#!/usr/bin/env bash
set -xeuo pipefail

OUTPUT_JAR=$1
shift
INPUT_JARS=$@

OUTPUT_JAR_D=${OUTPUT_JAR}.d
rm -rf $OUTPUT_JAR_D
rm -f $OUTPUT_JAR
mkdir -p $OUTPUT_JAR_D
cd $OUTPUT_JAR_D

for INPUT_JAR in $INPUT_JARS
do
jar xf $INPUT_JAR
done

rm -f META-INF/*.DSA META-INF/*.RSA META-INF/*.EC META-INF/*.SF META-INF/INDEX.LIST META-INF/*.MF

echo -en "Manifest-Version: 1.0\r\n" > META-INF/MANIFEST.MF
find . -type d -exec chmod 0755 {} \;
find . -type f -exec chmod 0644 {} \;
find . -exec touch -cd 1970-01-01T00:00:00Z {} \;
find . -type f -o -type d | sort | zip -9qX@ $OUTPUT_JAR
