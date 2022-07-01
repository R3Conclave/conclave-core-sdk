#!/usr/bin/env bash

set -eou pipefail

enclave_mode=$1

gradle_args="-PenclaveMode=$enclave_mode"

echo
echo Now testing Conclave Init
echo

pushd build/distributions/conclave-sdk-*/

conclaveVersion=${PWD#*conclave-sdk-}
conclaveRepo=$PWD/repo


# create java project
$JAVA_HOME/bin/java -jar tools/conclave-init.jar \
  --enclave-class-name "MegaEnclave" \
  --package "com.megacorp" \
  --target "mega-project" \
  --configure-gradle=false

# run the unit tests of the new project
pushd mega-project

echo -e "\nconclaveRepo=$conclaveRepo" >> gradle.properties
echo -e "\nconclaveVersion=$conclaveVersion" >> gradle.properties
./gradlew test

# run host and client
# NOTE: this command is only run in mock mode, so that the signer can be provided below.
./gradlew :host:run & _PID=$!
sleep 5
./gradlew :client:run \
  --args="'S:0000000000000000000000000000000000000000000000000000000000000000 PROD:1 SEC:INSECURE'"

# kill the host
kill -9 $_PID
sleep 10
popd

# create kotlin project
$JAVA_HOME/bin/java -jar tools/conclave-init.jar \
  --enclave-class-name "MegaEnclave" \
  --package "com.megacorp" \
  --target "mega-kotlin-project" \
  --language kotlin \
  --configure-gradle=false


# run unit tests
pushd mega-kotlin-project

echo -e "\nconclaveRepo=$conclaveRepo" >> gradle.properties
echo -e "\nconclaveVersion=$conclaveVersion" >> gradle.properties
./gradlew test

# run the host and client
# NOTE: this command is only run in mock mode, so that the signer can be provided below.
./gradlew :host:run & _PID=$!
sleep 5
./gradlew :client:run \
  --args="'S:0000000000000000000000000000000000000000000000000000000000000000 PROD:1 SEC:INSECURE'"

# kill the host
kill -9 $_PID
sleep 10
popd

# clean up
rm -r mega-project
rm -r mega-kotlin-project
popd
