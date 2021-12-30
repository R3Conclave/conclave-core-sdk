#!/usr/bin/env bash
set -xeuo pipefail

if [ $# -ne 3 ]
  then
    echo "usage: nexus-iq_publish.sh [login] [password] [build-stage]"
    exit 1
fi

script_dir=$(dirname ${BASH_SOURCE[0]})

# Get the current version from gradle's properties.
cd ${script_dir}/..
version=$(./gradlew -q properties | grep -w "version" | awk '{print $2}')
cd -

rm -fr "${script_dir}/../build/nexus-iq"
mkdir -p "${script_dir}/../build/nexus-iq"
# Note: Add [-w] to the command line to fail on any warning.
java --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED -jar $NEXUS_IQ_HOME/nexus-iq-cli.jar \
    -s https://nexusiq.dev.r3.com \
    -i conclave_sdk_${version%-*} \
    -a $1:$2 \
    -t $3 \
    -r "${script_dir}/../build/nexus-iq/nexus-iq_report.json" \
    "${script_dir}/../build/distributions/conclave-sdk-${version}.zip"

res=$?

if [ $res -eq 0 ]
then
    echo NEXUS-IQ COMPLETED
else
    echo NEXUS-IQ COMPLETED WITH ERROR CODE $res
fi

echo "${script_dir}/../build/nexus-iq/nexus-iq_report.json"
echo "* BEGIN OF ${script_dir}/../build/nexus-iq/nexus-iq_report.json"
cat "${script_dir}/../build/nexus-iq/nexus-iq_report.json"
echo
echo "* END OF ${script_dir}/../build/nexus-iq/nexus-iq_report.json"

exit $res